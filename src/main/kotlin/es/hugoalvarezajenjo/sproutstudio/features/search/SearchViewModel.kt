package es.hugoalvarezajenjo.sproutstudio.features.search

import es.hugoalvarezajenjo.sproutstudio.core.concurrency.AppDispatchers
import es.hugoalvarezajenjo.sproutstudio.core.viewmodel.ViewModel
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.SourceFile
import es.hugoalvarezajenjo.sproutstudio.domain.text.FindQuery
import es.hugoalvarezajenjo.sproutstudio.domain.text.ProjectSearch
import es.hugoalvarezajenjo.sproutstudio.domain.text.ProjectSearchResult
import es.hugoalvarezajenjo.sproutstudio.services.files.ProjectFiles
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Find in Files (⇧⌘F): searches every diagram of the project for [query], off the UI thread.
 * Open tabs are searched as they are on screen, unsaved edits included, so a hit's range is
 * where the tab has it. Typing restarts the search after a short pause; a newer search
 * cancels the one running.
 */
@Stable
class SearchViewModel(
    private val files: ProjectFiles,
    private val dispatchers: AppDispatchers,
    /** The project folder, or null when the window has none. */
    private val root: () -> File?,
    /** The text of each open tab, by absolute file; read on the UI thread when a search starts. */
    private val openTexts: () -> Map<File, String>,
    parent: Job? = null,
) : ViewModel(dispatchers.main, parent) {

    var query by mutableStateOf(FindQuery(""))
        private set
    var result by mutableStateOf(ProjectSearchResult.Empty)
        private set
    /** True while a search runs, for the panel's progress hint. */
    var searching by mutableStateOf(false)
        private set

    private val focus = Channel<Unit>(Channel.CONFLATED)
    /** The panel's field should take the keyboard (⇧⌘F). */
    val focusRequests: Flow<Unit> = focus.receiveAsFlow()

    private var job: Job? = null

    /** ⇧⌘F: seed the query with the editor's selection (one line only, like IntelliJ) and focus. */
    fun open(seed: String?) {
        if (!seed.isNullOrEmpty() && '\n' !in seed && seed != query.text) {
            query = query.copy(text = seed)
            search(debounce = false)
        }
        focus.trySend(Unit)
    }

    fun changeQuery(q: FindQuery) {
        if (q == query) return
        query = q
        search(debounce = true)
    }

    /** Search again now (Enter in the field, the refresh button). */
    fun refresh() = search(debounce = false)

    /** Files changed (an edit, a save): search again after the usual pause, if there is a query. */
    fun refreshSoon() { if (query.text.isNotEmpty()) search(debounce = true) }

    /** The project closed or changed: nothing from the old one stays listed. */
    fun clear() {
        job?.cancel()
        result = ProjectSearchResult.Empty
        searching = false
    }

    private fun search(debounce: Boolean) {
        job?.cancel()
        val q = query
        val dir = root()
        if (q.text.isEmpty() || dir == null) { clear(); return }
        val open = openTexts()
        job = scope.launch {
            if (debounce) delay(DEBOUNCE_MS)
            searching = true
            try {
                result = withContext(dispatchers.io) { ProjectSearch.search(sources(dir, open), q) }
            } finally {
                searching = false
            }
        }
    }

    private fun sources(dir: File, open: Map<File, String>): Sequence<SourceFile> =
        files.allDiagrams(dir).asSequence().mapNotNull { f ->
            val text = open[f] ?: runCatching { f.readText() }.getOrNull() ?: return@mapNotNull null
            SourceFile(f.path, text)
        }

    companion object {
        /** Pause after the last keystroke before searching. */
        const val DEBOUNCE_MS = 250L
    }
}
