package it.palestra.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MuscleGroup(val label: String) {
    PETTO("Petto"),
    DORSO("Dorso"),
    SPALLE("Spalle"),
    BICIPITI("Bicipiti"),
    TRICIPITI("Tricipiti"),
    GAMBE("Gambe e glutei"),
    ADDOME("Addome"),
    AVAMBRACCI("Avambracci"),
    CARDIO("Cardio");

    companion object {
        fun fromName(name: String?): MuscleGroup? = entries.firstOrNull { it.name == name }
    }
}

enum class ExerciseType(val label: String) {
    WEIGHT("Con pesi"),
    BODYWEIGHT("Corpo libero"),
    TIME("A tempo"),
    CARDIO("Cardio");

    /** Esercizi che si registrano a tempo (e distanza) invece che a ripetizioni. */
    val isTimed: Boolean get() = this == TIME || this == CARDIO
}

enum class SessionStatus { IN_PROGRESS, COMPLETED }

@Entity(tableName = "exercises", indices = [Index(value = ["name"], unique = true)])
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val muscleGroup: MuscleGroup,
    /** Gruppo secondario (nome dell'enum MuscleGroup) per trovare l'esercizio anche lì. */
    val secondaryGroup: String? = null,
    val equipment: String,
    val type: ExerciseType,
    /** Indicazioni di esecuzione mostrate durante l'allenamento. */
    val notes: String = "",
    val isCustom: Boolean = false,
    val archived: Boolean = false,
)

val ExerciseEntity.secondary: MuscleGroup? get() = MuscleGroup.fromName(secondaryGroup)
fun ExerciseEntity.belongsTo(group: MuscleGroup) = muscleGroup == group || secondary == group

/** Una scheda di allenamento (es. Push, Pull, Legs). */
@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val notes: String = "",
    val position: Int = 0,
)

@Entity(
    tableName = "template_exercises",
    foreignKeys = [
        ForeignKey(
            entity = TemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("templateId"), Index("exerciseId")],
)
data class TemplateExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val exerciseId: Long,
    val position: Int,
    val sets: Int = 3,
    val reps: Int = 10,
    val weight: Double = 0.0,
    val durationSec: Int = 0,
    val restSec: Int = 90,
    val warmupSets: Int = 0,
    /** 0 = nessuna superserie; esercizi consecutivi con lo stesso numero formano una superserie. */
    val supersetGroup: Int = 0,
    val autoTimer: Boolean = true,
    val notes: String = "",
)

/** Programmazione settimanale fissa: giorno (1 = lunedì … 7 = domenica) → scheda. */
@Entity(tableName = "schedule")
data class ScheduleEntity(
    @PrimaryKey val dayOfWeek: Int,
    val templateId: Long,
)

/**
 * Eccezione alla programmazione per una data precisa (allenamento spostato o recuperato).
 * templateId = null significa "riposo" in quel giorno.
 */
@Entity(tableName = "plan_overrides")
data class PlanOverrideEntity(
    @PrimaryKey val epochDay: Long,
    val templateId: Long?,
)

@Entity(tableName = "sessions", indices = [Index("epochDay"), Index("templateId")])
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long?,
    val name: String,
    val epochDay: Long,
    val startedAt: Long,
    val endedAt: Long? = null,
    val status: SessionStatus,
    val notes: String = "",
    /** Esercizio visualizzato per ultimo, per riprendere esattamente da dove si era. */
    val currentIndex: Int = 0,
)

val SessionEntity.durationMs: Long get() = ((endedAt ?: System.currentTimeMillis()) - startedAt).coerceAtLeast(0)

@Entity(
    tableName = "session_exercises",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class SessionExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val position: Int,
    val restSec: Int = 90,
    val supersetGroup: Int = 0,
    val autoTimer: Boolean = true,
    val notes: String = "",
)

@Entity(
    tableName = "set_entries",
    foreignKeys = [
        ForeignKey(
            entity = SessionExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionExerciseId")],
)
data class SetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionExerciseId: Long,
    val position: Int,
    val weight: Double = 0.0,
    val reps: Int = 0,
    val durationSec: Int = 0,
    val distanceKm: Double = 0.0,
    /** Serie di riscaldamento: esclusa da volume e record. */
    val isWarmup: Boolean = false,
    /** Serie fallita (cedimento prima del target): conta nel volume, non nel record di ripetizioni. */
    val isFailed: Boolean = false,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val targetReps: Int = 0,
)

/** Riga piatta usata per statistiche e record: una serie completata di una sessione conclusa. */
data class SetRow(
    val sessionId: Long,
    val epochDay: Long,
    val startedAt: Long,
    val templateId: Long?,
    val exerciseId: Long,
    val weight: Double,
    val reps: Int,
    val durationSec: Int,
    val distanceKm: Double,
    val isWarmup: Boolean,
    val isFailed: Boolean,
)

data class SessionExerciseLink(
    val sessionId: Long,
    val exerciseId: Long,
)
