package it.palestra.app.ui.settings

import it.palestra.app.data.db.secondary
import it.palestra.app.data.db.belongsTo
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.palestra.app.data.db.ExerciseEntity
import it.palestra.app.data.db.MuscleGroup
import it.palestra.app.ui.Nav
import it.palestra.app.ui.components.*
import it.palestra.app.ui.theme.Pal
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun SettingsScreen(nav: Nav) {
    val container = appContainer()
    val settings by container.settings.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            message = try {
                val r = container.backup.export(uri)
                "Backup salvato: ${r.sessions} allenamenti, ${r.templates} schede, ${r.exercises} esercizi."
            } catch (e: Exception) {
                "Errore durante l'esportazione: ${e.message}"
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingImport = uri
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        ScreenHeader("Impostazioni", onBack = { nav.back() })
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionLabel("Timer di recupero")
            AppCard(color = Pal.Surface) {
                Text("Recupero predefinito per i nuovi esercizi", fontSize = 14.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(60, 90, 120, 180).forEach { sec ->
                        SelectChip("${sec}s", settings.defaultRestSec == sec, { container.settings.update { it.copy(defaultRestSec = sec) } })
                    }
                }
                ToggleRow("Avvio automatico", settings.autoTimer, { v -> container.settings.update { it.copy(autoTimer = v) } }, "Il recupero parte quando completi una serie")
                ToggleRow("Suono a fine recupero", settings.sound, { v -> container.settings.update { it.copy(sound = v) } })
                ToggleRow("Vibrazione a fine recupero", settings.vibrate, { v -> container.settings.update { it.copy(vibrate = v) } })
                ToggleRow("Schermo sempre acceso in allenamento", settings.keepScreenOn, { v -> container.settings.update { it.copy(keepScreenOn = v) } })
            }

            val notificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
            if (!notificationsOn) {
                AppCard {
                    Text("Notifiche disattivate", fontWeight = FontWeight.Bold)
                    Text("Senza notifiche il timer non può avvisarti quando l'app è in background o lo schermo è spento.", color = Pal.TextSoft, fontSize = 13.sp)
                    PrimaryButton("Attiva notifiche", onClick = {
                        val i = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(i)
                    })
                }
            }

            SectionLabel("Esercizi")
            AppCard(color = Pal.Surface, onClick = { nav.library() }) {
                Text("Libreria esercizi", fontWeight = FontWeight.SemiBold)
                Text("Aggiungi esercizi personalizzati, modifica indicazioni e gruppi muscolari.", color = Pal.Muted, fontSize = 13.sp)
            }

            SectionLabel("Backup e cambio telefono")
            AppCard(color = Pal.Surface) {
                Text(
                    "Tutti i dati restano sul telefono, senza account. Per passare a un nuovo telefono esporta un backup " +
                        "(salvalo su Drive, sul PC o invialo per e-mail) e importalo sul nuovo dispositivo.",
                    color = Pal.TextSoft, fontSize = 13.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Esporta", onClick = { exportLauncher.launch("palestra-backup-${LocalDate.now()}.json") }, icon = Icons.Rounded.Upload, modifier = Modifier.weight(1f))
                    SecondaryButton("Importa", onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, icon = Icons.Rounded.Download, modifier = Modifier.weight(1f))
                }
                Text("È attivo anche il backup automatico di Android (se abilitato nel tuo account Google).", color = Pal.Dim, fontSize = 12.sp)
            }

            Text("Palestra 1.0 · dati salvati solo su questo dispositivo", color = Pal.Dim, fontSize = 12.sp)
        }
    }

    pendingImport?.let { uri ->
        ConfirmDialog(
            title = "Importare il backup?",
            text = "Tutti i dati attuali (schede, allenamenti, esercizi) verranno sostituiti con quelli del file.",
            confirm = "Importa",
            destructive = true,
            onConfirm = {
                scope.launch {
                    message = try {
                        val r = container.backup.import(uri)
                        "Importati ${r.sessions} allenamenti, ${r.templates} schede, ${r.exercises} esercizi."
                    } catch (e: Exception) {
                        "Importazione non riuscita: ${e.message}"
                    }
                }
            },
            onDismiss = { pendingImport = null },
        )
    }

    message?.let { m ->
        AlertDialog(
            onDismissRequest = { message = null },
            containerColor = Pal.Secondary,
            text = { Text(m) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK", color = Pal.Accent) } },
        )
    }
}

/** Libreria completa: consultazione, ricerca, modifica e archiviazione degli esercizi. */
@Composable
fun ExerciseLibraryScreen(nav: Nav) {
    val container = appContainer()
    val all by container.exercises.all.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<MuscleGroup?>(null) }
    var showArchived by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ExerciseEntity?>(null) }
    var creating by remember { mutableStateOf(false) }

    val list = all.filter { it.archived == showArchived && (group == null || it.belongsTo(group!!)) && matches(it, query) }
        .sortedWith(compareBy<ExerciseEntity>({ it.muscleGroup.ordinal }, { it.name.lowercase() }))

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Libreria esercizi", subtitle = "${list.size} esercizi", onBack = { nav.back() }) {
            RoundIconButton(Icons.Rounded.Add, "Nuovo esercizio", onClick = { creating = true }, tint = Pal.OnAccent, container = Pal.Accent)
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SearchField(query, { query = it }, "Cerca esercizio o attrezzo")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { SelectChip("Tutti", group == null, { group = null }) }
                items(MuscleGroup.entries) { g -> SelectChip(g.label, group == g, { group = g }) }
                item { SelectChip(if (showArchived) "Archiviati ✓" else "Archiviati", showArchived, { showArchived = !showArchived }) }
            }
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(list, key = { it.id }) { e ->
                ExerciseRow(
                    e, selected = false,
                    trailing = {
                        Text(
                            if (e.secondary != null) "${e.muscleGroup.label} + ${e.secondary!!.label}" else e.muscleGroup.label,
                            color = Pal.Muted, fontSize = 11.sp,
                        )
                    },
                ) { editing = e }
            }
        }
    }

    if (creating) {
        ExerciseEditDialog(initial = null, initialGroup = group, onDismiss = { creating = false }, onSaved = { creating = false })
    }
    editing?.let { e ->
        ExerciseEditDialog(
            initial = e,
            onDismiss = { editing = null },
            onSaved = { editing = null },
            onToggleArchive = {
                scope.launch { container.exercises.save(e.copy(archived = !e.archived)) }
                editing = null
            },
        )
    }
}
