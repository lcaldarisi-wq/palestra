package it.palestra.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.palestra.app.data.ExerciseBlock
import it.palestra.app.data.PreviousPerformance
import it.palestra.app.data.db.ExerciseType
import it.palestra.app.data.db.SetEntity
import it.palestra.app.ui.Fmt
import it.palestra.app.ui.Nav
import it.palestra.app.ui.components.*
import it.palestra.app.ui.theme.LabelCaps
import it.palestra.app.ui.theme.Pal
import kotlinx.coroutines.delay

@Composable
fun WorkoutScreen(sessionId: Long, nav: Nav) {
    val vm = appViewModel { WorkoutViewModel(it, sessionId) }
    val ui by vm.state.collectAsStateWithLifecycle()
    val container = appContainer()
    val settings by container.settings.state.collectAsStateWithLifecycle()

    var picking by remember { mutableStateOf(false) }
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    // Schermo sempre acceso durante l'allenamento
    val view = LocalView.current
    val keepOn = ui.inProgress && settings.keepScreenOn
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }

    val detail = ui.detail
    if (detail == null) {
        if (ui.loaded) {
            Column(Modifier.fillMaxSize()) {
                ScreenHeader("Allenamento", onBack = { nav.back() })
                EmptyState(Icons.Rounded.FitnessCenter, "Sessione non disponibile", "Questo allenamento è stato concluso o eliminato.")
            }
        }
        return
    }
    val session = detail.session

    Column(Modifier.fillMaxSize().imePadding()) {
        // --- intestazione ---
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RoundIconButton(Icons.Rounded.KeyboardArrowDown, "Riduci", onClick = { nav.back() })
            Column(Modifier.weight(1f)) {
                Text(session.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (ui.inProgress) {
                    val elapsed by produceState(System.currentTimeMillis() - session.startedAt, session.startedAt) {
                        while (true) {
                            value = System.currentTimeMillis() - session.startedAt
                            delay(1000)
                        }
                    }
                    Text("In corso · ${Fmt.elapsed(elapsed)}", color = Pal.Muted, fontSize = 13.sp)
                } else {
                    Text("Modifica sessione · ${Fmt.relativeDay(java.time.LocalDate.ofEpochDay(session.epochDay))}", color = Pal.Muted, fontSize = 13.sp)
                }
            }
            if (ui.inProgress) {
                OutlinedButton(
                    onClick = { confirmFinish = true },
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Pal.Accent),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    modifier = Modifier.height(40.dp),
                ) { Text("Termina", color = Pal.Accent, fontWeight = FontWeight.Bold) }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Altre azioni", tint = Pal.Muted) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Pal.Secondary) {
                        DropdownMenuItem(
                            text = { Text("Annulla allenamento", color = Pal.Failed) },
                            leadingIcon = { Icon(Icons.Rounded.DeleteForever, null, tint = Pal.Failed) },
                            onClick = { menu = false; confirmDiscard = true },
                        )
                    }
                }
            } else {
                Button(
                    onClick = { vm.finish { nav.back() } },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Pal.Accent, contentColor = Pal.OnAccent),
                    modifier = Modifier.height(40.dp),
                ) { Text("Fatto", fontWeight = FontWeight.Bold) }
            }
        }

        // --- avanzamento ---
        val total = ui.totalSets
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text(
                    if (ui.blocks.isEmpty()) "Nessun esercizio" else "Esercizio ${ui.current + 1} di ${ui.blocks.size}",
                    color = Pal.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f),
                )
                Text("${ui.doneSets} di $total serie", color = Pal.Muted, fontSize = 12.sp)
            }
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else ui.doneSets / total.toFloat() },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = Pal.Accent,
                trackColor = Pal.Secondary,
                drawStopIndicator = {},
            )
        }

        // --- selettore esercizi ---
        val listState = rememberLazyListState()
        LaunchedEffect(ui.current, ui.blocks.size) {
            if (ui.blocks.isNotEmpty()) listState.animateScrollToItem(ui.current.coerceIn(0, ui.blocks.size - 1))
        }
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(ui.blocks, key = { _, b -> b.sessionExercise.id }) { i, b ->
                ExerciseTab(i, b, selected = i == ui.current) { vm.setCurrent(i) }
            }
            item {
                Box(
                    Modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, Pal.Outline, RoundedCornerShape(12.dp))
                        .clickable { picking = true }.padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Add, null, tint = Pal.Accent, modifier = Modifier.size(18.dp))
                        Text("Esercizio", color = Pal.Accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // --- esercizio corrente ---
        val block = ui.currentBlock
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (block == null) {
                EmptyState(Icons.Rounded.FitnessCenter, "Aggiungi un esercizio", "Scegli gli esercizi dal catalogo per iniziare a registrare.") {
                    PrimaryButton("Aggiungi esercizi", onClick = { picking = true }, icon = Icons.Rounded.Add)
                }
            } else {
                CurrentExercise(
                    block = block,
                    blocks = ui.blocks,
                    previous = ui.previous[block.sessionExercise.exerciseId],
                    best = ui.bestWeight[block.sessionExercise.exerciseId],
                    vm = vm,
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        // --- barra inferiore: timer + prossimo ---
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TimerBar(container.timer)
            if (block != null) {
                val next = ui.blocks.getOrNull(ui.current + 1)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (ui.current > 0) {
                        Box(
                            Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Pal.Secondary).clickable { vm.setCurrent(ui.current - 1) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.ChevronLeft, "Esercizio precedente", tint = Pal.Text) }
                    }
                    when {
                        next != null -> PrimaryButton("Prossimo: ${next.name}", onClick = { vm.setCurrent(ui.current + 1) }, icon = Icons.AutoMirrored.Rounded.ArrowForward, modifier = Modifier.weight(1f))
                        ui.inProgress -> PrimaryButton("Termina allenamento", onClick = { confirmFinish = true }, icon = Icons.Rounded.Flag, modifier = Modifier.weight(1f))
                        else -> PrimaryButton("Salva modifiche", onClick = { vm.finish { nav.back() } }, icon = Icons.Rounded.Check, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (picking) {
        ExercisePickerDialog(
            title = "Aggiungi all'allenamento",
            onDismiss = { picking = false },
            onConfirm = { ids -> vm.addExercises(ids); picking = false },
        )
    }

    if (confirmFinish) {
        val pending = ui.totalSets - ui.doneSets
        ConfirmDialog(
            title = "Terminare l'allenamento?",
            text = buildString {
                append("Hai completato ${ui.doneSets} serie in ${Fmt.elapsed(System.currentTimeMillis() - session.startedAt)}.")
                if (pending > 0) append(" Le $pending serie non completate verranno scartate.")
                if (ui.doneSets == 0) append(" Non avendo serie completate, l'allenamento non verrà salvato.")
            },
            confirm = "Termina e salva",
            onConfirm = { vm.finish { saved -> if (saved) nav.replaceWorkoutWithSession(sessionId) else nav.back() } },
            onDismiss = { confirmFinish = false },
        )
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Annullare l'allenamento?",
            text = "Tutte le serie registrate in questa sessione verranno eliminate.",
            confirm = "Elimina",
            destructive = true,
            onConfirm = { vm.discard { nav.back() } },
            onDismiss = { confirmDiscard = false },
        )
    }
}

@Composable
private fun ExerciseTab(index: Int, block: ExerciseBlock, selected: Boolean, onClick: () -> Unit) {
    val done = block.sets.isNotEmpty() && block.sets.all { it.completed }
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .height(40.dp)
            .widthIn(max = 200.dp)
            .clip(shape)
            .background(if (selected) Pal.Accent else Pal.Secondary)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val fg = if (selected) Pal.OnAccent else Pal.Text
        if (done) Icon(Icons.Rounded.CheckCircle, null, tint = if (selected) Pal.OnAccent else Pal.Accent, modifier = Modifier.size(16.dp))
        else Text("${index + 1}", color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        Text(block.name, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CurrentExercise(
    block: ExerciseBlock,
    blocks: List<ExerciseBlock>,
    previous: PreviousPerformance?,
    best: Double?,
    vm: WorkoutViewModel,
) {
    val ex = block.exercise
    val type = ex?.type ?: ExerciseType.WEIGHT
    var showOptions by remember(block.sessionExercise.id) { mutableStateOf(false) }

    AppCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(block.name, style = MaterialTheme.typography.headlineSmall)
                val sub = listOfNotNull(ex?.muscleGroup?.label, ex?.equipment?.takeIf { it.isNotBlank() }, "Recupero ${Fmt.rest(block.sessionExercise.restSec)}")
                Text(sub.joinToString(" · "), color = Pal.Muted, fontSize = 13.sp)
            }
            if (best != null && best > 0) Pill("PR ${Fmt.kg(best)}", filled = false)
        }
        val group = block.sessionExercise.supersetGroup
        if (group > 0) {
            val others = blocks.filter { it.sessionExercise.supersetGroup == group && it.sessionExercise.id != block.sessionExercise.id }
            if (others.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Link, null, tint = Pal.Accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Superserie con ${others.joinToString(", ") { it.name }}", color = Pal.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        val notes = listOf(ex?.notes.orEmpty(), block.sessionExercise.notes).filter { it.isNotBlank() }.joinToString("\n")
        if (notes.isNotBlank()) {
            Text(notes, color = Pal.TextSoft, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Pal.SurfaceLow).padding(10.dp))
        }
        if (previous != null) {
            val work = previous.sets.filter { !it.isWarmup }
            Text(
                "Ultima volta (${Fmt.relativeDay(previous.date)}): " + work.joinToString(" · ") { setShort(it, type) },
                color = Pal.Muted, fontSize = 13.sp,
            )
        } else {
            Text("Prima volta con questo esercizio", color = Pal.Muted, fontSize = 13.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallAction(if (showOptions) "Chiudi opzioni" else "Opzioni", Icons.Rounded.Tune) { showOptions = !showOptions }
            SmallAction("Avvia recupero", Icons.Rounded.Timer) { vm.startTimer(block) }
        }
        if (showOptions) {
            SectionLabel("Recupero per questo esercizio")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(45, 60, 90, 120, 150, 180, 240).forEach { sec ->
                    SelectChip(Fmt.rest(sec), block.sessionExercise.restSec == sec, { vm.setRest(block, sec) })
                }
            }
            ToggleRow("Timer automatico", block.sessionExercise.autoTimer, { vm.setAutoTimer(block, it) })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallAction("Sposta prima", Icons.Rounded.ArrowUpward) { vm.moveExercise(block, -1) }
                SmallAction("Sposta dopo", Icons.Rounded.ArrowDownward) { vm.moveExercise(block, 1) }
            }
            SmallAction("Rimuovi esercizio", Icons.Rounded.Delete, color = Pal.Failed) { vm.removeExercise(block) }
        }
    }

    // intestazione tabella
    val timed = type.isTimed
    Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("SERIE", style = LabelCaps, modifier = Modifier.width(48.dp))
        Text("PRECEDENTE", style = LabelCaps, modifier = Modifier.weight(1f))
        when (type) {
            ExerciseType.TIME -> Text("SEC", style = LabelCaps, modifier = Modifier.width(76.dp), textAlign = TextAlign.Center)
            ExerciseType.CARDIO -> {
                Text("MIN", style = LabelCaps, modifier = Modifier.width(64.dp), textAlign = TextAlign.Center)
                Text("KM", style = LabelCaps, modifier = Modifier.width(64.dp), textAlign = TextAlign.Center)
            }
            else -> {
                Text(if (type == ExerciseType.BODYWEIGHT) "+KG" else "KG", style = LabelCaps, modifier = Modifier.width(76.dp), textAlign = TextAlign.Center)
                Text("RIP", style = LabelCaps, modifier = Modifier.width(56.dp), textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.width(44.dp))
    }

    val prevWork = previous?.sets?.filter { !it.isWarmup }.orEmpty()
    val prevWarm = previous?.sets?.filter { it.isWarmup }.orEmpty()
    var workIndex = 0
    var warmIndex = 0
    block.sets.forEach { set ->
        val label: String
        val prev: SetEntity?
        if (set.isWarmup) {
            label = "R"; prev = prevWarm.getOrNull(warmIndex); warmIndex++
        } else {
            workIndex++; label = "$workIndex"; prev = prevWork.getOrNull(workIndex - 1)
        }
        val isPr = set.completed && !set.isWarmup && set.reps > 0 && best != null && set.weight > best
        key(set.id) {
            SetRowUi(
                set = set,
                label = label,
                prevText = prev?.let { setShort(it, type) } ?: "–",
                type = type,
                isPr = isPr,
                onUpdate = vm::updateSet,
                onToggle = { vm.toggleComplete(block, set) },
                onWarmup = { vm.toggleWarmup(set) },
                onFailed = { vm.toggleFailed(block, set) },
                onDelete = { vm.deleteSet(set) },
            )
        }
    }
    if (!timed) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            LegendTag("R", outlined = true); Text("Riscaldamento", color = Pal.Muted, fontSize = 12.sp)
            LegendTag("F", outlined = false); Text("Fallita", color = Pal.Muted, fontSize = 12.sp)
            Text("· tocca il numero", color = Pal.Dim, fontSize = 12.sp)
        }
    }
    Box(
        Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, Pal.Outline, RoundedCornerShape(12.dp))
            .clickable { vm.addSet(block) },
        contentAlignment = Alignment.Center,
    ) { Text("+ Aggiungi serie", color = Pal.TextSoft, fontWeight = FontWeight.SemiBold) }
}

private fun setShort(s: SetEntity, type: ExerciseType): String = when (type) {
    ExerciseType.TIME -> Fmt.clock(s.durationSec)
    ExerciseType.CARDIO -> buildString {
        append("${s.durationSec / 60} min")
        if (s.distanceKm > 0) append(" ${Fmt.num(s.distanceKm)} km")
    }
    else -> "${Fmt.num(s.weight)}×${s.reps}" + if (s.isFailed) "F" else ""
}

@Composable
private fun SmallAction(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color = Pal.TextSoft, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(Pal.SurfaceLow).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LegendTag(t: String, outlined: Boolean) {
    Box(
        Modifier.size(18.dp).clip(RoundedCornerShape(5.dp))
            .then(if (outlined) Modifier.border(1.5.dp, Pal.Accent, RoundedCornerShape(5.dp)) else Modifier.background(Pal.Failed)),
        contentAlignment = Alignment.Center,
    ) { Text(t, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = if (outlined) Pal.Accent else Pal.OnFailed) }
}

@Composable
private fun SetRowUi(
    set: SetEntity,
    label: String,
    prevText: String,
    type: ExerciseType,
    isPr: Boolean,
    onUpdate: (SetEntity) -> Unit,
    onToggle: () -> Unit,
    onWarmup: () -> Unit,
    onFailed: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val rowBg = when {
        set.completed && set.isFailed -> Pal.Failed.copy(alpha = 0.10f)
        set.completed -> Pal.Accent.copy(alpha = 0.07f)
        else -> Color.Transparent
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(rowBg).padding(horizontal = 4.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // etichetta serie con menu tipo
        Box {
            val (bg, fg, border) = when {
                set.isWarmup -> Triple(Color.Transparent, Pal.Accent, Pal.Accent)
                set.isFailed -> Triple(Pal.Failed, Pal.OnFailed, Color.Transparent)
                else -> Triple(Pal.Secondary, Pal.Text, Color.Transparent)
            }
            Box(
                Modifier.size(width = 48.dp, height = 38.dp).clip(RoundedCornerShape(10.dp)).background(bg)
                    .border(1.5.dp, border, RoundedCornerShape(10.dp)).clickable { menu = true },
                contentAlignment = Alignment.Center,
            ) { Text(if (set.isFailed && !set.isWarmup) "$label·F" else label, color = fg, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Pal.Secondary) {
                DropdownMenuItem(
                    text = { Text("Serie di riscaldamento") },
                    leadingIcon = { Icon(if (set.isWarmup) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank, null, tint = Pal.Accent) },
                    onClick = { menu = false; onWarmup() },
                )
                DropdownMenuItem(
                    text = { Text("Serie fallita") },
                    leadingIcon = { Icon(if (set.isFailed) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank, null, tint = Pal.Failed) },
                    onClick = { menu = false; onFailed() },
                )
                DropdownMenuItem(
                    text = { Text("Elimina serie", color = Pal.Failed) },
                    leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = Pal.Failed) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
        Column(Modifier.weight(1f)) {
            if (isPr) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.EmojiEvents, null, tint = Pal.Accent, modifier = Modifier.size(14.dp))
                    Text(" Record!", color = Pal.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(prevText, color = Pal.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when (type) {
            ExerciseType.TIME -> NumberBox(set.durationSec.toDouble(), { onUpdate(set.copy(durationSec = it.toInt())) }, Modifier.width(76.dp), decimal = false)
            ExerciseType.CARDIO -> {
                NumberBox(set.durationSec / 60.0, { onUpdate(set.copy(durationSec = (it * 60).toInt())) }, Modifier.width(64.dp))
                NumberBox(set.distanceKm, { onUpdate(set.copy(distanceKm = it)) }, Modifier.width(64.dp))
            }
            else -> {
                NumberBox(set.weight, { onUpdate(set.copy(weight = it)) }, Modifier.width(76.dp), highlight = isPr)
                NumberBox(set.reps.toDouble(), { onUpdate(set.copy(reps = it.toInt())) }, Modifier.width(56.dp), decimal = false)
            }
        }
        val checkBg = when {
            !set.completed -> Color.Transparent
            set.isFailed -> Pal.Failed
            else -> Pal.Accent
        }
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(checkBg)
                .border(1.5.dp, if (set.completed) Color.Transparent else Pal.Outline, RoundedCornerShape(12.dp))
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Check, if (set.completed) "Serie completata" else "Segna serie completata", tint = if (set.completed) Pal.OnAccent else Pal.Outline)
        }
    }
}
