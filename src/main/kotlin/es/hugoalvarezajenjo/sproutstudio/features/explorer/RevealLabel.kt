package es.hugoalvarezajenjo.sproutstudio.features.explorer

import es.hugoalvarezajenjo.sproutstudio.core.os.Os
import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.tree_open_folder
import es.hugoalvarezajenjo.sproutstudio.resources.tree_reveal_explorer
import es.hugoalvarezajenjo.sproutstudio.resources.tree_reveal_finder
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.text

/** "Reveal in Finder", "Show in Explorer", or the generic "Open Containing Folder". */
internal fun revealLabel(): String = text(
    when {
        Os.isMac -> Res.string.tree_reveal_finder
        Os.isWindows -> Res.string.tree_reveal_explorer
        else -> Res.string.tree_open_folder
    },
)
