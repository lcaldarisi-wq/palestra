package it.palestra.app.ui.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import it.palestra.app.AppContainer
import it.palestra.app.data.db.TemplateEntity
import it.palestra.app.ui.Fmt
import it.palestra.app.ui.Nav
import it.palestra.app.ui.components.*
import it.palestra.app.ui.theme.Pal
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TemplateSummary(val template: TemplateEntity, val exerciseCount: Int, val setCount: Int, val days: List<Int>, val preview: String)

data class TemplatesState(
    val loaded: Boolean = false,
    val items: List<TemplateSummary> = emptyList(),
    /** giorno (1..7) → id scheda */
    val schedule: Map<Int, Long> = emptyMap(),
)

class TemplatesViewModel(private val c: AppContainer) : ViewModel() {
    val state = combine(c.templates.templates, c.templates.templateExercises, c.templates.schedule, c.exercises.all) { t, te, sch, ex ->
        val names = ex.associate { it.id to it.name }
        TemplatesState(
            loaded = true,
            items = t.map { tpl ->
                val items = te.filter { it.templateId == tpl.id }
                TemplateSummary(
                    template = tpl,
                    exerciseCount = items.size,
                    setCount = items.sumOf { it.sets },
                    days = sch.filter { it.templateId == tpl.id }.map { it.dayOfWeek }.sorted(),
                    preview = items.take(4).joinToString(" · ") { names[it.exerciseId] ?: "?" },
                )
            },
            schedule = sch.associate { it.dayOfWeek to it.templateId },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TemplatesState())

    fun assign(day: Int, templateId: Long?) = viewModelScope.launch { c.templates.assignDay(day, templateId) }
    fun duplicate(id: Long) = viewModelScope.launch { c.templates.duplicate(id) }
    fun delete(id: Long) = viewModelScope.launch { c.templates.delete(id) }
    fun start(id: Long, onStarted: (Long) -> Unit) = viewModelScope.launch {
        val existing = c.sessions.getActive()
        onStarted(existing?.id ?: c.sessions.startFromTemplate(id))
    }
}

@Composable
fun TemplatesScreen(nav: Nav) {
    val vm = appViewModel { TemplatesViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    var dayToAssign by remember { mutableStateOf<Int?>(null) }
    var toDelete by remember { mutableStateOf<TemplateEntity?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        ScreenHeader("Schede") {
            RoundIconButton(Icons.Rounded.Book, "Libreria esercizi", onClick = { nav.library() })
            RoundIconButton(Icons.Rounded.Add, "Nuova scheda", onClick = { nav.template(0) }, tint = Pal.OnAccent, container = Pal.Accent)
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (s.loaded && s.items.isEmpty()) {
                AppCard {
                    EmptyState(
                        Icons.Rounded.FitnessCenter,
                        "Nessuna scheda",
                        "Crea la tua prima scheda e scegli gli esercizi dal catalogo.",
                    ) { PrimaryButton("Crea scheda", onClick = { nav.template(0) }, icon = Icons.Rounded.Add) }
                }
            }

            s.items.forEach { item ->
                TemplateCard(
                    item = item,
                    onOpen = { nav.template(item.template.id) },
                    onStart = { vm.start(item.template.id) { nav.workout(it) } },
                    onDuplicate = { vm.duplicate(item.template.id) },
                    onDelete = { toDelete = item.template },
                )
            }

            if (s.items.isNotEmpty()) {
                SectionLabel("Programmazione settimanale", Modifier.padding(top = 8.dp))
                AppCard(color = Pal.Surface, padding = 6.dp) {
                    (1..7).forEach { day ->
                        val tid = s.schedule[day]
                        val name = s.items.firstOrNull { it.template.id == tid }?.template?.name
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { dayToAssign = day }.padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(Fmt.weekLong[day - 1], modifier = Modifier.width(110.dp), fontWeight = FontWeight.SemiBold)
                            Text(name ?: "Riposo", color = if (name != null) Pal.Accent else Pal.Dim, modifier = Modifier.weight(1f), fontWeight = if (name != null) FontWeight.Bold else FontWeight.Normal)
                            Icon(Icons.Rounded.ExpandMore, null, tint = Pal.Muted)
                        }
                    }
                }
                Text(
                    "Gli allenamenti si possono spostare o recuperare dal Calendario senza modificare la programmazione.",
                    color = Pal.Muted, fontSize = 12.sp,
                )
            }
        }
    }

    dayToAssign?.let { day ->
        AlertDialog(
            onDismissRequest = { dayToAssign = null },
            containerColor = Pal.Secondary,
            title = { Text(Fmt.weekLong[day - 1]) },
            text = {
                Column {
                    (listOf<TemplateEntity?>(null) + s.items.map { it.template }).forEach { t ->
                        val selected = s.schedule[day] == t?.id
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { vm.assign(day, t?.id); dayToAssign = null }.padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = Pal.Accent))
                            Spacer(Modifier.width(10.dp))
                            Text(t?.name ?: "Riposo")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dayToAssign = null }) { Text("Chiudi", color = Pal.Accent) } },
        )
    }

    toDelete?.let { t ->
        ConfirmDialog(
            title = "Eliminare \"${t.name}\"?",
            text = "La scheda verrà rimossa dalla programmazione. Gli allenamenti già registrati restano nello storico.",
            confirm = "Elimina",
            destructive = true,
            onConfirm = { vm.delete(t.id) },
            onDismiss = { toDelete = null },
        )
    }
}

@Composable
private fun TemplateCard(item: TemplateSummary, onOpen: () -> Unit, onStart: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    AppCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.template.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${item.exerciseCount} esercizi · ${item.setCount} serie", color = Pal.Muted, fontSize = 13.sp)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Altre azioni", tint = Pal.Muted) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Pal.Secondary) {
                    DropdownMenuItem(text = { Text("Modifica") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; onOpen() })
                    DropdownMenuItem(text = { Text("Duplica") }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) }, onClick = { menu = false; onDuplicate() })
                    DropdownMenuItem(text = { Text("Elimina", color = Pal.Failed) }, leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = Pal.Failed) }, onClick = { menu = false; onDelete() })
                }
            }
        }
        if (item.preview.isNotBlank()) Text(item.preview, color = Pal.TextSoft, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (item.days.isEmpty()) {
                Text("Non assegnata a nessun giorno", color = Pal.Dim, fontSize = 12.sp, modifier = Modifier.weight(1f))
            } else {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item.days.forEach { d ->
                        Text(
                            Fmt.weekShort[d - 1], fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Pal.Accent,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Pal.SurfaceLow).padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            Box(
                Modifier.clip(RoundedCornerShape(12.dp)).background(Pal.Accent).clickable(onClick = onStart).padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = Pal.OnAccent, modifier = Modifier.size(18.dp))
                    Text("Avvia", color = Pal.OnAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}
