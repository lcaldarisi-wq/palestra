package it.palestra.app

import it.palestra.app.data.ExerciseCatalog
import it.palestra.app.data.Stats
import it.palestra.app.data.db.ExerciseEntity
import it.palestra.app.data.db.ExerciseType
import it.palestra.app.data.db.MuscleGroup
import it.palestra.app.data.db.SessionEntity
import it.palestra.app.data.db.SessionStatus
import it.palestra.app.data.db.SetRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatsTest {

    private val day0 = LocalDate.of(2026, 11, 2) // lunedì

    private fun row(
        session: Long, day: LocalDate, ex: Long, w: Double, r: Int,
        warm: Boolean = false, failed: Boolean = false, dur: Int = 0, km: Double = 0.0,
    ) = SetRow(session, day.toEpochDay(), session * 1000, null, ex, w, r, dur, km, warm, failed)

    private fun session(id: Long, day: LocalDate, minutes: Int) = SessionEntity(
        id = id, templateId = null, name = "S$id", epochDay = day.toEpochDay(),
        startedAt = id * 1000, endedAt = id * 1000 + minutes * 60_000L, status = SessionStatus.COMPLETED,
    )

    @Test
    fun volumeExcludesWarmupButIncludesFailed() {
        val rows = listOf(
            row(1, day0, 1, 40.0, 10, warm = true),
            row(1, day0, 1, 80.0, 8),
            row(1, day0, 1, 80.0, 5, failed = true),
        )
        assertEquals(80.0 * 8 + 80.0 * 5, Stats.volume(rows), 0.001)
    }

    @Test
    fun recordsFollowRules() {
        val rows = listOf(
            row(1, day0, 1, 100.0, 12, warm = true), // riscaldamento: ignorato
            row(1, day0, 1, 80.0, 8),
            row(2, day0.plusDays(2), 1, 85.0, 5),
            row(2, day0.plusDays(2), 1, 85.0, 3, failed = true),
            row(3, day0.plusDays(4), 1, 60.0, 15, failed = true), // fallita: non vale per le ripetizioni
            row(3, day0.plusDays(4), 1, 60.0, 12),
        )
        val r = Stats.records(rows)
        assertEquals(85.0, r.maxWeight!!.weight, 0.0)
        assertEquals(5, r.maxWeight!!.reps)
        assertEquals(12, r.maxReps!!.reps)
        assertEquals(60.0, r.maxReps!!.weight, 0.0)
    }

    @Test
    fun maxSetVolumeConsidersFailedSets() {
        val rows = listOf(row(1, day0, 1, 80.0, 8), row(2, day0, 1, 60.0, 15, failed = true))
        assertEquals(900.0, Stats.setVolume(Stats.records(rows).maxSetVolume!!), 0.0)
    }

    @Test
    fun epley() {
        assertEquals(100.0, Stats.estimated1Rm(100.0, 1), 0.0)
        assertEquals(100.0 * (1 + 10 / 30.0), Stats.estimated1Rm(100.0, 10), 0.0001)
        assertEquals(0.0, Stats.estimated1Rm(0.0, 10), 0.0)
    }

    @Test
    fun summaryOnlyCountsPeriod() {
        val sessions = listOf(session(1, day0, 60), session(2, day0.plusDays(3), 40), session(3, day0.plusDays(8), 50))
        val rows = listOf(row(1, day0, 1, 50.0, 10), row(2, day0.plusDays(3), 1, 50.0, 10, warm = true), row(3, day0.plusDays(8), 1, 50.0, 10))
        val s = Stats.summary(sessions, rows, day0, day0.plusDays(6))
        assertEquals(2, s.sessions)
        assertEquals(500.0, s.volume, 0.0)
        assertEquals(1, s.workingSets)
        assertEquals(100, s.totalMinutes)
        assertEquals(50, s.avgMinutes)
    }

    @Test
    fun progressionIsChronological() {
        val rows = listOf(row(2, day0.plusDays(2), 1, 82.5, 6), row(1, day0, 1, 80.0, 8), row(1, day0, 1, 80.0, 7))
        val p = Stats.progression(rows)
        assertEquals(listOf(1L, 2L), p.map { it.sessionId })
        assertEquals(80.0 * 15, p[0].volume, 0.0)
        assertEquals(82.5, p[1].maxWeight, 0.0)
    }

    @Test
    fun latestPrIgnoresFirstTime() {
        val rows = listOf(row(1, day0, 1, 80.0, 8), row(1, day0, 2, 100.0, 5))
        assertNull(Stats.latestWeightPr(rows))
        val more = rows + row(2, day0.plusDays(2), 1, 85.0, 5) + row(2, day0.plusDays(2), 2, 100.0, 5)
        val pr = Stats.latestWeightPr(more)
        assertNotNull(pr)
        assertEquals(1L, pr!!.exerciseId)
        assertEquals(85.0, pr.weight, 0.0)
    }

    @Test
    fun weeklyFrequency() {
        val sessions = listOf(session(1, day0, 60), session(2, day0.plusDays(1), 60), session(3, day0.minusDays(3), 60))
        val f = Stats.weeklyFrequency(sessions, day0.plusDays(2), weeks = 2)
        assertEquals(listOf(1, 2), f.map { it.second })
        assertEquals(day0, f.last().first)
    }

    @Test
    fun setsByGroupCountsSecondaryAsHalf() {
        val ex = mapOf(
            1L to ExerciseEntity(1, "Panca", MuscleGroup.PETTO, MuscleGroup.TRICIPITI.name, "Bilanciere", ExerciseType.WEIGHT),
        )
        val g = Stats.setsByGroup(listOf(row(1, day0, 1, 80.0, 8), row(1, day0, 1, 80.0, 8)), ex).toMap()
        assertEquals(2.0, g[MuscleGroup.PETTO]!!, 0.0)
        assertEquals(1.0, g[MuscleGroup.TRICIPITI]!!, 0.0)
    }

    @Test
    fun catalogHasUniqueNames() {
        val names = ExerciseCatalog.items.map { it.name.lowercase() }
        assertEquals(names.size, names.toSet().size)
        assertTrue(ExerciseCatalog.items.size >= 140)
    }
}
