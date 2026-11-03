package it.palestra.app.ui.stats

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import it.palestra.app.AppContainer
import it.palestra.app.data.Stats
import it.palestra.app.data.db.ExerciseEntity
import it.palestra.app.data.db.ExerciseType
import it.palestra.app.data.db.SessionEntity
import it.palestra.app.data.db.SetRow
import it.palestra.app.ui.Fmt
import it.palestra.app.ui.components.*
import it.palestra.app.ui.theme.Pal
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

data class StatsData(
    val loaded: Boolean = false,
    val sessions: List<SessionEntity> = emptyList(),
    val rows: List<SetRow> = emptyList(),
    val exercises: Map<Long, ExerciseEntity> = emptyMap(),
)

class StatsViewModel(c: AppContainer) : ViewModel() {
    val data = combine(c.sessions.completed, c.sessions.setRows, c.exercises.all) { s, r, e ->
        StatsData(true, s, r, e.associateBy { it.id })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsData())
}

private enum class Metric(val label: String) { WEIGHT("Peso max"), ONE_RM("1RM stimato"), VOLUME("Volume"), REPS("Ripetizioni"), DURATION("Durata"), DISTANCE("Distanza") }

@Composable
fun StatsScreen() {
    val vm = appViewModel { StatsViewModel(it) }
    val d by vm.data.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    var monthly by remember { mutableStateOf(false) }
    var offset by remember { mutableIntStateOf(0) }
    var exerciseId by remember { mutableStateOf<Long?>(null) }
    var metric by remember { mutableStateOf(Metric.WEIGHT) }
    var pickExercise by remember { mutableStateOf(false) }

    // periodo corrente e precedente
    val (from, to) = if (monthly) {
        val m = YearMonth.from(today).plusMonths(offset.toLong())
        m.atDay(1) to m.atEndOfMonth()
    } else {
        val ws = Stats.weekStart(today).plusWeeks(offset.toLong())
        ws to ws.plusDays(6)
    }
    val (pFrom, pTo) = if (monthly) {
        val m = YearMonth.from(from).minusMonths(1); m.atDay(1) to m.atEndOfMonth()
    } else {
        from.minusWeeks(1) to to.minusWeeks(1)
    }

    val cur = remember(d, from) { Stats.summary(d.sessions, d.rows, from, to) }
    val prev = remember(d, pFrom) { Stats.summary(d.sessions, d.rows, pFrom, pTo) }

    // esercizi con dati, ordinati per uso recente
    val usedExercises = remember(d) {
        d.rows.groupBy { it.exerciseId }
            .mapNotNull { (id, l) -> d.exercises[id]?.let { it to l.maxOf { r -> r.startedAt } } }
            .sortedByDescending { it.second }.map { it.first }
    }
    LaunchedEffect(usedExercises) {
        if (exerciseId == null || usedExercises.none { it.id == exerciseId }) exerciseId = usedExercises.firstOrNull()?.id
    }
    val selectedEx = usedExercises.firstOrNull { it.id == exerciseId }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        ScreenHeader("Statistiche") {
            SegmentedToggle(listOf("Settimana", "Mese"), if (monthly) 1 else 0) { monthly = it == 1; offset = 0 }
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (d.loaded && d.sessions.isEmpty()) {
                AppCard {
                    EmptyState(Icons.Rounded.BarChart, "Ancora nessun dato", "Completa il primo allenamento: qui vedrai volumi, frequenza, record e progressi per ogni esercizio.")
                }
                return@Column
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(Icons.Rounded.ChevronLeft, "Periodo precedente", onClick = { offset -= 1 })
                Text(
                    when {
                        offset == 0 && monthly -> "Questo mese"
                        offset == 0 -> "Questa settimana"
                        monthly -> Fmt.monthYear(from)
                        else -> "${Fmt.dayMonth(from)} – ${Fmt.dayMonth(to)}"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                RoundIconButton(
                    Icons.Rounded.ChevronRight, "Periodo successivo",
                    onClick = { if (offset < 0) offset += 1 },
                    tint = if (offset < 0) Pal.Text else Pal.Dim,
                )
            }

            val vsLabel = if (monthly) "vs mese prec." else "vs sett. prec."
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Allenamenti", "${cur.sessions}", Modifier.weight(1f), sub = delta(cur.sessions.toDouble(), prev.sessions.toDouble(), vsLabel), subColor = deltaColor(cur.sessions.toDouble(), prev.sessions.toDouble()))
                StatTile("Volume", Fmt.volume(cur.volume), Modifier.weight(1f), sub = deltaPct(cur.volume, prev.volume, vsLabel), subColor = deltaColor(cur.volume, prev.volume))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Serie allenanti", "${cur.workingSets}", Modifier.weight(1f), sub = delta(cur.workingSets.toDouble(), prev.workingSets.toDouble(), vsLabel), subColor = deltaColor(cur.workingSets.toDouble(), prev.workingSets.toDouble()))
                StatTile("Ripetizioni", "${cur.reps}", Modifier.weight(1f), sub = delta(cur.reps.toDouble(), prev.reps.toDouble(), vsLabel), subColor = deltaColor(cur.reps.toDouble(), prev.reps.toDouble()))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Durata media", Fmt.minutes(cur.avgMinutes), Modifier.weight(1f))
                StatTile("Tempo totale", Fmt.minutes(cur.totalMinutes), Modifier.weight(1f))
            }

            // frequenza
            AppCard {
                Text("Allenamenti per settimana", fontWeight = FontWeight.SemiBold)
                val freq = remember(d.sessions) { Stats.weeklyFrequency(d.sessions, today, 8) }
                BarChart(freq.map { (ws, n) -> ChartPoint(Fmt.dayMonth(ws).substringBefore(' ') + "/" + ws.monthValue, n.toDouble()) })
                val avg = if (freq.isEmpty()) 0.0 else freq.sumOf { it.second } / freq.size.toDouble()
                Text("Media ultime 8 settimane: ${Fmt.num(Math.round(avg * 10) / 10.0)}", color = Pal.Muted, fontSize = 12.sp)
            }

            // progressi per esercizio
            SectionLabel("Progressi per esercizio", Modifier.padding(top = 6.dp))
            if (selectedEx != null) {
                ExerciseProgress(
                    ex = selectedEx,
                    rows = d.rows.filter { it.exerciseId == selectedEx.id },
                    metric = metric,
                    onMetric = { metric = it },
                    onPick = { pickExercise = true },
                )
            }

            // serie per gruppo muscolare
            val groups = remember(d, from) {
                Stats.setsByGroup(d.rows.filter { it.epochDay in from.toEpochDay()..to.toEpochDay() }, d.exercises)
            }
            AppCard {
                Text("Serie per gruppo muscolare", fontWeight = FontWeight.SemiBold)
                Text(if (monthly) "Nel periodo selezionato" else "Nella settimana selezionata · il gruppo secondario conta ½", color = Pal.Muted, fontSize = 12.sp)
                if (groups.isEmpty()) Text("Nessuna serie nel periodo.", color = Pal.Dim, fontSize = 13.sp)
                val maxG = groups.maxOfOrNull { it.second } ?: 1.0
                groups.forEach { (g, n) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(g.label, fontSize = 13.sp, modifier = Modifier.width(110.dp), maxLines = 1)
                        Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Pal.SurfaceLow)) {
                            Box(Modifier.fillMaxWidth((n / maxG).toFloat()).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Pal.Accent))
                        }
                        Text(Fmt.num(n), color = Pal.Muted, fontSize = 13.sp, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
                    }
                }
            }

            // record personali
            AllRecords(usedExercises, d.rows) { exerciseId = it }
        }
    }

    if (pickExercise) {
        Dialog(onDismissRequest = { pickExercise = false }) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.8f).clip(RoundedCornerShape(22.dp)).background(Pal.Secondary).padding(16.dp)) {
                Text("Scegli esercizio", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
                var q by remember { mutableStateOf("") }
                SearchField(q, { q = it }, "Cerca")
                LazyColumn(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(usedExercises.filter { matches(it, q) }, key = { it.id }) { e ->
                        ExerciseRow(e, e.id == exerciseId, trailing = {}) { exerciseId = e.id; pickExercise = false }
                    }
                }
            }
        }
    }
}

private fun delta(cur: Double, prev: Double, label: String): String {
    val dlt = cur - prev
    return when {
        dlt > 0 -> "+${Fmt.num(dlt)} $label"
        dlt < 0 -> "${Fmt.num(dlt)} $label"
        else -> "= $label"
    }
}

private fun deltaPct(cur: Double, prev: Double, label: String): String =
    if (prev <= 0) (if (cur > 0) "nuovo $label" else "= $label") else {
        val p = Math.round((cur - prev) / prev * 100).toInt()
        "${if (p > 0) "+" else ""}$p% $label"
    }

private fun deltaColor(cur: Double, prev: Double) = if (cur > prev) Pal.Accent else Pal.Muted

@Composable
private fun ExerciseProgress(ex: ExerciseEntity, rows: List<SetRow>, metric: Metric, onMetric: (Metric) -> Unit, onPick: () -> Unit) {
    val progression = remember(rows) { Stats.progression(rows) }
    val records = remember(rows) { Stats.records(rows) }
    val metrics = when (ex.type) {
        ExerciseType.CARDIO -> listOf(Metric.DURATION, Metric.DISTANCE)
        ExerciseType.TIME -> listOf(Metric.DURATION)
        ExerciseType.BODYWEIGHT -> listOf(Metric.REPS, Metric.WEIGHT, Metric.VOLUME)
        ExerciseType.WEIGHT -> listOf(Metric.WEIGHT, Metric.ONE_RM, Metric.VOLUME, Metric.REPS)
    }
    val m = if (metric in metrics) metric else metrics.first()

    AppCard {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onPick).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExerciseAvatar(ex.name)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(ex.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${progression.size} sessioni registrate", color = Pal.Muted, fontSize = 12.sp)
            }
            Text("Cambia", color = Pal.Accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Icon(Icons.Rounded.ExpandMore, null, tint = Pal.Accent)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            metrics.forEach { SelectChip(it.label, it == m, { onMetric(it) }) }
        }
        val pts = progression.takeLast(20).map { p ->
            val v = when (m) {
                Metric.WEIGHT -> p.maxWeight
                Metric.ONE_RM -> Math.round(p.est1Rm * 10) / 10.0
                Metric.VOLUME -> p.volume
                Metric.REPS -> p.totalReps.toDouble()
                Metric.DURATION -> p.durationSec / 60.0
                Metric.DISTANCE -> p.distanceKm
            }
            ChartPoint(Fmt.dayMonth(LocalDate.ofEpochDay(p.epochDay)), v)
        }
        val unit = when (m) {
            Metric.WEIGHT, Metric.ONE_RM, Metric.VOLUME -> " kg"
            Metric.DURATION -> " min"
            Metric.DISTANCE -> " km"
            Metric.REPS -> ""
        }
        if (pts.size < 2) {
            Text("Servono almeno due sessioni per vedere l'andamento.", color = Pal.Muted, fontSize = 13.sp)
        }
        LineChart(pts, unit = unit)

        // record
        SectionLabel("Record personali")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (ex.type.isTimed) {
                StatTile("Durata max", records.longestDuration?.let { Fmt.clock(it.durationSec) } ?: "–", Modifier.weight(1f))
                if (ex.type == ExerciseType.CARDIO) {
                    StatTile("Distanza max", records.longestDistance?.let { "${Fmt.num(it.distanceKm)} km" } ?: "–", Modifier.weight(1f))
                }
            } else {
                StatTile(
                    "Peso max", records.maxWeight?.let { Fmt.kg(it.weight) } ?: "–", Modifier.weight(1f),
                    sub = records.maxWeight?.let { "× ${it.reps} · ${Fmt.dayMonth(LocalDate.ofEpochDay(it.epochDay))}" },
                )
                StatTile(
                    "Rip. max", records.maxReps?.let { "${it.reps}" } ?: "–", Modifier.weight(1f),
                    sub = records.maxReps?.let { if (it.weight > 0) "a ${Fmt.kg(it.weight)}" else "corpo libero" },
                )
            }
        }
        if (!ex.type.isTimed) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    "Volume serie", records.maxSetVolume?.let { Fmt.volume(Stats.setVolume(it)) } ?: "–", Modifier.weight(1f),
                    sub = records.maxSetVolume?.let { "${Fmt.num(it.weight)} × ${it.reps}" },
                )
                StatTile(
                    "1RM stimato", if (records.best1Rm > 0) Fmt.kg(Math.round(records.best1Rm * 10) / 10.0) else "–", Modifier.weight(1f),
                    sub = records.bestSessionVolume?.let { "Best sessione ${Fmt.volume(it.second)}" },
                )
            }
        }

        // confronto ultime due sessioni
        if (progression.size >= 2) {
            val a = progression[progression.size - 2]
            val b = progression.last()
            SectionLabel("Confronto ultime due sessioni")
            CompareRow("", Fmt.dayMonth(LocalDate.ofEpochDay(a.epochDay)), Fmt.dayMonth(LocalDate.ofEpochDay(b.epochDay)), header = true)
            if (ex.type.isTimed) {
                CompareRow("Durata", Fmt.clock(a.durationSec), Fmt.clock(b.durationSec), b.durationSec > a.durationSec)
                if (ex.type == ExerciseType.CARDIO) CompareRow("Distanza", "${Fmt.num(a.distanceKm)} km", "${Fmt.num(b.distanceKm)} km", b.distanceKm > a.distanceKm)
            } else {
                CompareRow("Peso max", Fmt.kg(a.maxWeight), Fmt.kg(b.maxWeight), b.maxWeight > a.maxWeight)
                CompareRow("Ripetizioni", "${a.totalReps}", "${b.totalReps}", b.totalReps > a.totalReps)
                CompareRow("Volume", Fmt.volume(a.volume), Fmt.volume(b.volume), b.volume > a.volume)
            }
        }
    }
}

@Composable
private fun CompareRow(label: String, a: String, b: String, better: Boolean = false, header: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Pal.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(a, fontSize = 13.sp, color = if (header) Pal.Muted else Pal.TextSoft, modifier = Modifier.width(90.dp), textAlign = TextAlign.End)
        Text(
            b, fontSize = 13.sp, fontWeight = if (header) FontWeight.Normal else FontWeight.Bold,
            color = when {
                header -> Pal.Muted
                better -> Pal.Accent
                else -> Pal.Text
            },
            modifier = Modifier.width(90.dp), textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun AllRecords(exercises: List<ExerciseEntity>, rows: List<SetRow>, onSelect: (Long) -> Unit) {
    val list = remember(exercises, rows) {
        exercises.filter { !it.type.isTimed }.mapNotNull { e ->
            Stats.records(rows.filter { it.exerciseId == e.id }).maxWeight?.let { e to it }
        }.sortedByDescending { it.second.startedAt }
    }
    if (list.isEmpty()) return
    AppCard(color = Pal.Surface) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.EmojiEvents, null, tint = Pal.Accent)
            Spacer(Modifier.width(8.dp))
            Text("Tutti i record di carico", fontWeight = FontWeight.SemiBold)
        }
        list.forEach { (e, r) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onSelect(e.id) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(e.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                Text("${Fmt.kg(r.weight)} × ${r.reps}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("  ${Fmt.dayMonth(LocalDate.ofEpochDay(r.epochDay))}", color = Pal.Muted, fontSize = 12.sp)
            }
        }
    }
}
