package es.hugoalvarezajenjo.sproutstudio.features.git

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import es.hugoalvarezajenjo.sproutstudio.domain.git.LineDiff
import es.hugoalvarezajenjo.sproutstudio.features.git.GitViewModel
import es.hugoalvarezajenjo.sproutstudio.services.git.ChangeType
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.IdeColors
import java.io.File
import kotlinx.coroutines.delay

/** IntelliJ's file-name colour for a git state; null = normal text. */
internal fun IdeColors.vcsColor(type: ChangeType?): Color? = when (type) {
    ChangeType.MODIFIED -> vcsModified
    ChangeType.ADDED -> vcsAdded
    ChangeType.DELETED -> vcsDeleted
    ChangeType.UNVERSIONED -> vcsUnversioned
    ChangeType.CONFLICT -> error
    null -> null
}

/**
 * Gutter markers for [file] with [text] against its committed version. Recomputed shortly after
 * typing stops and whenever HEAD moves (a commit); empty for files git doesn't track yet.
 */
@Composable
internal fun rememberLineChanges(git: GitViewModel, file: File?, text: String): LineDiff.Index {
    var idx by remember(file) { mutableStateOf(LineDiff.Index.Empty) }
    val head = git.status.head
    val type = file?.let { git.changeOf(it)?.type }
    val tracked = file != null && head != null && type != ChangeType.UNVERSIONED && type != ChangeType.ADDED
    LaunchedEffect(file, text, head, tracked) {
        if (!tracked) { idx = LineDiff.Index.Empty; return@LaunchedEffect }
        delay(120) // typing debounce
        idx = git.lineChanges(file, text)
    }
    return idx
}
