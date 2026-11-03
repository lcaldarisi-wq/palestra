package it.palestra.app

import android.app.Application
import android.content.Context
import it.palestra.app.data.BackupManager
import it.palestra.app.data.ExerciseRepository
import it.palestra.app.data.SessionRepository
import it.palestra.app.data.SettingsRepository
import it.palestra.app.data.TemplateRepository
import it.palestra.app.data.db.AppDatabase
import it.palestra.app.timer.RestTimerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PalestraApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Contenitore delle dipendenze (dependency injection manuale, semplice e senza generazione di codice). */
class AppContainer(context: Context) {
    private val app = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db: AppDatabase = AppDatabase.build(app)
    val settings = SettingsRepository(app)
    val exercises = ExerciseRepository(db)
    val templates = TemplateRepository(db)
    val sessions = SessionRepository(db)
    val backup = BackupManager(app, db)
    val timer = RestTimerController(app, settings)

    init {
        scope.launch(Dispatchers.IO) { exercises.seedIfEmpty() }
    }
}
