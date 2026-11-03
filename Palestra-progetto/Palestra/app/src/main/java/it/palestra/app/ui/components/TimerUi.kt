package it.palestra.app.ui.components

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.palestra.app.timer.RestTimerController
import it.palestra.app.timer.TimerState
import it.palestra.app.ui.Fmt
import it.palestra.app.ui.theme.LabelCaps
import it.palestra.app.ui.theme.Pal
import kotlinx.coroutines.delay

/** Orologio che si aggiorna 4 volte al secondo mentre il timer è attivo. */
@Composable
fun rememberTimerNow(state: TimerState): Long {
    val now by produceState(SystemClock.elapsedRealtime(), state.active, state.paused, state.endAtElapsed) {
        while (state.active && !state.paused) {
            value = SystemClock.elapsedRealtime()
            delay(250)
        }
        value = SystemClock.elapsedRealtime()
    }
    return now
}

/** Barra compatta del recupero, visibile in ogni schermata quando il timer è attivo. */
@Composable
fun TimerBar(timer: RestTimerController, modifier: Modifier = Modifier) {
    val state by timer.state.collectAsStateWithLifecycle()
    if (!state.active) return
    var showSheet by remember { mutableStateOf(false) }
    val now = rememberTimerNow(state)
    val remaining = state.remainingMs(now)
    val progress = if (state.totalSec > 0) (remaining / (state.totalSec * 1000f)).coerceIn(0f, 1f) else 0f

    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Pal.Secondary)
            .clickable { showSheet = true }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Pal.Accent).clickable { timer.togglePause() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (state.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                if (state.paused) "Riprendi" else "Pausa",
                tint = Pal.OnAccent,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text(if (state.paused) "Recupero in pausa" else "Recupero", color = Pal.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("${Fmt.clockMs(remaining)} / ${Fmt.clock(state.totalSec)}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = Pal.Accent,
                trackColor = Pal.SurfaceLow,
                drawStopIndicator = {},
            )
        }
        Text(
            "Salta",
            color = Pal.Accent,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { timer.skip() }.padding(8.dp),
        )
    }

    if (showSheet) {
        TimerSheet(timer = timer, onDismiss = { showSheet = false })
    }
}

@Composable
fun TimerSheet(timer: RestTimerController, onDismiss: () -> Unit) {
    val state by timer.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(state.active) { if (!state.active) onDismiss() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Pal.Background) {
        TimerPanel(timer, state)
    }
}

@Composable
private fun TimerPanel(timer: RestTimerController, state: TimerState) {
    val now = rememberTimerNow(state)
    val remaining = state.remainingMs(now)
    val progress = if (state.totalSec > 0) (remaining / (state.totalSec * 1000f)).coerceIn(0f, 1f) else 0f
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(state.label.ifBlank { "Recupero" }, color = Pal.Muted, fontSize = 14.sp)
        Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 14.dp.toPx()
                val d = size.minDimension - stroke
                val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
                drawArc(Pal.Secondary, 0f, 360f, false, topLeft, Size(d, d), style = Stroke(stroke))
                drawArc(Pal.Accent, -90f, 360f * progress, false, topLeft, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Fmt.clockMs(remaining), fontSize = 60.sp, fontWeight = FontWeight.ExtraBold)
                Text("di ${Fmt.clock(state.totalSec)}", color = Pal.Muted, fontSize = 14.sp)
                Text(
                    if (state.paused) "IN PAUSA" else "IN CORSO",
                    style = LabelCaps,
                    color = if (state.paused) Pal.Muted else Pal.Accent,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            BigTextButton("−15 s", Modifier.weight(1f)) { timer.addSeconds(-15) }
            Box(
                Modifier.weight(1f).height(72.dp).clip(RoundedCornerShape(20.dp)).background(Pal.Accent).clickable { timer.togglePause() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (state.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    if (state.paused) "Riprendi" else "Pausa",
                    tint = Pal.OnAccent,
                    modifier = Modifier.size(34.dp),
                )
            }
            BigTextButton("+15 s", Modifier.weight(1f)) { timer.addSeconds(15) }
        }
        SecondaryButton("Salta recupero", onClick = { timer.skip() }, icon = Icons.Rounded.SkipNext, modifier = Modifier.fillMaxWidth())
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Ricomincia con")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(60, 90, 120, 180).forEach { sec ->
                    SelectChip("${sec}s", selected = sec == state.totalSec, onClick = { timer.start(sec, state.label) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun BigTextButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(56.dp).clip(RoundedCornerShape(16.dp)).background(Pal.Secondary).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
}
