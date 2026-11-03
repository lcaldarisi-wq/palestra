package it.palestra.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import it.palestra.app.data.Stats
import it.palestra.app.data.db.ExerciseEntity
import it.palestra.app.data.db.SessionEntity
import it.palestra.app.data.db.SetRow
import it.palestra.app.data.db.TemplateEntity
import it.palestra.app.data.db.TemplateExerciseEntity
import it.palestra.app.ui.Fmt
import it.palestra.app.ui.Nav
import it.palestra.app.ui.components.*
import it.palestra.app.ui.theme.Pal
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class WeekDay(val date: LocalDate, val status: DayStatus, val isToday: Boolean)

data class PlannedWorkout(
    val template: TemplateEntity,
    val lines: List<Pair<String, String>>,
    val totalSets: Int,
    val estMinutes: Int,
)

data class TodayState(
    val loaded: Boolean = false,
    val today: LocalDate = LocalDate.now(),
    val week: List<WeekDay> = emptyList(),
    val planned: PlannedWorkout? = null,
    val doneToday: SessionEntity? = null,
    val templates: List<TemplateEntity> = emptyList(),
    val active: SessionEntity? = null,
    val weekDone: Int = 0,
    val weekPlanned: Int = 0,
    val weekVolume: Double = 0.0,
    val weekMinutes: Int = 0,
    val lastPr: Pair<String, SetRow>? = null,
)

class TodayViewModel(private val c: AppContainer) : ViewModel() {

    private data class Base(
        val templates: List<TemplateEntity>,
        val tEx: List<TemplateExerciseEntity>,
        val exercises: List<ExerciseEntity>,
    )

    private val base = combine(c.templates.templates, c.templates.templateExercises, c.exercises.all) { t, te, e -> Base(t, te, e) }

    val state: StateFlow<TodayState> = combine(
        base, c.templates.planner, c.sessions.completed, c.sessions.setRows, c.sessions.active,
    ) { b, planner, sessions, rows, active ->
        val today = LocalDate.now()
        val weekStart = Stats.weekStart(today)
        val byDay = sessions.groupBy { it.epochDay }
        val week = (0..6).map { i ->
            val d = weekStart.plusDays(i.toLong())
            WeekDay(d, planner.status(d, today, byDay[d.toEpochDay()].orEmpty()), d == today)
        }
        val exById = b.exercises.associateBy { it.id }
        val planned = planner.plannedTemplateId(today)?.let { tid ->
            val t = b.templates.firstOrNull { it.id == tid } ?: return@let null
            val items = b.tEx.filter { it.templateId == tid }
            val sets = items.sumOf { it.sets + it.warmupSets }
            val minutes = items.sumOf { (it.sets + it.warmupSets) * (45 + it.restSec) } / 60
            PlannedWorkout(
                template = t,
                lines = items.map { te ->
                    val ex = exById[te.exerciseId]
                    val right = if (ex?.type?.isTimed == true) "${te.sets} × ${Fmt.clock(te.durationSec)}" else "${te.sets} × ${te.reps}"
                    (ex?.name ?: "?") to right
                },
                totalSets = sets,
                estMinutes = minutes,
            )
        }
        val summary = Stats.summary(sessions, rows, weekStart, weekStart.plusDays(6))
        val pr = Stats.latestWeightPr(rows)?.let { r -> (exById[r.exerciseId]?.name ?: "Esercizio") to r }
        TodayState(
            loaded = true,
            today = today,
            week = week,
            planned = planned,
            doneToday = byDay[today.toEpochDay()]?.firstOrNull(),
            templates = b.templates,
            active = active,
            weekDone = summary.sessions,
            weekPlanned = week.count { it.status != DayStatus.REST },
            weekVolume = summary.volume,
            weekMinutes = summary.totalMinutes,
            lastPr = pr,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TodayState())

    fun start(templateId: Long?, onStarted: (Long) -> Unit) {
        viewModelScope.launch {
            val existing = c.sessions.getActive()
            val id = existing?.id ?: if (templateId == null) c.sessions.startEmpty() else c.sessions.startFromTemplate(templateId)
            onStarted(id)
        }
    }
}

@Composable
fun TodayScreen(nav: Nav) {
    val vm = appViewModel { TodayViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    var chooseOther by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        ScreenHeader(title = "Oggi", subtitle = Fmt.dayLong(s.today)) {
            RoundIconButton(Icons.Rounded.Settings, "Impostazioni", onClick = { nav.settings() })
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            WeekStrip(s.week)

            val active = s.active
            val planned = s.planned
            when {
                !s.loaded -> Spacer(Modifier.height(200.dp))
                active != null -> AppCard {
                    Pill("Allenamento in corso")
                    Text(active.name, style = MaterialTheme.typography.headlineSmall)
                    Text("Iniziato alle ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.ITALY).format(java.util.Date(active.startedAt))}", color = Pal.Muted)
                    PrimaryButton("Riprendi allenamento", onClick = { nav.workout(active.id) }, icon = Icons.Rounded.PlayArrow)
                }
                s.templates.isEmpty() -> AppCard {
                    EmptyState(
                        Icons.Rounded.FitnessCenter,
                        "Crea la tua prima scheda",
                        "Componi una scheda (es. Push, Pull, Legs), assegnala ai giorni della settimana e la troverai qui ogni giorno.",
                    ) { PrimaryButton("Crea scheda", onClick = { nav.template(0) }, icon = Icons.Rounded.Add) }
                }
                planned != null -> PlannedCard(
                    planned = planned,
                    doneToday = s.doneToday,
                    onStart = { vm.start(planned.template.id) { nav.workout(it) } },
                    onOpenDone = { id -> nav.session(id) },
                )
                else -> AppCard {
                    Pill("Riposo", filled = false)
                    Text("Nessun allenamento in programma oggi", style = MaterialTheme.typography.titleLarge)
                    s.doneToday?.let { d ->
                        Text("Completato: ${d.name}", color = Pal.Accent, fontWeight = FontWeight.SemiBold)
                    }
                    Text("Puoi comunque scegliere una scheda o fare un allenamento libero.", color = Pal.Muted, fontSize = 14.sp)
                }
            }

            if (s.loaded && s.active == null && s.templates.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Altra scheda", onClick = { chooseOther = true }, icon = Icons.Rounded.SwapHoriz, modifier = Modifier.weight(1f))
                    SecondaryButton("Libero", onClick = { vm.start(null) { nav.workout(it) } }, icon = Icons.Rounded.Add, modifier = Modifier.weight(1f))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Settimana", "${s.weekDone}/${s.weekPlanned}", Modifier.weight(1f))
                StatTile("Volume", Fmt.volume(s.weekVolume), Modifier.weight(1f))
                StatTile("Tempo", Fmt.minutes(s.weekMinutes), Modifier.weight(1f))
            }

            s.lastPr?.let { (name, row) ->
                AppCard(color = Pal.SurfaceLow) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Pal.Secondary), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.EmojiEvents, null, tint = Pal.Accent)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("Ultimo record personale · ${Fmt.relativeDay(LocalDate.ofEpochDay(row.epochDay))}", color = Pal.Muted, fontSize = 12.sp)
                            Text("$name · ${Fmt.kg(row.weight)} × ${row.reps}", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }

    if (chooseOther) {
        AlertDialog(
            onDismissRequest = { chooseOther = false },
            containerColor = Pal.Secondary,
            title = { Text("Scegli la scheda") },
            text = {
                Column {
                    s.templates.forEach { t ->
                        TextButton(onClick = { chooseOther = false; vm.start(t.id) { nav.workout(it) } }, modifier = Modifier.fillMaxWidth()) {
                            Text(t.name, color = Pal.Text, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { chooseOther = false }) { Text("Chiudi", color = Pal.Accent) } },
        )
    }
}

@Composable
private fun PlannedCard(planned: PlannedWorkout, doneToday: SessionEntity?, onStart: () -> Unit, onOpenDone: (Long) -> Unit) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (doneToday != null) Pill("Completato oggi") else Pill("In programma oggi")
            Spacer(Modifier.weight(1f))
            Text("~${planned.estMinutes} min", color = Pal.Muted, fontSize = 13.sp)
        }
        Text(planned.template.name, style = MaterialTheme.typography.headlineMedium)
        Text("${planned.lines.size} esercizi · ${planned.totalSets} serie", color = Pal.Muted, fontSize = 14.sp)
        planned.lines.take(4).forEach { (name, right) ->
            Row {
                Text(name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                Text(right, color = Pal.Muted, fontSize = 14.sp)
            }
        }
        if (planned.lines.size > 4) Text("+ altri ${planned.lines.size - 4} esercizi", color = Pal.Muted, fontSize = 13.sp)
        if (doneToday != null) {
            SecondaryButton("Vedi riepilogo", onClick = { onOpenDone(doneToday.id) }, modifier = Modifier.fillMaxWidth())
        } else {
            PrimaryButton("Inizia allenamento", onClick = onStart, icon = Icons.Rounded.PlayArrow)
        }
    }
}

@Composable
fun WeekStrip(week: List<WeekDay>) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        week.forEachIndexed { i, d ->
            val bg = if (d.isToday) Pal.Secondary else Color.Transparent
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(bg).padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(Fmt.weekLetters[i], fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (d.isToday) Pal.Accent else Pal.Muted)
                DayDot(d.date.dayOfMonth.toString(), d.status, d.isToday)
            }
        }
    }
}

@Composable
fun DayDot(label: String, status: DayStatus, isToday: Boolean, size: Int = 30) {
    val mod = Modifier.size(size.dp).clip(CircleShape)
    val (m, fg) = when {
        status == DayStatus.COMPLETED -> mod.background(Pal.Accent) to Pal.OnAccent
        isToday -> mod.border(2.dp, Pal.Accent, CircleShape) to Pal.Accent
        status == DayStatus.MISSED -> mod.border(1.5.dp, Pal.Failed, CircleShape) to Pal.Text
        status == DayStatus.PLANNED -> mod.border(1.5.dp, Pal.Outline, CircleShape) to Pal.Text
        else -> mod to Pal.Dim
    }
    Box(m, contentAlignment = Alignment.Center) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

