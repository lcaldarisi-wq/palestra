package it.palestra.app.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.palestra.app.AppContainer
import it.palestra.app.data.ExerciseBlock
import it.palestra.app.data.PreviousPerformance
import it.palestra.app.data.SessionDetail
import it.palestra.app.data.db.SessionStatus
import it.palestra.app.data.db.SetEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WorkoutUi(
    val loaded: Boolean = false,
    val detail: SessionDetail? = null,
    val current: Int = 0,
    /** esercizio → ultima prestazione registrata (prima di questa sessione) */
    val previous: Map<Long, PreviousPerformance> = emptyMap(),
    /** esercizio → peso massimo registrato prima di questa sessione (per evidenziare i nuovi record) */
    val bestWeight: Map<Long, Double> = emptyMap(),
) {
    val inProgress: Boolean get() = detail?.session?.status == SessionStatus.IN_PROGRESS
    val blocks: List<ExerciseBlock> get() = detail?.blocks.orEmpty()
    val currentBlock: ExerciseBlock? get() = blocks.getOrNull(current.coerceIn(0, (blocks.size - 1).coerceAtLeast(0)))
    val totalSets: Int get() = blocks.sumOf { it.sets.size }
    val doneSets: Int get() = blocks.sumOf { it.completedSets }
}

class WorkoutViewModel(private val c: AppContainer, private val sessionId: Long) : ViewModel() {

    private val detailFlow = c.sessions.observeDetail(sessionId)
    private val previous = MutableStateFlow<Map<Long, PreviousPerformance?>>(emptyMap())
    private val current = MutableStateFlow(-1)

    val state: StateFlow<WorkoutUi> = combine(detailFlow, previous, current, c.sessions.setRows) { d, prev, cur, rows ->
        val best = rows.asSequence()
            .filter { it.sessionId != sessionId && !it.isWarmup && it.reps > 0 }
            .groupBy { it.exerciseId }
            .mapValues { (_, l) -> l.maxOf { it.weight } }
        WorkoutUi(
            loaded = true,
            detail = d,
            current = if (cur < 0) d?.session?.currentIndex ?: 0 else cur,
            previous = prev.mapNotNull { (k, v) -> v?.let { k to it } }.toMap(),
            bestWeight = best,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WorkoutUi())

    init {
        viewModelScope.launch {
            detailFlow.collect { d ->
                d ?: return@collect
                val missing = d.blocks.map { it.sessionExercise.exerciseId }.distinct().filter { it !in previous.value }
                missing.forEach { exId ->
                    val p = c.sessions.previousPerformance(exId, sessionId, before = d.session.startedAt)
                    previous.value = previous.value + (exId to p)
                }
            }
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    fun setCurrent(index: Int) {
        current.value = index
        launch {
            val s = c.sessions.get(sessionId) ?: return@launch
            if (s.currentIndex != index) c.sessions.updateSession(s.copy(currentIndex = index))
        }
    }

    fun updateSet(set: SetEntity) = launch { c.sessions.updateSet(set) }

    fun toggleComplete(block: ExerciseBlock, set: SetEntity) {
        val nowCompleted = !set.completed
        launch {
            c.sessions.updateSet(set.copy(completed = nowCompleted, completedAt = if (nowCompleted) System.currentTimeMillis() else null))
        }
        if (nowCompleted) afterSetCompleted(block, set)
    }

    fun toggleWarmup(set: SetEntity) = launch { c.sessions.updateSet(set.copy(isWarmup = !set.isWarmup, isFailed = if (!set.isWarmup) false else set.isFailed)) }

    fun toggleFailed(block: ExerciseBlock, set: SetEntity) {
        val failed = !set.isFailed
        val completeNow = failed && !set.completed
        launch {
            c.sessions.updateSet(
                set.copy(
                    isFailed = failed,
                    isWarmup = if (failed) false else set.isWarmup,
                    completed = set.completed || completeNow,
                    completedAt = if (completeNow) System.currentTimeMillis() else set.completedAt,
                ),
            )
        }
        if (completeNow) afterSetCompleted(block, set)
    }

    /** Gestione superserie e avvio automatico del recupero. */
    private fun afterSetCompleted(block: ExerciseBlock, set: SetEntity) {
        val ui = state.value
        val blocks = ui.blocks
        val idx = blocks.indexOfFirst { it.sessionExercise.id == block.sessionExercise.id }
        val group = block.sessionExercise.supersetGroup
        if (group > 0) {
            val members = blocks.indices.filter { blocks[it].sessionExercise.supersetGroup == group }
            val pos = members.indexOf(idx)
            if (pos in 0 until members.lastIndex) {
                setCurrent(members[pos + 1]) // passa al prossimo esercizio della superserie, senza recupero
                return
            }
            // ultimo del gruppo: recupero e ritorno al primo se restano serie da fare
            val remaining = members.any { m -> blocks[m].sets.any { !it.completed && it.id != set.id } }
            if (remaining) setCurrent(members.first())
        }
        maybeStartTimer(block, set)
    }

    private fun maybeStartTimer(block: ExerciseBlock, set: SetEntity) {
        if (!state.value.inProgress) return
        if (!c.settings.state.value.autoTimer || !block.sessionExercise.autoTimer) return
        val n = block.sets.indexOfFirst { it.id == set.id } + 1
        c.timer.start(block.sessionExercise.restSec, "${block.name} · serie $n")
    }

    fun startTimer(block: ExerciseBlock) = c.timer.start(block.sessionExercise.restSec, block.name)

    fun addSet(block: ExerciseBlock) = launch { c.sessions.addSet(block) }
    fun deleteSet(set: SetEntity) = launch { c.sessions.deleteSet(set.id) }

    fun addExercises(ids: List<Long>) = launch {
        val before = state.value.blocks.size
        c.sessions.addExercises(sessionId, ids, c.settings.state.value.defaultRestSec)
        setCurrent(before)
    }

    fun removeExercise(block: ExerciseBlock) = launch {
        c.sessions.removeExercise(block.sessionExercise.id)
        val size = state.value.blocks.size - 1
        if (state.value.current >= size) setCurrent((size - 1).coerceAtLeast(0))
    }

    fun moveExercise(block: ExerciseBlock, delta: Int) = launch {
        c.sessions.moveExercise(sessionId, block.sessionExercise.id, delta)
        setCurrent((state.value.current + delta).coerceIn(0, (state.value.blocks.size - 1).coerceAtLeast(0)))
    }

    fun setRest(block: ExerciseBlock, sec: Int) = launch { c.sessions.updateSessionExercise(block.sessionExercise.copy(restSec = sec)) }
    fun setAutoTimer(block: ExerciseBlock, on: Boolean) = launch { c.sessions.updateSessionExercise(block.sessionExercise.copy(autoTimer = on)) }

    fun finish(onDone: (saved: Boolean) -> Unit) = launch {
        val wasInProgress = state.value.inProgress
        val saved = c.sessions.finish(sessionId)
        if (wasInProgress) c.timer.skip()
        onDone(saved)
    }

    fun discard(onDone: () -> Unit) = launch {
        c.timer.skip()
        c.sessions.delete(sessionId)
        onDone()
    }
}
