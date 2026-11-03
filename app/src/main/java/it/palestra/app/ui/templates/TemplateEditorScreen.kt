package it.palestra.app.ui.templates

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import it.palestra.app.data.db.ExerciseEntity
import it.palestra.app.data.db.TemplateEntity
import it.palestra.app.data.db.TemplateExerciseEntity
import it.palestra.app.ui.Fmt
import it.palestra.app.ui.Nav
import it.palestra.app.ui.components.*
import it.palestra.app.ui.theme.Pal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditItem(
    val key: Long,
    val exercise: ExerciseEntity,
    val sets: Int,
    val reps: Int,
    val weight: Double,
    val durationSec: Int,
    val restSec: Int,
    val warmupSets: Int,
    val autoTimer: Boolean,
    /** collegato all'esercizio successivo in superserie */
    val linkNext: Boolean,
    val notes: String,
)

data class EditorState(
    val loaded: Boolean = false,
    val id: Long = 0,
    val name: String = "",
    val days: Set<Int> = emptySet(),
    val items: List<EditItem> = emptyList(),
    val dirty: Boolean = false,
    val expandedKey: Long? = null,
)

class TemplateEditorViewModel(private val c: AppContainer, private val templateId: Long) : ViewModel() {
    private val _state = MutableStateFlow(EditorState())
    val state = _state.asStateFlow()
    private var nextKey = 1L

    init {
        viewModelScope.launch {
            if (templateId == 0L) {
                _state.value = EditorState(loaded = true, name = "")
                return@launch
            }
            val t = c.templates.get(templateId)
            val rows = c.templates.exercisesOf(templateId)
            val days = c.templates.daysOf(templateId)
            val items = rows.mapIndexedNotNull { i, r ->
                val ex = c.exercises.get(r.exerciseId) ?: return@mapIndexedNotNull null
                val next = rows.getOrNull(i + 1)
                EditItem(
                    key = nextKey++, exercise = ex, sets = r.sets, reps = r.reps, weight = r.weight,
                    durationSec = r.durationSec, restSec = r.restSec, warmupSets = r.warmupSets, autoTimer = r.autoTimer,
                    linkNext = r.supersetGroup != 0 && next != null && next.supersetGroup == r.supersetGroup,
                    notes = r.notes,
                )
            }
            _state.value = EditorState(loaded = true, id = templateId, name = t?.name ?: "", days = days, items = items)
        }
    }

    private fun edit(block: (EditorState) -> EditorState) = _state.update { block(it).copy(dirty = true) }

    fun setName(n: String) = edit { it.copy(name = n) }
    fun toggleDay(d: Int) = edit { it.copy(days = if (d in it.days) it.days - d else it.days + d) }
    fun toggleExpanded(key: Long) = _state.update { it.copy(expandedKey = if (it.expandedKey == key) null else key) }

    fun updateItem(key: Long, f: (EditItem) -> EditItem) = edit { s -> s.copy(items = s.items.map { if (it.key == key) f(it) else it }) }

    fun remove(key: Long) = edit { s -> s.copy(items = s.items.filter { it.key != key }) }

    fun move(key: Long, delta: Int) = edit { s ->
        val list = s.items.toMutableList()
        val i = list.indexOfFirst { it.key == key }
        val j = i + delta
        if (i < 0 || j !in list.indices) s else {
            val item = list.removeAt(i); list.add(j, item); s.copy(items = list)
        }
    }

    fun addExercises(ids: List<Long>) {
        viewModelScope.launch {
            val rest = c.settings.state.value.defaultRestSec
            val newItems = ids.mapNotNull { c.exercises.get(it) }.map { ex ->
                val timed = ex.type.isTimed
                EditItem(
                    key = nextKey++, exercise = ex, sets = 3, reps = if (timed) 0 else 10, weight = 0.0,
                    durationSec = if (timed) 60 else 0, restSec = rest, warmupSets = 0, autoTimer = true, linkNext = false, notes = "",
                )
            }
            edit { s -> s.copy(items = s.items + newItems, expandedKey = newItems.firstOrNull()?.key ?: s.expandedKey) }
        }
    }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (s.name.isBlank()) return
        viewModelScope.launch {
            var group = 0
            var groupId = 0
            val rows = s.items.mapIndexed { i, it ->
                val prevLinked = i > 0 && s.items[i - 1].linkNext
                val g = when {
                    it.linkNext && !prevLinked -> { group += 1; groupId = group; groupId }
                    prevLinked -> groupId
                    else -> 0
                }
                TemplateExerciseEntity(
                    templateId = s.id, exerciseId = it.exercise.id, position = i, sets = it.sets, reps = it.reps,
                    weight = it.weight, durationSec = it.durationSec, restSec = it.restSec, warmupSets = it.warmupSets,
                    supersetGroup = g, autoTimer = it.autoTimer, notes = it.notes,
                )
            }
            val existing = if (s.id != 0L) c.templates.get(s.id) else null
            val tpl = (existing ?: TemplateEntity(name = s.name.trim())).copy(name = s.name.trim())
            val id = c.templates.save(tpl, rows, s.days)
            _state.update { it.copy(id = id, dirty = false) }
            onDone()
        }
    }
}

private val restOptions = listOf(30, 45, 60, 90, 120, 150, 180, 240)

@Composable
fun TemplateEditorScreen(templateId: Long, nav: Nav) {
    val vm = appViewModel { TemplateEditorViewModel(it, templateId) }
    val s by vm.state.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }

    BackHandler(enabled = s.dirty) { confirmExit = true }

    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenHeader(
            title = if (templateId == 0L) "Nuova scheda" else "Modifica scheda",
            onBack = { if (s.dirty) confirmExit = true else nav.back() },
        ) {
            TextButton(onClick = { vm.save { nav.back() } }, enabled = s.name.isNotBlank()) {
                Text("Salva", color = if (s.name.isNotBlank()) Pal.Accent else Pal.Dim, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        if (!s.loaded) return@Column

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                AppTextField(s.name, vm::setName, "Nome scheda", placeholder = "es. Push, Pull, Legs")
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    SectionLabel("Giorni della settimana")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..7).forEach { d ->
                            SelectChip(Fmt.weekLetters[d - 1], d in s.days, { vm.toggleDay(d) }, Modifier.weight(1f))
                        }
                    }
                    Text("Se un giorno era assegnato a un'altra scheda, verrà sostituito.", color = Pal.Dim, fontSize = 12.sp)
                }
            }
            item {
                SectionLabel("Esercizi", Modifier.padding(top = 10.dp), trailing = "${s.items.size}")
            }
            itemsIndexed(s.items, key = { _, it -> it.key }) { i, item ->
                val linkedFromPrev = i > 0 && s.items[i - 1].linkNext
                EditExerciseCard(
                    item = item,
                    index = i,
                    count = s.items.size,
                    expanded = s.expandedKey == item.key,
                    linkedFromPrev = linkedFromPrev,
                    onToggle = { vm.toggleExpanded(item.key) },
                    onChange = { f -> vm.updateItem(item.key, f) },
                    onMove = { d -> vm.move(item.key, d) },
                    onRemove = { vm.remove(item.key) },
                )
            }
            item {
                SecondaryButton("Aggiungi esercizi", onClick = { picking = true }, icon = Icons.Rounded.Add, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
        }
    }

    if (picking) {
        ExercisePickerDialog(
            title = "Aggiungi esercizi",
            onDismiss = { picking = false },
            onConfirm = { ids -> vm.addExercises(ids); picking = false },
        )
    }

    if (confirmExit) {
        ConfirmDialog(
            title = "Uscire senza salvare?",
            text = "Le modifiche alla scheda andranno perse.",
            confirm = "Esci",
            destructive = true,
            onConfirm = { nav.back() },
            onDismiss = { confirmExit = false },
        )
    }
}

@Composable
private fun EditExerciseCard(
    item: EditItem,
    index: Int,
    count: Int,
    expanded: Boolean,
    linkedFromPrev: Boolean,
    onToggle: () -> Unit,
    onChange: ((EditItem) -> EditItem) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    val timed = item.exercise.type.isTimed
    val summary = buildString {
        if (item.warmupSets > 0) append("${item.warmupSets}R + ")
        append("${item.sets} × ")
        append(if (timed) Fmt.clock(item.durationSec) else "${item.reps}")
        if (item.weight > 0) append(" · ${Fmt.kg(item.weight)}")
        append(" · rec. ${Fmt.rest(item.restSec)}")
    }
    AppCard(color = if (expanded) Pal.Secondary else Pal.Surface, padding = 14.dp) {
        if (linkedFromPrev) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Link, null, tint = Pal.Accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("In superserie con l'esercizio precedente", color = Pal.Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Row(Modifier.clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
            ExerciseAvatar(item.exercise.name)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.exercise.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(summary, color = Pal.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (expanded) "Comprimi" else "Espandi", tint = Pal.Muted)
        }
        if (!expanded) return@AppCard

        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Stepper("Serie", "${item.sets}", { onChange { it.copy(sets = (it.sets - 1).coerceAtLeast(1)) } }, { onChange { it.copy(sets = (it.sets + 1).coerceAtMost(20)) } })
            if (timed) {
                Stepper("Durata", Fmt.clock(item.durationSec), { onChange { it.copy(durationSec = (it.durationSec - 15).coerceAtLeast(15)) } }, { onChange { it.copy(durationSec = it.durationSec + 15) } })
            } else {
                Stepper("Ripetizioni", "${item.reps}", { onChange { it.copy(reps = (it.reps - 1).coerceAtLeast(1)) } }, { onChange { it.copy(reps = (it.reps + 1).coerceAtMost(100)) } })
            }
            Stepper("Riscaldamento", "${item.warmupSets}", { onChange { it.copy(warmupSets = (it.warmupSets - 1).coerceAtLeast(0)) } }, { onChange { it.copy(warmupSets = (it.warmupSets + 1).coerceAtMost(5)) } })
            if (!timed) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (item.exercise.type.name == "BODYWEIGHT") "Kg extra" else "Peso (kg)", color = Pal.Muted, fontSize = 12.sp)
                    NumberBox(item.weight, { v -> onChange { it.copy(weight = v) } }, Modifier.width(96.dp))
                }
            }
        }
        Text("In allenamento vengono proposti i valori dell'ultima volta; il peso qui serve per la prima sessione.", color = Pal.Dim, fontSize = 12.sp)

        SectionLabel("Recupero")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            restOptions.forEach { sec -> SelectChip(Fmt.rest(sec), item.restSec == sec, { onChange { it.copy(restSec = sec) } }) }
        }
        ToggleRow("Timer automatico", item.autoTimer, { v -> onChange { it.copy(autoTimer = v) } }, "Parte da solo quando completi una serie")
        if (index < count - 1) {
            ToggleRow("Superserie con il successivo", item.linkNext, { v -> onChange { it.copy(linkNext = v) } }, "Il recupero parte dopo l'ultimo esercizio del gruppo")
        }
        AppTextField(item.notes, { v -> onChange { it.copy(notes = v) } }, "Note per questa scheda", singleLine = false)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(Icons.Rounded.ArrowUpward, "Sposta su", onClick = { onMove(-1) })
            RoundIconButton(Icons.Rounded.ArrowDownward, "Sposta giù", onClick = { onMove(1) })
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onRemove).background(Pal.SurfaceLow).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Delete, null, tint = Pal.Failed, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Rimuovi", color = Pal.Failed, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}
