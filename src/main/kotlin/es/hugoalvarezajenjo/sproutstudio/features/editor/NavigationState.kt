package es.hugoalvarezajenjo.sproutstudio.features.editor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.Occurrence

/** The "N usages of X" list: where [symbol] appears, and the row picked with ↑/↓. */
data class UsagesList(val symbol: String, val items: List<Occurrence>, val selected: Int = 0)

/**
 * One document's navigation: where Go to Declaration came from (for Back / Forward, as caret
 * offsets clamped to the text when used), and the usages list on screen.
 */
@Stable
class NavigationState {
    var usages by mutableStateOf<UsagesList?>(null)
        private set
    var canGoBack by mutableStateOf(false)
        private set
    var canGoForward by mutableStateOf(false)
        private set

    private val back = ArrayDeque<Int>()
    private val forward = ArrayDeque<Int>()

    fun showUsages(list: UsagesList) { usages = list }
    fun closeUsages() { usages = null }

    /** Move the usages selection by [delta] rows, wrapping around. */
    fun moveSelection(delta: Int) {
        val u = usages ?: return
        usages = u.copy(selected = Math.floorMod(u.selected + delta, u.items.size))
    }

    /** A jump away from [from]: Back returns there; a new jump drops the Forward trail. */
    fun recordJump(from: Int) {
        if (back.lastOrNull() != from) back.addLast(from)
        if (back.size > LIMIT) back.removeFirst()
        forward.clear()
        sync()
    }

    /** Where Back goes from [current], or null at the start of the trail. */
    fun popBack(current: Int): Int? {
        val to = back.removeLastOrNull() ?: return null
        forward.addLast(current)
        sync()
        return to
    }

    /** Where Forward goes from [current], or null at the end of the trail. */
    fun popForward(current: Int): Int? {
        val to = forward.removeLastOrNull() ?: return null
        back.addLast(current)
        sync()
        return to
    }

    private fun sync() {
        canGoBack = back.isNotEmpty()
        canGoForward = forward.isNotEmpty()
    }

    private companion object {
        const val LIMIT = 50
    }
}
