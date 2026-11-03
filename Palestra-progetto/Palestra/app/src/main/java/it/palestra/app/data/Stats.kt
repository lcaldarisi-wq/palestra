package it.palestra.app.data

import it.palestra.app.data.db.durationMs
import it.palestra.app.data.db.secondary
import it.palestra.app.data.db.ExerciseEntity
import it.palestra.app.data.db.MuscleGroup
import it.palestra.app.data.db.SessionEntity
import it.palestra.app.data.db.SetRow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Tutte le statistiche sono calcolate al volo dalle serie registrate (nessun dato duplicato),
 * così restano coerenti anche quando si modifica una sessione passata.
 *
 * Regole:
 * - le serie di riscaldamento non contano né nel volume né nei record;
 * - le serie fallite contano nel volume e nel peso massimo, ma non nel record di ripetizioni.
 */
object Stats {

    fun setVolume(r: SetRow): Double = r.weight * r.reps

    fun working(rows: List<SetRow>) = rows.filter { !it.isWarmup }

    fun volume(rows: List<SetRow>): Double = working(rows).sumOf { setVolume(it) }

    /** Massimale stimato (formula di Epley), considerato affidabile fino a 12 ripetizioni. */
    fun estimated1Rm(weight: Double, reps: Int): Double = when {
        reps <= 0 || weight <= 0 -> 0.0
        reps == 1 -> weight
        else -> weight * (1 + reps / 30.0)
    }

    data class PeriodSummary(
        val sessions: Int,
        val volume: Double,
        val workingSets: Int,
        val reps: Int,
        val totalMinutes: Int,
    ) {
        val avgMinutes: Int get() = if (sessions == 0) 0 else totalMinutes / sessions
    }

    fun summary(sessions: List<SessionEntity>, rows: List<SetRow>, from: LocalDate, to: LocalDate): PeriodSummary {
        val f = from.toEpochDay()
        val t = to.toEpochDay()
        val s = sessions.filter { it.epochDay in f..t }
        val r = working(rows.filter { it.epochDay in f..t })
        return PeriodSummary(
            sessions = s.size,
            volume = r.sumOf { setVolume(it) },
            workingSets = r.size,
            reps = r.sumOf { it.reps },
            totalMinutes = s.sumOf { (it.durationMs / 60_000L).toInt() },
        )
    }

    data class Records(
        val maxWeight: SetRow?,
        val maxReps: SetRow?,
        val maxSetVolume: SetRow?,
        val bestSessionVolume: Pair<Long, Double>?,
        val best1Rm: Double,
        val longestDuration: SetRow?,
        val longestDistance: SetRow?,
    ) {
        val isEmpty get() = maxWeight == null && maxReps == null && longestDuration == null
    }

    /** Record personali di un singolo esercizio (righe già filtrate per esercizio). */
    fun records(rows: List<SetRow>): Records {
        val w = working(rows)
        return Records(
            maxWeight = w.filter { it.reps > 0 && it.weight > 0 }
                .maxWithOrNull(compareBy<SetRow>({ it.weight }, { it.reps }, { -it.startedAt })),
            maxReps = w.filter { !it.isFailed && it.reps > 0 }
                .maxWithOrNull(compareBy<SetRow>({ it.reps }, { it.weight }, { -it.startedAt })),
            maxSetVolume = w.filter { setVolume(it) > 0 }.maxByOrNull { setVolume(it) },
            bestSessionVolume = w.groupBy { it.sessionId }
                .map { (_, l) -> l.first().epochDay to l.sumOf { setVolume(it) } }
                .filter { it.second > 0 }
                .maxByOrNull { it.second },
            best1Rm = w.filter { it.reps in 1..12 }.maxOfOrNull { estimated1Rm(it.weight, it.reps) } ?: 0.0,
            longestDuration = w.filter { it.durationSec > 0 }.maxByOrNull { it.durationSec },
            longestDistance = w.filter { it.distanceKm > 0 }.maxByOrNull { it.distanceKm },
        )
    }

    data class SessionPoint(
        val sessionId: Long,
        val epochDay: Long,
        val startedAt: Long,
        val maxWeight: Double,
        val volume: Double,
        val est1Rm: Double,
        val totalReps: Int,
        val durationSec: Int,
        val distanceKm: Double,
    )

    /** Andamento sessione per sessione di un esercizio, in ordine cronologico. */
    fun progression(rows: List<SetRow>): List<SessionPoint> =
        working(rows).groupBy { it.sessionId }.map { (sid, l) ->
            SessionPoint(
                sessionId = sid,
                epochDay = l.first().epochDay,
                startedAt = l.first().startedAt,
                maxWeight = l.maxOf { it.weight },
                volume = l.sumOf { setVolume(it) },
                est1Rm = l.filter { it.reps in 1..12 }.maxOfOrNull { estimated1Rm(it.weight, it.reps) } ?: 0.0,
                totalReps = l.sumOf { it.reps },
                durationSec = l.sumOf { it.durationSec },
                distanceKm = l.sumOf { it.distanceKm },
            )
        }.sortedBy { it.startedAt }

    /** Serie allenanti per gruppo muscolare (il gruppo secondario conta mezza serie). */
    fun setsByGroup(rows: List<SetRow>, exercises: Map<Long, ExerciseEntity>): List<Pair<MuscleGroup, Double>> {
        val acc = mutableMapOf<MuscleGroup, Double>()
        working(rows).forEach { r ->
            val ex = exercises[r.exerciseId] ?: return@forEach
            acc[ex.muscleGroup] = (acc[ex.muscleGroup] ?: 0.0) + 1.0
            ex.secondary?.let { acc[it] = (acc[it] ?: 0.0) + 0.5 }
        }
        return acc.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }

    fun weekStart(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Numero di allenamenti per settimana nelle ultime [weeks] settimane (dalla più vecchia). */
    fun weeklyFrequency(sessions: List<SessionEntity>, today: LocalDate, weeks: Int = 8): List<Pair<LocalDate, Int>> {
        val current = weekStart(today)
        return (weeks - 1 downTo 0).map { i ->
            val start = current.minusWeeks(i.toLong())
            val end = start.plusDays(6)
            start to sessions.count { it.epochDay in start.toEpochDay()..end.toEpochDay() }
        }
    }

    /**
     * Ultimo record di carico battuto (escludendo la prima volta in assoluto di ogni esercizio).
     * Restituisce la serie che ha stabilito il record, o null.
     */
    fun latestWeightPr(rows: List<SetRow>): SetRow? {
        val best = mutableMapOf<Long, Double>()
        var latest: SetRow? = null
        working(rows).filter { it.reps > 0 && it.weight > 0 }
            .sortedBy { it.startedAt }
            .groupBy { it.sessionId }
            .forEach { (_, sessionRows) ->
                // confronta con il massimo delle sessioni precedenti, poi aggiorna
                val updates = mutableMapOf<Long, SetRow>()
                sessionRows.forEach { r ->
                    val prev = best[r.exerciseId]
                    if (prev != null && r.weight > prev) {
                        val u = updates[r.exerciseId]
                        if (u == null || r.weight > u.weight) updates[r.exerciseId] = r
                    }
                }
                sessionRows.groupBy { it.exerciseId }.forEach { (ex, l) ->
                    best[ex] = maxOf(best[ex] ?: 0.0, l.maxOf { it.weight })
                }
                updates.values.maxByOrNull { it.weight }?.let { latest = it }
            }
        return latest
    }
}
