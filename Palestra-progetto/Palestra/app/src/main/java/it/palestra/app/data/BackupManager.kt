package it.palestra.app.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import it.palestra.app.data.db.AppDatabase
import it.palestra.app.data.db.ExerciseEntity
import it.palestra.app.data.db.ExerciseType
import it.palestra.app.data.db.MuscleGroup
import it.palestra.app.data.db.PlanOverrideEntity
import it.palestra.app.data.db.ScheduleEntity
import it.palestra.app.data.db.SessionEntity
import it.palestra.app.data.db.SessionExerciseEntity
import it.palestra.app.data.db.SessionStatus
import it.palestra.app.data.db.SetEntity
import it.palestra.app.data.db.TemplateEntity
import it.palestra.app.data.db.TemplateExerciseEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Esporta e importa tutti i dati in un unico file JSON leggibile.
 * È il modo consigliato per passare a un nuovo telefono: esporti, copi il file (Drive, PC, e-mail…)
 * e lo importi sull'altro dispositivo.
 */
class BackupManager(private val context: Context, private val db: AppDatabase) {

    data class Result(val sessions: Int, val templates: Int, val exercises: Int)

    suspend fun export(uri: Uri): Result = withContext(Dispatchers.IO) {
        val ex = db.exerciseDao().getAll()
        val tDao = db.templateDao()
        val sDao = db.sessionDao()
        val templates = tDao.getAll()
        val sessions = sDao.getAllSessions()
        val root = JSONObject()
            .put("format", "palestra-backup")
            .put("version", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("exercises", JSONArray(ex.map { it.toJson() }))
            .put("templates", JSONArray(templates.map { it.toJson() }))
            .put("templateExercises", JSONArray(tDao.getAllExercises().map { it.toJson() }))
            .put("schedule", JSONArray(tDao.getSchedule().map { JSONObject().put("dayOfWeek", it.dayOfWeek).put("templateId", it.templateId) }))
            .put("overrides", JSONArray(tDao.getOverrides().map { JSONObject().put("epochDay", it.epochDay).put("templateId", it.templateId ?: JSONObject.NULL) }))
            .put("sessions", JSONArray(sessions.map { it.toJson() }))
            .put("sessionExercises", JSONArray(sDao.getAllSessionExercises().map { it.toJson() }))
            .put("sets", JSONArray(sDao.getAllSets().map { it.toJson() }))
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("Impossibile scrivere il file")
        out.use { it.write(root.toString(1).toByteArray(Charsets.UTF_8)) }
        Result(sessions.size, templates.size, ex.size)
    }

    suspend fun import(uri: Uri): Result = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Impossibile leggere il file")
        val root = JSONObject(text)
        require(root.optString("format") == "palestra-backup") { "Il file non è un backup di Palestra" }

        val exercises = root.getJSONArray("exercises").objects().map { exerciseFrom(it) }
        val templates = root.getJSONArray("templates").objects().map {
            TemplateEntity(it.getLong("id"), it.getString("name"), it.optString("notes"), it.optInt("position"))
        }
        val templateExercises = root.getJSONArray("templateExercises").objects().map { templateExerciseFrom(it) }
        val schedule = root.getJSONArray("schedule").objects().map { ScheduleEntity(it.getInt("dayOfWeek"), it.getLong("templateId")) }
        val overrides = root.getJSONArray("overrides").objects().map { PlanOverrideEntity(it.getLong("epochDay"), it.optLongOrNull("templateId")) }
        val sessions = root.getJSONArray("sessions").objects().map { sessionFrom(it) }
        val sessionExercises = root.getJSONArray("sessionExercises").objects().map { sessionExerciseFrom(it) }
        val sets = root.getJSONArray("sets").objects().map { setFrom(it) }

        val m = db.maintenanceDao()
        db.withTransaction {
            m.clearSets(); m.clearSessionExercises(); m.clearSessions()
            m.clearTemplateExercises(); m.clearSchedule(); m.clearOverrides(); m.clearTemplates()
            m.clearExercises()
            m.insertExercises(exercises)
            m.insertTemplates(templates)
            m.insertTemplateExercises(templateExercises)
            m.insertSchedule(schedule)
            m.insertOverrides(overrides)
            m.insertSessions(sessions)
            m.insertSessionExercises(sessionExercises)
            m.insertSets(sets)
        }
        Result(sessions.size, templates.size, exercises.size)
    }

    // --- serializzazione ---

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.optLongOrNull(key: String): Long? = if (!has(key) || isNull(key)) null else getLong(key)

    private fun ExerciseEntity.toJson() = JSONObject()
        .put("id", id).put("name", name).put("muscleGroup", muscleGroup.name)
        .put("secondaryGroup", secondaryGroup ?: JSONObject.NULL)
        .put("equipment", equipment).put("type", type.name).put("notes", notes)
        .put("isCustom", isCustom).put("archived", archived)

    private fun exerciseFrom(o: JSONObject) = ExerciseEntity(
        id = o.getLong("id"),
        name = o.getString("name"),
        muscleGroup = MuscleGroup.fromName(o.getString("muscleGroup")) ?: MuscleGroup.PETTO,
        secondaryGroup = if (o.isNull("secondaryGroup")) null else o.optString("secondaryGroup"),
        equipment = o.optString("equipment"),
        type = ExerciseType.entries.firstOrNull { it.name == o.optString("type") } ?: ExerciseType.WEIGHT,
        notes = o.optString("notes"),
        isCustom = o.optBoolean("isCustom"),
        archived = o.optBoolean("archived"),
    )

    private fun TemplateEntity.toJson() = JSONObject().put("id", id).put("name", name).put("notes", notes).put("position", position)

    private fun TemplateExerciseEntity.toJson() = JSONObject()
        .put("id", id).put("templateId", templateId).put("exerciseId", exerciseId).put("position", position)
        .put("sets", sets).put("reps", reps).put("weight", weight).put("durationSec", durationSec)
        .put("restSec", restSec).put("warmupSets", warmupSets).put("supersetGroup", supersetGroup)
        .put("autoTimer", autoTimer).put("notes", notes)

    private fun templateExerciseFrom(o: JSONObject) = TemplateExerciseEntity(
        id = o.getLong("id"), templateId = o.getLong("templateId"), exerciseId = o.getLong("exerciseId"),
        position = o.optInt("position"), sets = o.optInt("sets", 3), reps = o.optInt("reps", 10),
        weight = o.optDouble("weight", 0.0), durationSec = o.optInt("durationSec"), restSec = o.optInt("restSec", 90),
        warmupSets = o.optInt("warmupSets"), supersetGroup = o.optInt("supersetGroup"),
        autoTimer = o.optBoolean("autoTimer", true), notes = o.optString("notes"),
    )

    private fun SessionEntity.toJson() = JSONObject()
        .put("id", id).put("templateId", templateId ?: JSONObject.NULL).put("name", name).put("epochDay", epochDay)
        .put("startedAt", startedAt).put("endedAt", endedAt ?: JSONObject.NULL).put("status", status.name)
        .put("notes", notes).put("currentIndex", currentIndex)

    private fun sessionFrom(o: JSONObject) = SessionEntity(
        id = o.getLong("id"), templateId = o.optLongOrNull("templateId"), name = o.getString("name"),
        epochDay = o.getLong("epochDay"), startedAt = o.getLong("startedAt"), endedAt = o.optLongOrNull("endedAt"),
        status = if (o.optString("status") == SessionStatus.IN_PROGRESS.name) SessionStatus.IN_PROGRESS else SessionStatus.COMPLETED,
        notes = o.optString("notes"), currentIndex = o.optInt("currentIndex"),
    )

    private fun SessionExerciseEntity.toJson() = JSONObject()
        .put("id", id).put("sessionId", sessionId).put("exerciseId", exerciseId).put("position", position)
        .put("restSec", restSec).put("supersetGroup", supersetGroup).put("autoTimer", autoTimer).put("notes", notes)

    private fun sessionExerciseFrom(o: JSONObject) = SessionExerciseEntity(
        id = o.getLong("id"), sessionId = o.getLong("sessionId"), exerciseId = o.getLong("exerciseId"),
        position = o.optInt("position"), restSec = o.optInt("restSec", 90), supersetGroup = o.optInt("supersetGroup"),
        autoTimer = o.optBoolean("autoTimer", true), notes = o.optString("notes"),
    )

    private fun SetEntity.toJson() = JSONObject()
        .put("id", id).put("sessionExerciseId", sessionExerciseId).put("position", position)
        .put("weight", weight).put("reps", reps).put("durationSec", durationSec).put("distanceKm", distanceKm)
        .put("isWarmup", isWarmup).put("isFailed", isFailed).put("completed", completed)
        .put("completedAt", completedAt ?: JSONObject.NULL).put("targetReps", targetReps)

    private fun setFrom(o: JSONObject) = SetEntity(
        id = o.getLong("id"), sessionExerciseId = o.getLong("sessionExerciseId"), position = o.optInt("position"),
        weight = o.optDouble("weight", 0.0), reps = o.optInt("reps"), durationSec = o.optInt("durationSec"),
        distanceKm = o.optDouble("distanceKm", 0.0), isWarmup = o.optBoolean("isWarmup"), isFailed = o.optBoolean("isFailed"),
        completed = o.optBoolean("completed"), completedAt = o.optLongOrNull("completedAt"), targetReps = o.optInt("targetReps"),
    )
}
