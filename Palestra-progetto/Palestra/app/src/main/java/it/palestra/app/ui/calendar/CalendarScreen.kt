package it.palestra.app.ui.calendar

import it.palestra.app.data.db.durationMs
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import it.palestra.app.AppContainer
import it.palestra.app.data.DayStatus
import it.palestra.app.data.Planner
import it.palestra.app.data.Stats
import it.palestra.app.data.db.SessionEntity
import it.palestra.app.data.db.TemplateEntity
import it.palestra.app.ui.Fmt
import it.palestra.app.ui.Nav
import it.palestra.app.ui.components.*
import it.palestra.app.ui.theme.Pal
import it.palestra.app.ui.today.DayDot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

data class CalendarData(
    val loaded: Boolean = false,
    val planner: Planner? = null,
    val templates: List<TemplateEntity> = emptyList(),
    val sessionsByDay: Map<Long, List<SessionEntity>> = emptyMap(),
)

class CalendarViewModel(private val c: AppContainer) : ViewModel() {
    val data = combine(c.templates.planner, c.templates.templates, c.sessions.completed) { p, t, s ->
        CalendarData(true, p, t, s.groupBy { it.epochDay })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CalendarData())

    fun move(from: LocalDate, to: LocalDate, templateId: Long) = viewModelScope.launch { c.templates.moveWorkout(from, to, templateId) }
    fun setPlan(date: LocalDate, templateId: Long?) = viewModelScope.launch { c.templates.setPlan(date, templateId) }
    fun reset(date: LocalDate) = viewModelScope.launch { c.templates.resetPlan(date) }
    fun start(templateId: Long, onStarted: (Long) -> Unit) = viewModelScope.launch {
        onStarted(c.sessions.getActive()?.id ?: c.sessions.startFromTemplate(templateId))
    }
}

private enum class PickMode { MOVE, RECOVER }

@Composable
fun CalendarScreen(nav: Nav) {
    val vm = appViewModel { CalendarViewModel(it) }
    val d by vm.data.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var selected by remember { mutableStateOf(today) }
    var weekMode by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf<PickMode?>(null) }
    var chooseTemplate by remember { mutableStateOf(false) }
    val planner = d.planner

    fun statusOf(date: LocalDate): DayStatus =
        planner?.status(date, today, d.sessionsByDay[date.toEpochDay()].orEmpty()) ?: DayStatus.REST

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        ScreenHeader("Calendario") {
            SegmentedToggle(listOf("Settimana", "Mese"), if (weekMode) 0 else 1) { weekMode = it == 0 }
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // navigazione periodo
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(Icons.Rounded.ChevronLeft, "Precedente", onClick = {
                    if (weekMode) {
                        selected = selected.minusWeeks(1)
                        month = YearMonth.from(selected)
                    } else {
                        month = month.minusMonths(1)
                    }
                })
                Text(
                    if (weekMode) {
                        val ws = Stats.weekStart(selected)
                        "${Fmt.dayMonth(ws)} – ${Fmt.dayMonth(ws.plusDays(6))}"
                    } else {
                        Fmt.monthYear(month.atDay(1))
                    },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                RoundIconButton(Icons.Rounded.ChevronRight, "Successivo", onClick = {
                    if (weekMode) {
                        selected = selected.plusWeeks(1)
                        month = YearMonth.from(selected)
                    } else {
                        month = month.plusMonths(1)
                    }
                })
            }

            if (weekMode) {
                val ws = Stats.weekStart(selected)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0..6).forEach { i ->
                        val date = ws.plusDays(i.toLong())
                        WeekRow(
                            date = date,
                            status = statusOf(date),
                            isToday = date == today,
                            isSelected = date == selected,
                            plannedName = planner?.plannedTemplateId(date)?.let { id -> d.templates.firstOrNull { it.id == id }?.name },
                            done = d.sessionsByDay[date.toEpochDay()].orEmpty(),
                            onClick = { selected = date },
                        )
                    }
                }
            } else {
                MonthGrid(month, today, selected, ::statusOf) { selected = it }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Legend(Pal.Accent, filled = true, "Completato")
                    Legend(Pal.Outline, filled = false, "Programmato")
                    Legend(Pal.Failed, filled = false, "Saltato")
                }
            }

            // dettaglio giorno selezionato
            if (planner != null) {
                DayDetail(
                    date = selected,
                    today = today,
                    status = statusOf(selected),
                    planned = planner.plannedTemplateId(selected)?.let { id -> d.templates.firstOrNull { it.id == id } },
                    overridden = planner.isOverridden(selected),
                    sessions = d.sessionsByDay[selected.toEpochDay()].orEmpty(),
                    onOpenSession = { nav.session(it) },
                    onStart = { tid -> vm.start(tid) { nav.workout(it) } },
                    onMove = { pickDate = PickMode.MOVE },
                    onRecover = { pickDate = PickMode.RECOVER },
                    onRest = { vm.setPlan(selected, null) },
                    onReset = { vm.reset(selected) },
                    onPlan = { chooseTemplate = true },
                )
            }
        }
    }

    pickDate?.let { mode ->
        val tid = planner?.plannedTemplateId(selected)
        DatePickDialog(
            initial = if (mode == PickMode.RECOVER) today else selected.plusDays(1),
            title = if (mode == PickMode.RECOVER) "Recupera in data" else "Sposta a",
            onDismiss = { pickDate = null },
            onPick = { date ->
                if (tid != null) vm.move(selected, date, tid)
                selected = date
                month = YearMonth.from(date)
                pickDate = null
            },
        )
    }

    if (chooseTemplate) {
        AlertDialog(
            onDismissRequest = { chooseTemplate = false },
            containerColor = Pal.Secondary,
            title = { Text("Pianifica il ${Fmt.dayMonth(selected)}") },
            text = {
                Column {
                    if (d.templates.isEmpty()) Text("Crea prima una scheda.", color = Pal.Muted)
                    d.templates.forEach { t ->
                        TextButton(onClick = { vm.setPlan(selected, t.id); chooseTemplate = false }, modifier = Modifier.fillMaxWidth()) {
                            Text(t.name, color = Pal.Text, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { chooseTemplate = false }) { Text("Chiudi", color = Pal.Accent) } },
        )
    }
}

@Composable
private fun Legend(color: Color, filled: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(12.dp).clip(CircleShape)
                .then(if (filled) Modifier.background(color) else Modifier.border(1.5.dp, color, CircleShape)),
        )
        Spacer(Modifier.width(6.dp))
        Text(text, color = Pal.Muted, fontSize = 12.sp)
    }
}

@Composable
private fun MonthGrid(month: YearMonth, today: LocalDate, selected: LocalDate, statusOf: (LocalDate) -> DayStatus, onSelect: (LocalDate) -> Unit) {
    val first = month.atDay(1)
    val lead = first.dayOfWeek.value - 1
    val days = month.lengthOfMonth()
    val cells = lead + days
    val rows = (cells + 6) / 7
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Fmt.weekLetters.forEach {
                Text(it, color = Pal.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        repeat(rows) { r ->
            Row {
                repeat(7) { c ->
                    val dayNum = r * 7 + c - lead + 1
                    Box(Modifier.weight(1f).height(52.dp), contentAlignment = Alignment.Center) {
                        if (dayNum in 1..days) {
                            val date = month.atDay(dayNum)
                            val status = statusOf(date)
                            Column(
                                Modifier.fillMaxSize().padding(2.dp).clip(RoundedCornerShape(12.dp))
                                    .background(if (date == selected) Pal.Secondary else Color.Transparent)
                                    .clickable { onSelect(date) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                DayDot(dayNum.toString(), status, date == today, size = 32)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekRow(
    date: LocalDate,
    status: DayStatus,
    isToday: Boolean,
    isSelected: Boolean,
    plannedName: String?,
    done: List<SessionEntity>,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) Pal.Secondary else Pal.Surface)
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DayDot(date.dayOfMonth.toString(), status, isToday, size = 36)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(Fmt.weekLong[date.dayOfWeek.value - 1], color = Pal.Muted, fontSize = 12.sp)
            val title = when {
                done.isNotEmpty() -> done.joinToString(" + ") { it.name }
                plannedName != null -> plannedName
                else -> "Riposo"
            }
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (status == DayStatus.REST) Pal.Dim else Pal.Text)
        }
        Text(
            when (status) {
                DayStatus.COMPLETED -> "Fatto"
                DayStatus.PLANNED -> if (isToday) "Oggi" else "Previsto"
                DayStatus.MISSED -> "Saltato"
                DayStatus.REST -> ""
            },
            color = when (status) {
                DayStatus.COMPLETED -> Pal.Accent
                DayStatus.MISSED -> Pal.Failed
                else -> Pal.Muted
            },
            fontSize = 12.sp, fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DayDetail(
    date: LocalDate,
    today: LocalDate,
    status: DayStatus,
    planned: TemplateEntity?,
    overridden: Boolean,
    sessions: List<SessionEntity>,
    onOpenSession: (Long) -> Unit,
    onStart: (Long) -> Unit,
    onMove: () -> Unit,
    onRecover: () -> Unit,
    onRest: () -> Unit,
    onReset: () -> Unit,
    onPlan: () -> Unit,
) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Fmt.dayLong(date) + if (date == today) " · oggi" else "", color = Pal.Muted, fontSize = 12.sp)
                Text(
                    planned?.name ?: if (sessions.isNotEmpty()) sessions.first().name else "Riposo",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            when (status) {
                DayStatus.COMPLETED -> Pill("Completato")
                DayStatus.PLANNED -> Pill(if (date == today) "Oggi" else "Programmato", filled = false)
                DayStatus.MISSED -> Pill("Saltato", filled = false, color = Pal.Failed)
                DayStatus.REST -> Pill("Libero", filled = false, color = Pal.Muted)
            }
        }
        sessions.forEach { s ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Pal.SurfaceLow).clickable { onOpenSession(s.id) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.CheckCircle, null, tint = Pal.Accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(s.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(Fmt.minutes((s.durationMs / 60000).toInt()), color = Pal.Muted, fontSize = 13.sp)
                Icon(Icons.Rounded.ChevronRight, null, tint = Pal.Muted)
            }
        }
        when {
            status == DayStatus.MISSED && planned != null -> {
                Text("Questo allenamento non è stato svolto. Puoi recuperarlo in un altro giorno.", color = Pal.TextSoft, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton("Recupera", onClick = onRecover, icon = Icons.Rounded.Replay, modifier = Modifier.weight(1f))
                    SecondaryButton("Segna riposo", onClick = onRest, modifier = Modifier.weight(1f).height(56.dp))
                }
            }
            status == DayStatus.PLANNED && planned != null -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(if (date == today) "Inizia" else "Inizia ora", onClick = { onStart(planned.id) }, icon = Icons.Rounded.PlayArrow, modifier = Modifier.weight(1f))
                    SecondaryButton("Sposta", onClick = onMove, icon = Icons.Rounded.Event, modifier = Modifier.weight(1f).height(56.dp))
                }
                TextButton(onClick = onRest) { Text("Salta questo giorno (riposo)", color = Pal.Muted) }
            }
            status == DayStatus.REST && !date.isBefore(today) -> {
                SecondaryButton("Pianifica una scheda", onClick = onPlan, icon = Icons.Rounded.Add, modifier = Modifier.fillMaxWidth())
            }
        }
        if (overridden) {
            TextButton(onClick = onReset) { Text("Ripristina la programmazione settimanale", color = Pal.Accent) }
        }
    }
}

@Composable
fun DatePickDialog(initial: LocalDate, title: String, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
            }) { Text("OK", color = Pal.Accent, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla", color = Pal.TextSoft) } },
        colors = DatePickerDefaults.colors(containerColor = Pal.Secondary),
    ) {
        DatePicker(
            state = state,
            title = { Text(title, modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
            colors = DatePickerDefaults.colors(
                containerColor = Pal.Secondary,
                selectedDayContainerColor = Pal.Accent,
                selectedDayContentColor = Pal.OnAccent,
                todayDateBorderColor = Pal.Accent,
                todayContentColor = Pal.Accent,
            ),
        )
    }
}
