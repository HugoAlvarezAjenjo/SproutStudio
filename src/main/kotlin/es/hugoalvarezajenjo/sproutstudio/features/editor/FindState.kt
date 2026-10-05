package es.hugoalvarezajenjo.sproutstudio.features.editor

import es.hugoalvarezajenjo.sproutstudio.domain.text.FindQuery

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.receiveAsFlow
import androidx.compose.runtime.mutableStateOf
import es.hugoalvarezajenjo.sproutstudio.features.document.Document

/** Per-document state of the find bar (kept on the Document so the menu can drive it). */
@Stable
class FindState {
    var visible by mutableStateOf(false)
    var replaceVisible by mutableStateOf(false)
    var query by mutableStateOf(FindQuery(""))
    var replacement by mutableStateOf("")
    private val focusChannel = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
    private val revealChannel = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
    /** The find field should take focus (and select its text). */
    val focusRequests: kotlinx.coroutines.flow.Flow<Unit> = focusChannel.receiveAsFlow()
    /** A find action moved the selection: the editor scrolls it into view. */
    val revealRequests: kotlinx.coroutines.flow.Flow<Unit> = revealChannel.receiveAsFlow()

    fun requestReveal() { revealChannel.trySend(Unit) }
    /** Status for the bar after Replace All ("3 replaced"); cleared on the next edit of the query. */
    var notice by mutableStateOf<String?>(null)

    fun open(replace: Boolean, seed: String?) {
        visible = true
        if (replace) replaceVisible = true
        // Seed with the editor selection, like IntelliJ, but not with multi-line blocks.
        if (!seed.isNullOrEmpty() && '\n' !in seed) query = query.copy(text = seed)
        focusChannel.trySend(Unit)
    }

    fun close() {
        visible = false
        replaceVisible = false
        notice = null
    }
}
