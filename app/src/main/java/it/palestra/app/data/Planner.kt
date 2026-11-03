package it.palestra.app.data

import it.palestra.app.data.db.PlanOverrideEntity
import it.palestra.app.data.db.ScheduleEntity
import it.palestra.app.data.db.SessionEntity
import java.time.LocalDate

enum class DayStatus { COMPLETED, PLANNED, MISSED, REST }

/** Calcolo puro della programmazione: quale scheda è prevista in un giorno e con che esito. */
class Planner(
    schedule: List<ScheduleEntity>,
    overrides: List<PlanOverrideEntity>,
    private val validTemplateIds: Set<Long>,
) {
    private val byDay = schedule.associate { it.dayOfWeek to it.templateId }
    private val overrideByDate = overrides.associateBy { it.epochDay }

    fun isOverridden(date: LocalDate) = overrideByDate.containsKey(date.toEpochDay())

    fun plannedTemplateId(date: LocalDate): Long? {
        val o = overrideByDate[date.toEpochDay()]
        val id = if (o != null) o.templateId else byDay[date.dayOfWeek.value]
        return id?.takeIf { it in validTemplateIds }
    }

    fun status(date: LocalDate, today: LocalDate, sessionsOnDay: List<SessionEntity>): DayStatus = when {
        sessionsOnDay.isNotEmpty() -> DayStatus.COMPLETED
        plannedTemplateId(date) == null -> DayStatus.REST
        date.isBefore(today) -> DayStatus.MISSED
        else -> DayStatus.PLANNED
    }
}
