package it.palestra.app.data

import androidx.room.withTransaction
import it.palestra.app.data.db.AppDatabase
import it.palestra.app.data.db.ExerciseEntity
import it.palestra.app.data.db.PlanOverrideEntity
import it.palestra.app.data.db.ScheduleEntity
import it.palestra.app.data.db.SessionEntity
import it.palestra.app.data.db.SessionExerciseEntity
import it.palestra.app.data.db.SessionStatus
import it.palestra.app.data.db.SetEntity
import it.palestra.app.data.db.TemplateEntity
import it.palestra.app.data.db.TemplateExerciseEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

class ExerciseRepository(private val db: AppDatabase) {
    private val dao = db.exerciseDao()

    val all: Flow<List<ExerciseEntity>> = dao.observeAll()

    suspend fun seedIfEmpty() {
        if (dao.count() == 0) dao.insertAll(ExerciseCatalog.items)
    }

    suspend fun get(id: Long) = dao.get(id)

    suspend fun nameTaken(name: String, excludeId: Long) = dao.countByName(name.trim(), excludeId) > 0

    suspend fun save(e: ExerciseEntity): Long =
        if (e.id == 0L) dao.insert(e.copy(name = e.name.trim(), isCustom = true)) else {
            dao.update(e.copy(name = e.name.trim())); e.id
        }
}

class TemplateRepository(private val db: AppDatabase) {
    private val dao = db.templateDao()

    val templates: Flow<List<TemplateEntity>> = dao.observeTemplates()
    val templateExercises: Flow<List<TemplateExerciseEntity>> = dao.observeAllExercises()
    val schedule: Flow<List<ScheduleEntity>> = dao.observeSchedule()
    val overrides: Flow<List<PlanOverrideEntity>> = dao.observeOverrides()

    /** Programmazione completa, pronta per i calcoli del calendario. */
    val planner: Flow<Planner> = combine(schedule, overrides, templates) { s, o, t ->
        Planner(s, o, t.map { it.id }.toSet())
    }

    suspend fun get(id: Long) = dao.get(id)
    suspend fun exercisesOf(id: Long) = dao.getExercises(id)
    suspend fun daysOf(id: Long): Set<Int> = dao.getSchedule().filter { it.templateId == id }.map { it.dayOfWeek }.toSet()

    suspend fun save(template: TemplateEntity, exercises: List<TemplateExerciseEntity>, days: Set<Int>): Long =
        db.withTransaction {
            val id = if (template.id == 0L) {
                val pos = (dao.getAll().maxOfOrNull { it.position } ?: -1) + 1
                dao.insert(template.copy(position = pos))
            } else {
                dao.update(template); template.id
            }
            dao.clearExercises(id)
            dao.insertExercises(exercises.mapIndexed { i, e -> e.copy(id = 0, templateId = id, position = i) })
            dao.clearScheduleForTemplate(id)
            days.forEach { dao.setSchedule(ScheduleEntity(it, id)) }
            id
        }

    suspend fun duplicate(id: Long): Long? {
        val t = dao.get(id) ?: return null
        return save(t.copy(id = 0, name = "${t.name} (copia)"), dao.getExercises(id), emptySet())
    }

    suspend fun delete(id: Long) = db.withTransaction {
        dao.clearScheduleForTemplate(id)
        dao.delete(id)
    }

    suspend fun assignDay(dayOfWeek: Int, templateId: Long?) {
        if (templateId == null) dao.clearScheduleDay(dayOfWeek) else dao.setSchedule(ScheduleEntity(dayOfWeek, templateId))
    }

    /** Sposta (o recupera) un allenamento: il giorno di origine diventa riposo, quello di arrivo ha la scheda. */
    suspend fun moveWorkout(from: LocalDate, to: LocalDate, templateId: Long) = db.withTransaction {
        if (from != to) dao.setOverride(PlanOverrideEntity(from.toEpochDay(), null))
        dao.setOverride(PlanOverrideEntity(to.toEpochDay(), templateId))
    }

    suspend fun setPlan(date: LocalDate, templateId: Long?) = dao.setOverride(PlanOverrideEntity(date.toEpochDay(), templateId))

    suspend fun resetPlan(date: LocalDate) = dao.clearOverride(date.toEpochDay())
}

data class ExerciseBlock(
    val sessionExercise: SessionExerciseEntity,
    val exercise: ExerciseEntity?,
    val sets: List<SetEntity>,
) {
    val name: String get() = exercise?.name ?: "Esercizio rimosso"
    val completedSets: Int get() = sets.count { it.completed }
}

data class SessionDetail(val session: SessionEntity, val blocks: List<ExerciseBlock>)

data class PreviousPerformance(val date: LocalDate, val sets: List<SetEntity>)

class SessionRepository(private val db: AppDatabase) {
    private val dao = db.sessionDao()
    private val templateDao = db.templateDao()
    private val exerciseDao = db.exerciseDao()

    val active: Flow<SessionEntity?> = dao.observeActive()
    val completed: Flow<List<SessionEntity>> = dao.observeCompleted()
    val setRows = dao.observeCompletedSetRows()
    val links = dao.observeSessionExerciseLinks()

    suspend fun getActive() = dao.getActive()
    suspend fun get(id: Long) = dao.getSession(id)

    fun observeDetail(sessionId: Long): Flow<SessionDetail?> = combine(
        dao.observeSession(sessionId),
        dao.observeSessionExercises(sessionId),
        dao.observeSetsForSession(sessionId),
        exerciseDao.observeAll(),
    ) { s, exs, sets, catalog ->
        if (s == null) return@combine null
        val byId = catalog.associateBy { it.id }
        val setsBy = sets.groupBy { it.sessionExerciseId }
        SessionDetail(s, exs.map { se -> ExerciseBlock(se, byId[se.exerciseId], setsBy[se.id].orEmpty().sortedBy { it.position }) })
    }

    private fun now() = System.currentTimeMillis()
    private fun today() = LocalDate.now().toEpochDay()

    suspend fun previousPerformance(exerciseId: Long, excludeSessionId: Long, before: Long = Long.MAX_VALUE): PreviousPerformance? {
        val seId = dao.lastSessionExerciseId(exerciseId, excludeSessionId, before) ?: return null
        val day = dao.epochDayForSessionExercise(seId) ?: return null
        val sets = dao.getSetsForSessionExercise(seId).filter { it.completed }
        if (sets.isEmpty()) return null
        return PreviousPerformance(LocalDate.ofEpochDay(day), sets)
    }

    /** Crea le serie di un esercizio, precompilate con l'ultima sessione (o con i target della scheda). */
    private suspend fun buildSets(
        sessionExerciseId: Long,
        exerciseId: Long,
        sessionId: Long,
        warmups: Int,
        sets: Int,
        reps: Int,
        weight: Double,
        durationSec: Int,
    ): List<SetEntity> {
        val prev = previousPerformance(exerciseId, sessionId)?.sets.orEmpty()
        val prevWarm = prev.filter { it.isWarmup }
        val prevWork = prev.filter { !it.isWarmup }
        val result = mutableListOf<SetEntity>()
        repeat(warmups) { i ->
            val p = prevWarm.getOrNull(i)
            result += SetEntity(
                sessionExerciseId = sessionExerciseId,
                position = result.size,
                weight = p?.weight ?: roundTo(weight * 0.5, 2.5),
                reps = p?.reps ?: reps,
                durationSec = p?.durationSec ?: durationSec,
                isWarmup = true,
                targetReps = reps,
            )
        }
        repeat(sets) { i ->
            val p = prevWork.getOrNull(i) ?: prevWork.lastOrNull()
            result += SetEntity(
                sessionExerciseId = sessionExerciseId,
                position = result.size,
                weight = p?.weight ?: weight,
                reps = if (reps > 0) reps else p?.reps ?: 0,
                durationSec = if (durationSec > 0) durationSec else p?.durationSec ?: 0,
                distanceKm = p?.distanceKm ?: 0.0,
                targetReps = reps,
            )
        }
        return result
    }

    private fun roundTo(v: Double, step: Double) = (v / step).roundToInt() * step

    suspend fun startFromTemplate(templateId: Long): Long = db.withTransaction {
        val t = templateDao.get(templateId) ?: error("Scheda non trovata")
        val sid = dao.insertSession(
            SessionEntity(templateId = t.id, name = t.name, epochDay = today(), startedAt = now(), status = SessionStatus.IN_PROGRESS),
        )
        templateDao.getExercises(templateId).forEachIndexed { i, te ->
            val seId = dao.insertSessionExercise(
                SessionExerciseEntity(
                    sessionId = sid, exerciseId = te.exerciseId, position = i, restSec = te.restSec,
                    supersetGroup = te.supersetGroup, autoTimer = te.autoTimer, notes = te.notes,
                ),
            )
            dao.insertSets(buildSets(seId, te.exerciseId, sid, te.warmupSets, te.sets, te.reps, te.weight, te.durationSec))
        }
        sid
    }

    suspend fun startEmpty(name: String = "Allenamento libero"): Long =
        dao.insertSession(SessionEntity(templateId = null, name = name, epochDay = today(), startedAt = now(), status = SessionStatus.IN_PROGRESS))

    /** Ripete una sessione passata: stessi esercizi e stessi valori, da completare oggi. */
    suspend fun repeatSession(sourceId: Long): Long = db.withTransaction {
        val src = dao.getSession(sourceId) ?: error("Sessione non trovata")
        val sid = dao.insertSession(
            SessionEntity(templateId = src.templateId, name = src.name, epochDay = today(), startedAt = now(), status = SessionStatus.IN_PROGRESS),
        )
        dao.getSessionExercises(sourceId).forEach { se ->
            val newSe = dao.insertSessionExercise(se.copy(id = 0, sessionId = sid))
            dao.insertSets(
                dao.getSetsForSessionExercise(se.id).map {
                    it.copy(id = 0, sessionExerciseId = newSe, completed = false, completedAt = null, isFailed = false)
                },
            )
        }
        sid
    }

    suspend fun addExercises(sessionId: Long, exerciseIds: List<Long>, defaultRestSec: Int) = db.withTransaction {
        var pos = (dao.getSessionExercises(sessionId).maxOfOrNull { it.position } ?: -1) + 1
        exerciseIds.forEach { exId ->
            val ex = exerciseDao.get(exId) ?: return@forEach
            val seId = dao.insertSessionExercise(
                SessionExerciseEntity(sessionId = sessionId, exerciseId = exId, position = pos++, restSec = defaultRestSec),
            )
            val timed = ex.type.isTimed
            dao.insertSets(buildSets(seId, exId, sessionId, 0, 3, if (timed) 0 else 10, 0.0, if (timed) 60 else 0))
        }
    }

    suspend fun addSet(block: ExerciseBlock) {
        val last = block.sets.lastOrNull { !it.isWarmup } ?: block.sets.lastOrNull()
        dao.insertSet(
            SetEntity(
                sessionExerciseId = block.sessionExercise.id,
                position = (block.sets.maxOfOrNull { it.position } ?: -1) + 1,
                weight = last?.weight ?: 0.0,
                reps = last?.reps ?: 0,
                durationSec = last?.durationSec ?: 0,
                distanceKm = last?.distanceKm ?: 0.0,
                targetReps = last?.targetReps ?: 0,
            ),
        )
    }

    suspend fun updateSet(set: SetEntity) = dao.updateSet(set)
    suspend fun deleteSet(id: Long) = dao.deleteSet(id)
    suspend fun updateSessionExercise(e: SessionExerciseEntity) = dao.updateSessionExercise(e)
    suspend fun updateSession(s: SessionEntity) = dao.updateSession(s)

    suspend fun removeExercise(sessionExerciseId: Long) = dao.deleteSessionExercise(sessionExerciseId)

    suspend fun moveExercise(sessionId: Long, sessionExerciseId: Long, delta: Int) = db.withTransaction {
        val list = dao.getSessionExercises(sessionId).toMutableList()
        val i = list.indexOfFirst { it.id == sessionExerciseId }
        val j = i + delta
        if (i < 0 || j !in list.indices) return@withTransaction
        val item = list.removeAt(i)
        list.add(j, item)
        dao.updateSessionExercises(list.mapIndexed { idx, e -> e.copy(position = idx) })
    }

    /**
     * Conclude la sessione: le serie non completate vengono scartate.
     * Se non c'è nessuna serie completata la sessione viene eliminata e restituisce false.
     */
    suspend fun finish(sessionId: Long): Boolean = db.withTransaction {
        val s = dao.getSession(sessionId) ?: return@withTransaction false
        dao.deleteIncompleteSets(sessionId)
        dao.deleteEmptyExercises(sessionId)
        if (dao.getSessionExercises(sessionId).isEmpty()) {
            dao.deleteSession(sessionId)
            false
        } else {
            val end = if (s.status == SessionStatus.COMPLETED) s.endedAt ?: now() else now()
            dao.updateSession(s.copy(status = SessionStatus.COMPLETED, endedAt = end))
            true
        }
    }

    suspend fun delete(sessionId: Long) = dao.deleteSession(sessionId)

    /** Cambia la data di una sessione registrata mantenendo orario e durata. */
    suspend fun changeDate(sessionId: Long, date: LocalDate) {
        val s = dao.getSession(sessionId) ?: return
        val zone = ZoneId.systemDefault()
        val oldStart = Instant.ofEpochMilli(s.startedAt).atZone(zone)
        val newStart = date.atTime(oldStart.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
        val shift = newStart - s.startedAt
        dao.updateSession(s.copy(epochDay = date.toEpochDay(), startedAt = newStart, endedAt = s.endedAt?.plus(shift)))
    }
}
