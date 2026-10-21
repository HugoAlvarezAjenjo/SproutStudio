package es.hugoalvarezajenjo.sproutstudio.features.explorer

import es.hugoalvarezajenjo.sproutstudio.core.os.Os
import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.trash_restore_hint
import es.hugoalvarezajenjo.sproutstudio.resources.trash_restore_hint_linux
import es.hugoalvarezajenjo.sproutstudio.resources.trash_restore_hint_windows
import es.hugoalvarezajenjo.sproutstudio.resources.tree_open_folder
import es.hugoalvarezajenjo.sproutstudio.resources.tree_reveal_explorer
import es.hugoalvarezajenjo.sproutstudio.resources.tree_reveal_finder
import es.hugoalvarezajenjo.sproutstudio.resources.welcome_tip
import es.hugoalvarezajenjo.sproutstudio.resources.welcome_tip_linux
import es.hugoalvarezajenjo.sproutstudio.resources.welcome_tip_windows
import org.jetbrains.compose.resources.StringResource

/**
 * The texts that name the OS's own file manager or Trash, all in one place. Each `when` covers
 * every [Os.Family], so a new OS can't silently get the macOS wording.
 */
object OsTexts {
    /** "Reveal in Finder", "Show in Explorer", or the generic "Open Containing Folder". */
    val reveal: StringResource = when (Os.family) {
        Os.Family.MAC -> Res.string.tree_reveal_finder
        Os.Family.WINDOWS -> Res.string.tree_reveal_explorer
        Os.Family.LINUX -> Res.string.tree_open_folder
    }

    /** Where a file moved to the Trash can be restored from. */
    val trashRestoreHint: StringResource = when (Os.family) {
        Os.Family.MAC -> Res.string.trash_restore_hint
        Os.Family.WINDOWS -> Res.string.trash_restore_hint_windows
        Os.Family.LINUX -> Res.string.trash_restore_hint_linux
    }

    /** The welcome screen's tip about double-clicking a .puml in the file manager. */
    val welcomeTip: StringResource = when (Os.family) {
        Os.Family.MAC -> Res.string.welcome_tip
        Os.Family.WINDOWS -> Res.string.welcome_tip_windows
        Os.Family.LINUX -> Res.string.welcome_tip_linux
    }
}
