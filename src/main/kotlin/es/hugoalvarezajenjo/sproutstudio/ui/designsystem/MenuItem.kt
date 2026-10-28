package es.hugoalvarezajenjo.sproutstudio.ui.designsystem

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A context-menu entry with what [ContextMenuItem] can't say: an icon, the shortcut that does the
 * same, a destructive action (drawn in red), and a submenu ([children], shown on hover). Plain
 * [ContextMenuItem]s (the text field's Cut/Copy/Paste) still work: they just have none of these.
 */
class MenuItem(
    label: String,
    val icon: ImageVector? = null,
    val shortcut: String? = null,
    val danger: Boolean = false,
    /** [ContextMenuItem] keeps its own flag internal, so the menu reads this one. */
    val isEnabled: Boolean = true,
    val children: List<ContextMenuItem> = emptyList(),
    onClick: () -> Unit = {},
) : ContextMenuItem(label, isEnabled, onClick)

/** A line between groups. Leading, trailing and doubled ones are dropped when shown. */
internal object MenuSeparator : ContextMenuItem("", false, {})

/** Builds a menu in groups: a separator goes between non-empty groups only. */
internal class MenuBuilder {
    private val groups = ArrayList<MutableList<ContextMenuItem>>()

    fun group(block: MutableList<ContextMenuItem>.() -> Unit) {
        groups += ArrayList<ContextMenuItem>().apply(block)
    }

    fun build(): List<ContextMenuItem> = groups.filter { it.isNotEmpty() }.flatMapIndexed { i, g -> if (i == 0) g else listOf(MenuSeparator) + g }
}

internal fun menu(block: MenuBuilder.() -> Unit): List<ContextMenuItem> = MenuBuilder().apply(block).build()

/** Leading, trailing and back-to-back separators removed. */
internal fun List<ContextMenuItem>.tidy(): List<ContextMenuItem> = buildList {
    for (item in this@tidy) {
        if (item === MenuSeparator && (isEmpty() || last() === MenuSeparator)) continue
        add(item)
    }
    if (lastOrNull() === MenuSeparator) removeAt(lastIndex)
}
