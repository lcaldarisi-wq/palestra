package it.palestra.app.ui.components

import it.palestra.app.data.db.belongsTo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.palestra.app.data.db.ExerciseEntity
import it.palestra.app.data.db.ExerciseType
import it.palestra.app.data.db.MuscleGroup
import it.palestra.app.ui.theme.Pal
import kotlinx.coroutines.launch

fun initials(name: String): String {
    val words = name.split(' ', '-', '(').filter { it.isNotBlank() && it[0].isLetter() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2).uppercase()
        else -> "${words[0][0]}${words.last()[0]}".uppercase()
    }
}

fun matches(e: ExerciseEntity, query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim().lowercase()
    return e.name.lowercase().contains(q) || e.equipment.lowercase().contains(q) || e.muscleGroup.label.lowercase().contains(q)
}

@Composable
fun ExerciseAvatar(name: String, size: Int = 40) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)).background(Pal.Secondary),
        contentAlignment = Alignment.Center,
    ) { Text(initials(name), color = Pal.Accent, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp) }
}

/** Scelta (multipla) di esercizi dal catalogo, con ricerca, filtro per gruppo e creazione rapida. */
@Composable
fun ExercisePickerDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (List<Long>) -> Unit,
) {
    val container = appContainer()
    val all by container.exercises.all.collectAsStateWithLifecycle(initialValue = emptyList())
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<MuscleGroup?>(null) }
    val selected = remember { mutableStateListOf<Long>() }
    var creating by remember { mutableStateOf(false) }

    val visible = all.filter { !it.archived && (group == null || it.belongsTo(group!!)) && matches(it, query) }
        .sortedWith(compareBy<ExerciseEntity>({ it.muscleGroup.ordinal }, { it.name.lowercase() }))

    // La finestra resta dentro le barre di sistema: il pulsante di conferma non finisce mai sotto la barra di navigazione.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {
        Column(
            Modifier.fillMaxSize().background(Pal.Background).navigationBarsPadding().imePadding(),
        ) {
            ScreenHeader(title = title, subtitle = "${selected.size} selezionati", onBack = onDismiss) {
                RoundIconButton(Icons.Rounded.Add, "Crea esercizio", onClick = { creating = true })
                if (selected.isNotEmpty()) {
                    Button(
                        onClick = { onConfirm(selected.toList()) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Pal.Accent, contentColor = Pal.OnAccent),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        modifier = Modifier.height(44.dp),
                    ) { Text("Aggiungi (${selected.size})", fontWeight = FontWeight.Bold) }
                }
            }
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SearchField(query, { query = it }, "Cerca esercizio o attrezzo")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { SelectChip("Tutti", group == null, { group = null }) }
                    items(MuscleGroup.entries) { g -> SelectChip(g.label, group == g, { group = g }) }
                }
            }
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                var lastGroup: MuscleGroup? = null
                visible.forEach { e ->
                    if (group == null && e.muscleGroup != lastGroup) {
                        lastGroup = e.muscleGroup
                        item(key = "h_${e.muscleGroup.name}") {
                            SectionLabel(e.muscleGroup.label, Modifier.padding(top = 10.dp, bottom = 2.dp))
                        }
                    }
                    item(key = e.id) {
                        val isSel = e.id in selected
                        ExerciseRow(e, isSel) { if (isSel) selected.remove(e.id) else selected.add(e.id) }
                    }
                }
                if (visible.isEmpty()) {
                    item {
                        EmptyState(Icons.Rounded.SearchOff, "Nessun esercizio trovato", "Puoi crearlo con il pulsante + in alto.")
                    }
                }
            }
            Box(Modifier.background(Pal.Background).padding(16.dp)) {
                PrimaryButton(
                    text = when (selected.size) {
                        0 -> "Seleziona almeno un esercizio"
                        1 -> "Aggiungi 1 esercizio"
                        else -> "Aggiungi ${selected.size} esercizi"
                    },
                    onClick = { onConfirm(selected.toList()) },
                    enabled = selected.isNotEmpty(),
                )
            }
        }
    }

    if (creating) {
        ExerciseEditDialog(
            initial = null,
            initialGroup = group,
            onDismiss = { creating = false },
            onSaved = { id -> selected.add(id); creating = false },
        )
    }
}

@Composable
fun ExerciseRow(e: ExerciseEntity, selected: Boolean, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Pal.Accent.copy(alpha = 0.08f) else Pal.Surface)
            .border(1.dp, if (selected) Pal.Accent else Pal.Secondary, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ExerciseAvatar(e.name)
        Column(Modifier.weight(1f)) {
            Text(e.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val extra = listOfNotNull(e.equipment.takeIf { it.isNotBlank() }, e.type.takeIf { it.isTimed }?.label).joinToString(" · ")
            Text(extra, color = Pal.Muted, fontSize = 12.sp, maxLines = 1)
        }
        if (trailing != null) {
            trailing()
        } else {
            Box(
                Modifier.size(28.dp).clip(CircleShape)
                    .then(if (selected) Modifier.background(Pal.Accent) else Modifier.border(1.5.dp, Pal.Outline, CircleShape)),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Rounded.Check, null, tint = Pal.OnAccent, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Crea o modifica un esercizio (nome, gruppo, attrezzo, tipo, indicazioni). */
@Composable
fun ExerciseEditDialog(
    initial: ExerciseEntity?,
    initialGroup: MuscleGroup? = null,
    onDismiss: () -> Unit,
    onSaved: (Long) -> Unit,
    onToggleArchive: (() -> Unit)? = null,
) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var group by remember { mutableStateOf(initial?.muscleGroup ?: initialGroup ?: MuscleGroup.PETTO) }
    var equipment by remember { mutableStateOf(initial?.equipment ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: ExerciseType.WEIGHT) }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pal.Secondary,
        title = { Text(if (initial == null) "Nuovo esercizio" else "Modifica esercizio") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTextField(name, { name = it; error = null }, "Nome")
                if (error != null) Text(error!!, color = Pal.Failed, fontSize = 13.sp)
                SectionLabel("Gruppo muscolare")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MuscleGroup.entries.forEach { g -> SelectChip(g.label, g == group, { group = g }) }
                }
                SectionLabel("Tipo")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExerciseType.entries.forEach { t -> SelectChip(t.label, t == type, { type = t }) }
                }
                AppTextField(equipment, { equipment = it }, "Attrezzo", placeholder = "es. Manubri, Cavi, Macchina")
                AppTextField(notes, { notes = it }, "Indicazioni di esecuzione", singleLine = false)
                if (onToggleArchive != null && initial != null) {
                    TextButton(onClick = onToggleArchive) {
                        Icon(if (initial.archived) Icons.Rounded.Unarchive else Icons.Rounded.Archive, null, tint = Pal.Muted)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (initial.archived) "Ripristina esercizio" else "Archivia (nascondi dalla scelta)",
                            color = Pal.Muted,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = name.trim()
                if (n.isEmpty()) {
                    error = "Inserisci un nome"; return@TextButton
                }
                scope.launch {
                    if (container.exercises.nameTaken(n, initial?.id ?: 0L)) {
                        error = "Esiste già un esercizio con questo nome"
                    } else {
                        val base = initial ?: ExerciseEntity(name = n, muscleGroup = group, equipment = equipment, type = type)
                        val id = container.exercises.save(base.copy(name = n, muscleGroup = group, equipment = equipment.trim(), type = type, notes = notes.trim()))
                        onSaved(id)
                    }
                }
            }) { Text("Salva", color = Pal.Accent, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla", color = Pal.TextSoft) } },
    )
}
