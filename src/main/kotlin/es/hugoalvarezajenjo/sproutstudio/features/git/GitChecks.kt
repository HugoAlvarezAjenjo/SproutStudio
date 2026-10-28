package es.hugoalvarezajenjo.sproutstudio.features.git

import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.text
import androidx.compose.ui.input.key.type
import es.hugoalvarezajenjo.sproutstudio.features.document.isDiagram
import es.hugoalvarezajenjo.sproutstudio.features.workspace.WorkspaceViewModel
import es.hugoalvarezajenjo.sproutstudio.services.git.ChangeType
import java.io.File

/** True when [f] is committed and differs from that version (a text diff is meaningful). */
internal fun hasCommittedChanges(p: WorkspaceViewModel, f: File?): Boolean =
    f != null && p.git.changeOf(f)?.type.let { it == ChangeType.MODIFIED || it == ChangeType.DELETED }

/** Opens [f] next to its committed version. */
internal fun canCompareWithHead(p: WorkspaceViewModel, f: File?): Boolean =
    f != null && f.isDiagram() && p.git.changeOf(f)?.type == ChangeType.MODIFIED
