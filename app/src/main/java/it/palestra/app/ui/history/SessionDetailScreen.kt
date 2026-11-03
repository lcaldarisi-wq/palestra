package it.palestra.app.ui.history

import it.palestra.app.data.db.durationMs
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import it.palestra.app.AppContainer
import it.palestra.app.data.SessionDetail
import it.palestra.app.data.Stats
import it.palestra.app.data.db.ExerciseType
import it.palestra.app.data.db.SetEntity
import it.palestra.app.data.db.SetRow
import it.palestra.app.ui.Fmt
import it.palestra.app.ui.Nav
import it.palestra.app.ui.calendar.DatePickDialog
import it.palestra.app.ui.components.*
import it.palestra.app.ui.theme.Pal
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DetailUi(val loaded: Boolean = false, val detail: SessionDetail? = null, val rows: List<SetRow> = emptyList())

class SessionDetailViewModel(private val c: AppContainer, private val sessionId: Long) : ViewModel() {
    val state = combine(c.sessions.observeDetail(sessionId), c.sessions.setRows) { d, r -> DetailUi(true, d, r) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DetailUi())

    fun repeat(onStarted: (Long) -> Unit) = viewModelScope.launch {
        onStarted(c.sessions.getActive()?.id ?: c.sessions.repeatSession(sessionId))
    }

    fun changeDate(date: LocalDate) = viewModelScope.launch { c.sessions.changeDate(sessionId, date) }
    fun delete(onDone: () -> Unit) = viewModelScope.launch { c.sessions.delete(sessionId); onDone() }
}

@Composable
fun SessionDetailScreen(sessionId: Long, nav: Nav) {
    val vm = appViewModel { SessionDetailViewModel(it, sessionId) }
    val ui by vm.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    val detail = ui.detail
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = detail?.session?.name ?: "Allenamento",
            subtitle = detail?.let { Fmt.dayLong(LocalDate.ofEpochDay(it.session.epochDay)) },
            onBack = { nav.back() },
        ) {
            if (detail != null) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Altre azioni", tint = Pal.Muted) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Pal.Secondary) {
                        DropdownMenuItem(text = { Text("Cambia data") }, leadingIcon = { Icon(Icons.Rounded.Event, null) }, onClick = { menu = false; pickDate = true })
                        DropdownMenuItem(text = { Text("Elimina", color = Pal.Failed) }, leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = Pal.Failed) }, onClick = { menu = false; confirmDelete = true })
                    }
                }
            }
        }
        if (detail == null) {
            if (ui.loaded) EmptyState(Icons.Rounded.History, "Sessione non trovata", "Potrebbe essere stata eliminata.")
            return@Column
        }
        val s = detail.session
        val sessionRows = ui.rows.filter { it.sessionId == s.id }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Durata", Fmt.minutes((s.durationMs / 60000).toInt()), Modifier.weight(1f))
                StatTile("Volume", Fmt.volume(Stats.volume(sessionRows)), Modifier.weight(1f))
                StatTile("Serie", "${Stats.working(sessionRows).size}", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Modifica", onClick = { nav.workout(s.id) }, icon = Icons.Rounded.Edit, modifier = Modifier.weight(1f))
                SecondaryButton("Ripeti oggi", onClick = { vm.repeat { nav.workout(it) } }, icon = Icons.Rounded.Replay, modifier = Modifier.weight(1f))
            }

            detail.blocks.forEach { block ->
                val ex = block.exercise
                val type = ex?.type ?: ExerciseType.WEIGHT
                val exRows = ui.rows.filter { it.exerciseId == block.sessionExercise.exerciseId }
                val thisVol = Stats.volume(exRows.filter { it.sessionId == s.id })
                val prevSession = exRows.filter { it.startedAt < s.startedAt }.maxByOrNull { it.startedAt }?.sessionId
                val prevVol = prevSession?.let { pid -> Stats.volume(exRows.filter { it.sessionId == pid }) }
                val bestBefore = exRows.filter { it.startedAt < s.startedAt && !it.isWarmup && it.reps > 0 }.maxOfOrNull { it.weight }

                AppCard(color = Pal.Surface) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ExerciseAvatar(block.name, 36)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(block.name, fontWeight = FontWeight.Bold)
                            if (!type.isTimed && prevVol != null && prevVol > 0) {
                                val diff = thisVol - prevVol
                                Text(
                                    "Volume ${Fmt.volume(thisVol)} · ${if (diff >= 0) "+" else "−"}${Fmt.volume(kotlin.math.abs(diff))} vs precedente",
                                    color = if (diff > 0) Pal.Accent else Pal.Muted, fontSize = 12.sp,
                                )
                            } else if (!type.isTimed) {
                                Text("Volume ${Fmt.volume(thisVol)}", color = Pal.Muted, fontSize = 12.sp)
                            }
                        }
                    }
                    var n = 0
                    block.sets.forEach { set ->
                        val label = if (set.isWarmup) "R" else { n++; "$n" }
                        val isPr = !set.isWarmup && set.reps > 0 && bestBefore != null && set.weight > bestBefore
                        SetLine(label, set, type, isPr)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (pickDate && detail != null) {
        DatePickDialog(
            initial = LocalDate.ofEpochDay(detail.session.epochDay),
            title = "Data dell'allenamento",
            onDismiss = { pickDate = false },
            onPick = { vm.changeDate(it); pickDate = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Eliminare l'allenamento?",
            text = "La sessione e tutte le sue serie verranno eliminate definitivamente.",
            confirm = "Elimina",
            destructive = true,
            onConfirm = { vm.delete { nav.back() } },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun SetLine(label: String, set: SetEntity, type: ExerciseType, isPr: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val (bg, fg, border) = when {
            set.isWarmup -> Triple(Color.Transparent, Pal.Accent, Pal.Accent)
            set.isFailed -> Triple(Pal.Failed, Pal.OnFailed, Color.Transparent)
            else -> Triple(Pal.Secondary, Pal.Text, Color.Transparent)
        }
        Box(
            Modifier.size(width = 36.dp, height = 28.dp).clip(RoundedCornerShape(8.dp)).background(bg).border(1.5.dp, border, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) { Text(label, color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp) }
        val text = when (type) {
            ExerciseType.TIME -> Fmt.clock(set.durationSec)
            ExerciseType.CARDIO -> "${set.durationSec / 60} min" + if (set.distanceKm > 0) " · ${Fmt.num(set.distanceKm)} km" else ""
            else -> "${Fmt.kg(set.weight)} × ${set.reps}"
        }
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (set.isFailed) Text("fallita", color = Pal.Failed, fontSize = 12.sp)
        if (isPr) {
            Icon(Icons.Rounded.EmojiEvents, "Record", tint = Pal.Accent, modifier = Modifier.size(18.dp))
        }
    }
}
