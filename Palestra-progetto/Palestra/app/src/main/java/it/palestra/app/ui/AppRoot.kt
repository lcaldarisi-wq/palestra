package it.palestra.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import it.palestra.app.ui.calendar.CalendarScreen
import it.palestra.app.ui.components.TimerBar
import it.palestra.app.ui.components.appContainer
import it.palestra.app.ui.history.HistoryScreen
import it.palestra.app.ui.history.SessionDetailScreen
import it.palestra.app.ui.settings.ExerciseLibraryScreen
import it.palestra.app.ui.settings.SettingsScreen
import it.palestra.app.ui.stats.StatsScreen
import it.palestra.app.ui.templates.TemplateEditorScreen
import it.palestra.app.ui.templates.TemplatesScreen
import it.palestra.app.ui.theme.Pal
import it.palestra.app.ui.today.TodayScreen
import it.palestra.app.ui.workout.WorkoutScreen

object Routes {
    const val TODAY = "today"
    const val TEMPLATES = "templates"
    const val CALENDAR = "calendar"
    const val STATS = "stats"
    const val HISTORY = "history"
    const val TEMPLATE = "template/{id}"
    const val WORKOUT = "workout/{id}"
    const val SESSION = "session/{id}"
    const val SETTINGS = "settings"
    const val LIBRARY = "library"

    fun template(id: Long) = "template/$id"
    fun workout(id: Long) = "workout/$id"
    fun session(id: Long) = "session/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.TODAY, "Oggi", Icons.Rounded.Home),
    Tab(Routes.TEMPLATES, "Schede", Icons.Rounded.FitnessCenter),
    Tab(Routes.CALENDAR, "Calendario", Icons.Rounded.CalendarMonth),
    Tab(Routes.STATS, "Statistiche", Icons.Rounded.BarChart),
    Tab(Routes.HISTORY, "Storico", Icons.Rounded.History),
)

/** Azioni di navigazione condivise dalle schermate. */
class Nav(private val nav: NavHostController) {
    fun back() {
        if (nav.previousBackStackEntry != null) nav.popBackStack() else tab(Routes.TODAY)
    }
    fun template(id: Long) = nav.navigate(Routes.template(id))
    fun workout(id: Long) = nav.navigate(Routes.workout(id)) { launchSingleTop = true }
    fun session(id: Long) = nav.navigate(Routes.session(id))
    fun settings() = nav.navigate(Routes.SETTINGS)
    fun library() = nav.navigate(Routes.LIBRARY)
    fun tab(route: String) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    /** Dopo aver concluso un allenamento: mostra il riepilogo al posto della schermata di allenamento. */
    fun replaceWorkoutWithSession(id: Long) = nav.navigate(Routes.session(id)) {
        popUpTo(Routes.WORKOUT) { inclusive = true }
    }
}

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val nav = remember(navController) { Nav(navController) }
    val container = appContainer()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val isTab = tabs.any { it.route == route }
    val active by container.sessions.active.collectAsStateWithLifecycle(initialValue = null)

    NotificationPermissionRequest()

    Scaffold(
        containerColor = Pal.Background,
        bottomBar = {
            if (isTab) {
                Column(Modifier.background(Pal.Background)) {
                    val a = active
                    if (a != null) {
                        ActiveWorkoutBanner(name = a.name, startedAt = a.startedAt, onClick = { nav.workout(a.id) })
                    }
                    TimerBar(container.timer, Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    NavigationBar(containerColor = Pal.Surface, tonalElevation = 0.dp) {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = route == tab.route,
                                onClick = { nav.tab(tab.route) },
                                icon = { Icon(tab.icon, null) },
                                label = { Text(tab.label, fontSize = 11.sp, maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Pal.OnAccent,
                                    selectedTextColor = Pal.Accent,
                                    indicatorColor = Pal.Accent,
                                    unselectedIconColor = Pal.Muted,
                                    unselectedTextColor = Pal.Muted,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.TODAY,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Routes.TODAY) { TodayScreen(nav) }
            composable(Routes.TEMPLATES) { TemplatesScreen(nav) }
            composable(Routes.CALENDAR) { CalendarScreen(nav) }
            composable(Routes.STATS) { StatsScreen() }
            composable(Routes.HISTORY) { HistoryScreen(nav) }
            composable(Routes.SETTINGS) { SettingsScreen(nav) }
            composable(Routes.LIBRARY) { ExerciseLibraryScreen(nav) }
            composable(Routes.TEMPLATE, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                TemplateEditorScreen(templateId = it.arguments?.getLong("id") ?: 0L, nav = nav)
            }
            composable(Routes.WORKOUT, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                WorkoutScreen(sessionId = it.arguments?.getLong("id") ?: 0L, nav = nav)
            }
            composable(Routes.SESSION, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                SessionDetailScreen(sessionId = it.arguments?.getLong("id") ?: 0L, nav = nav)
            }
        }
    }
}

@Composable
private fun ActiveWorkoutBanner(name: String, startedAt: Long, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Pal.Accent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.FitnessCenter, null, tint = Pal.OnAccent)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Allenamento in corso", color = Pal.OnAccent, fontSize = 12.sp)
            Text(name, color = Pal.OnAccent, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Text("Riprendi", color = Pal.OnAccent, fontWeight = FontWeight.Bold)
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Pal.OnAccent, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun NotificationPermissionRequest() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
