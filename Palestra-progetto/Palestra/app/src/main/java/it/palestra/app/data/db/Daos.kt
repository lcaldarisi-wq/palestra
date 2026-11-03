package it.palestra.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises")
    suspend fun getAll(): List<ExerciseEntity>

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun get(id: Long): ExerciseEntity?

    @Query("SELECT COUNT(*) FROM exercises WHERE name = :name COLLATE NOCASE AND id != :excludeId")
    suspend fun countByName(name: String, excludeId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<ExerciseEntity>)

    @Insert
    suspend fun insert(item: ExerciseEntity): Long

    @Update
    suspend fun update(item: ExerciseEntity)
}

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates ORDER BY position, id")
    fun observeTemplates(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates ORDER BY position, id")
    suspend fun getAll(): List<TemplateEntity>

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun get(id: Long): TemplateEntity?

    @Insert
    suspend fun insert(t: TemplateEntity): Long

    @Update
    suspend fun update(t: TemplateEntity)

    @Query("DELETE FROM templates WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM template_exercises WHERE templateId = :templateId ORDER BY position")
    suspend fun getExercises(templateId: Long): List<TemplateExerciseEntity>

    @Query("SELECT * FROM template_exercises ORDER BY templateId, position")
    fun observeAllExercises(): Flow<List<TemplateExerciseEntity>>

    @Query("SELECT * FROM template_exercises")
    suspend fun getAllExercises(): List<TemplateExerciseEntity>

    @Query("DELETE FROM template_exercises WHERE templateId = :templateId")
    suspend fun clearExercises(templateId: Long)

    @Insert
    suspend fun insertExercises(items: List<TemplateExerciseEntity>)

    @Query("SELECT * FROM schedule")
    fun observeSchedule(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedule")
    suspend fun getSchedule(): List<ScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSchedule(entry: ScheduleEntity)

    @Query("DELETE FROM schedule WHERE dayOfWeek = :day")
    suspend fun clearScheduleDay(day: Int)

    @Query("DELETE FROM schedule WHERE templateId = :templateId")
    suspend fun clearScheduleForTemplate(templateId: Long)

    @Query("SELECT * FROM plan_overrides")
    fun observeOverrides(): Flow<List<PlanOverrideEntity>>

    @Query("SELECT * FROM plan_overrides")
    suspend fun getOverrides(): List<PlanOverrideEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setOverride(entry: PlanOverrideEntity)

    @Query("DELETE FROM plan_overrides WHERE epochDay = :epochDay")
    suspend fun clearOverride(epochDay: Long)
}

@Dao
interface SessionDao {
    @Insert
    suspend fun insertSession(s: SessionEntity): Long

    @Update
    suspend fun updateSession(s: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSession(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observeSession(id: Long): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE status = 'IN_PROGRESS' ORDER BY startedAt DESC LIMIT 1")
    fun observeActive(): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE status = 'IN_PROGRESS' ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActive(): SessionEntity?

    @Query("SELECT * FROM sessions WHERE status = 'COMPLETED' ORDER BY startedAt DESC")
    fun observeCompleted(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions")
    suspend fun getAllSessions(): List<SessionEntity>

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Insert
    suspend fun insertSessionExercise(e: SessionExerciseEntity): Long

    @Update
    suspend fun updateSessionExercise(e: SessionExerciseEntity)

    @Update
    suspend fun updateSessionExercises(items: List<SessionExerciseEntity>)

    @Query("DELETE FROM session_exercises WHERE id = :id")
    suspend fun deleteSessionExercise(id: Long)

    @Query("SELECT * FROM session_exercises WHERE sessionId = :sessionId ORDER BY position")
    fun observeSessionExercises(sessionId: Long): Flow<List<SessionExerciseEntity>>

    @Query("SELECT * FROM session_exercises WHERE sessionId = :sessionId ORDER BY position")
    suspend fun getSessionExercises(sessionId: Long): List<SessionExerciseEntity>

    @Query("SELECT * FROM session_exercises")
    suspend fun getAllSessionExercises(): List<SessionExerciseEntity>

    @Query("SELECT DISTINCT sessionId, exerciseId FROM session_exercises")
    fun observeSessionExerciseLinks(): Flow<List<SessionExerciseLink>>

    @Insert
    suspend fun insertSet(s: SetEntity): Long

    @Insert
    suspend fun insertSets(items: List<SetEntity>)

    @Update
    suspend fun updateSet(s: SetEntity)

    @Query("DELETE FROM set_entries WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query(
        "SELECT st.* FROM set_entries st INNER JOIN session_exercises se ON st.sessionExerciseId = se.id " +
            "WHERE se.sessionId = :sessionId ORDER BY st.position",
    )
    fun observeSetsForSession(sessionId: Long): Flow<List<SetEntity>>

    @Query("SELECT * FROM set_entries WHERE sessionExerciseId = :sessionExerciseId ORDER BY position")
    suspend fun getSetsForSessionExercise(sessionExerciseId: Long): List<SetEntity>

    @Query("SELECT * FROM set_entries")
    suspend fun getAllSets(): List<SetEntity>

    @Query(
        "DELETE FROM set_entries WHERE completed = 0 AND sessionExerciseId IN " +
            "(SELECT id FROM session_exercises WHERE sessionId = :sessionId)",
    )
    suspend fun deleteIncompleteSets(sessionId: Long)

    @Query(
        "DELETE FROM session_exercises WHERE sessionId = :sessionId AND id NOT IN " +
            "(SELECT sessionExerciseId FROM set_entries)",
    )
    suspend fun deleteEmptyExercises(sessionId: Long)

    @Query(
        "SELECT se.id FROM session_exercises se INNER JOIN sessions s ON se.sessionId = s.id " +
            "WHERE se.exerciseId = :exerciseId AND s.status = 'COMPLETED' AND s.id != :excludeSessionId " +
            "AND s.startedAt < :before ORDER BY s.startedAt DESC LIMIT 1",
    )
    suspend fun lastSessionExerciseId(exerciseId: Long, excludeSessionId: Long, before: Long): Long?

    @Query(
        "SELECT s.epochDay FROM sessions s INNER JOIN session_exercises se ON se.sessionId = s.id " +
            "WHERE se.id = :sessionExerciseId",
    )
    suspend fun epochDayForSessionExercise(sessionExerciseId: Long): Long?

    @Query(
        "SELECT s.id AS sessionId, s.epochDay AS epochDay, s.startedAt AS startedAt, s.templateId AS templateId, " +
            "se.exerciseId AS exerciseId, st.weight AS weight, st.reps AS reps, st.durationSec AS durationSec, " +
            "st.distanceKm AS distanceKm, st.isWarmup AS isWarmup, st.isFailed AS isFailed " +
            "FROM set_entries st INNER JOIN session_exercises se ON st.sessionExerciseId = se.id " +
            "INNER JOIN sessions s ON se.sessionId = s.id " +
            "WHERE st.completed = 1 AND s.status = 'COMPLETED'",
    )
    fun observeCompletedSetRows(): Flow<List<SetRow>>
}

/** Operazioni massive usate da backup e ripristino. */
@Dao
interface MaintenanceDao {
    @Query("DELETE FROM set_entries") suspend fun clearSets()
    @Query("DELETE FROM session_exercises") suspend fun clearSessionExercises()
    @Query("DELETE FROM sessions") suspend fun clearSessions()
    @Query("DELETE FROM template_exercises") suspend fun clearTemplateExercises()
    @Query("DELETE FROM templates") suspend fun clearTemplates()
    @Query("DELETE FROM schedule") suspend fun clearSchedule()
    @Query("DELETE FROM plan_overrides") suspend fun clearOverrides()
    @Query("DELETE FROM exercises") suspend fun clearExercises()

    @Insert suspend fun insertExercises(items: List<ExerciseEntity>)
    @Insert suspend fun insertTemplates(items: List<TemplateEntity>)
    @Insert suspend fun insertTemplateExercises(items: List<TemplateExerciseEntity>)
    @Insert suspend fun insertSchedule(items: List<ScheduleEntity>)
    @Insert suspend fun insertOverrides(items: List<PlanOverrideEntity>)
    @Insert suspend fun insertSessions(items: List<SessionEntity>)
    @Insert suspend fun insertSessionExercises(items: List<SessionExerciseEntity>)
    @Insert suspend fun insertSets(items: List<SetEntity>)
}
