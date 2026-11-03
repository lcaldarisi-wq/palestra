package it.palestra.app

import it.palestra.app.data.DayStatus
import it.palestra.app.data.Planner
import it.palestra.app.data.db.PlanOverrideEntity
import it.palestra.app.data.db.ScheduleEntity
import it.palestra.app.data.db.SessionEntity
import it.palestra.app.data.db.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class PlannerTest {
    private val monday = LocalDate.of(2026, 11, 2)
    private val schedule = listOf(ScheduleEntity(1, 10), ScheduleEntity(2, 20), ScheduleEntity(4, 30))

    @Test
    fun weeklyScheduleAndOverrides() {
        val p = Planner(schedule, listOf(PlanOverrideEntity(monday.plusDays(1).toEpochDay(), null), PlanOverrideEntity(monday.plusDays(2).toEpochDay(), 20)), setOf(10, 20, 30))
        assertEquals(10L, p.plannedTemplateId(monday))
        assertNull(p.plannedTemplateId(monday.plusDays(1))) // spostato: riposo
        assertEquals(20L, p.plannedTemplateId(monday.plusDays(2))) // recuperato mercoledì
        assertEquals(30L, p.plannedTemplateId(monday.plusDays(3)))
        assertNull(p.plannedTemplateId(monday.plusDays(4)))
    }

    @Test
    fun deletedTemplatesAreIgnored() {
        val p = Planner(schedule, emptyList(), setOf(10))
        assertNull(p.plannedTemplateId(monday.plusDays(1)))
    }

    @Test
    fun statuses() {
        val p = Planner(schedule, emptyList(), setOf(10, 20, 30))
        val today = monday.plusDays(2)
        val done = listOf(SessionEntity(1, 10, "Push", monday.toEpochDay(), 0, 1, SessionStatus.COMPLETED))
        assertEquals(DayStatus.COMPLETED, p.status(monday, today, done))
        assertEquals(DayStatus.MISSED, p.status(monday.plusDays(1), today, emptyList()))
        assertEquals(DayStatus.REST, p.status(today, today, emptyList()))
        assertEquals(DayStatus.PLANNED, p.status(monday.plusDays(3), today, emptyList()))
    }
}
