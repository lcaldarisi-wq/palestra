package it.palestra.app.ui.history

import it.palestra.app.data.db.durationMs
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import it.palestra.app.AppContainer
import it.palestra.app.data.Stats
import it.palestra.app.data.db.SessionEntity
import it.palestra.app.data.db.TemplateEntity
import it.palestra.app.ui.Fmt
import it.palestra.app.ui.Nav
import it.palestra.app.ui.calendar.DatePickDialog
import it.palestra.app.ui.components.*
import it.palestra.app.ui.theme.Pal
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class HistoryItem(
    val session: SessionEntity,
    val exerciseIds: Set<Long>,
    val exerciseNames: List<String>,
    val volume: Double,
    val sets: Int,
)

data class HistoryData(
    val loaded: Boolean = false,
    val items: List<HistoryItem> = emptyList(),
    val templates: List<TemplateEntity> = emptyList(),
)

class HistoryViewModel(c: AppContainer) : ViewModel() {
    val data = combine(c.sessions.completed, c.sessions.links, c.sessions.setRows, c.exercises.all, c.templates.templates) { s, links, rows, ex, t ->
        val names = ex.associate { it.id to it.name }
        val linksBy = links.groupBy({ it.sessionId }, { it.exerciseId })
        val rowsBy = rows.groupBy { it.sessionId }
        HistoryData(
            loaded = true,
            items = s.map { session ->
                val ids = linksBy[session.id].orEmpty()
                val r = rowsBy[session.id].orEmpty()
                HistoryItem(session, ids.toSet(), ids.mapNotNull { names[it] }, Stats.volume(r), Stats.working(r).size)
            },
            templates = t,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryData())
}

private enum class Period(val label: String, val days: Long?) { ALL("Tutto", null), D7("7 giorni", 7), D30("30 giorni", 30), D90("3 mesi", 90), DAY("Data…", null) }

@Composable
fun HistoryScreen(nav: Nav) {
    val vm = appViewModel { HistoryViewModel(it) }
    val d by vm.data.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var period by remember { mutableStateOf(Period.ALL) }
    var day by remember { mutableStateOf<LocalDate?>(null) }
    var templateId by remember { mutableStateOf<Long?>(null) }
    var pickDay by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    val filtered = d.items.filter { item ->
        val s = item.session
        val okQuery = query.isBlank() || s.name.contains(query, true) || item.exerciseNames.any { it.contains(query, true) }
        val okTemplate = templateId == null || s.templateId == templateId
        val okPeriod = when (period) {
            Period.ALL -> true
            Period.DAY -> day == null || s.epochDay == day!!.toEpochDay()
            else -> s.epochDay >= today.minusDays(period.days!! - 1).toEpochDay()
        }
        okQuery && okTemplate && okPeriod
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Storico", subtitle = if (d.loaded) "${filtered.size} allenamenti" else null)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SearchField(query, { query = it }, "Cerca per esercizio o scheda")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Period.entries) { p ->
                    val label = if (p == Period.DAY && day != null) Fmt.dayMonth(day!!) else p.label
                    SelectChip(label, period == p, {
                        period = p
                        if (p == Period.DAY) pickDay = true
                    })
                }
            }
            if (d.templates.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { SelectChip("Tutte le schede", templateId == null, { templateId = null }) }
                    items(d.templates, key = { it.id }) { t -> SelectChip(t.name, templateId == t.id, { templateId = t.id }) }
                }
            }
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (d.loaded && filtered.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Rounded.History,
                        if (d.items.isEmpty()) "Nessun allenamento registrato" else "Nessun risultato",
                        if (d.items.isEmpty()) "Gli allenamenti conclusi compariranno qui." else "Prova a cambiare ricerca o filtri.",
                    )
                }
            }
            items(filtered, key = { it.session.id }) { item ->
                HistoryCard(item, query) { nav.session(item.session.id) }
            }
        }
    }

    if (pickDay) {
        DatePickDialog(
            initial = day ?: today,
            title = "Scegli il giorno",
            onDismiss = { pickDay = false; if (day == null) period = Period.ALL },
            onPick = { day = it; pickDay = false },
        )
    }
}

@Composable
private fun HistoryCard(item: HistoryItem, query: String, onClick: () -> Unit) {
    val s = item.session
    AppCard(onClick = onClick, color = Pal.Surface) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Fmt.relativeDay(LocalDate.ofEpochDay(s.epochDay)), color = Pal.Muted, fontSize = 12.sp)
                Text(s.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = Pal.Muted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStat(Icons.Rounded.Schedule, Fmt.minutes((s.durationMs / 60000).toInt()))
            MiniStat(Icons.Rounded.FitnessCenter, "${item.exerciseNames.size} es.")
            MiniStat(Icons.Rounded.Layers, "${item.sets} serie")
            MiniStat(Icons.Rounded.Scale, Fmt.volume(item.volume))
        }
        val names = if (query.isBlank()) item.exerciseNames else item.exerciseNames.sortedByDescending { it.contains(query, true) }
        Text(names.joinToString(" · "), color = Pal.TextSoft, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MiniStat(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(Pal.SurfaceLow).padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Pal.Accent, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

