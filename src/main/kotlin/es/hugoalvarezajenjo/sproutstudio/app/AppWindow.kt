package es.hugoalvarezajenjo.sproutstudio.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import es.hugoalvarezajenjo.sproutstudio.domain.git.DiffSide
import es.hugoalvarezajenjo.sproutstudio.features.workspace.WorkspaceViewModel
import java.io.File

/** A window the app is showing. */
sealed class AppWindow {
    val id: Long = nextId++

    class QuickPreview(val file: File) : AppWindow()

    /** [file] at [left] next to [right] (by default: last commit vs now), from [project]'s repository. */
    class DiagramDiff(
        val project: WorkspaceViewModel,
        val file: File,
        val left: DiffSide = DiffSide.Head,
        val right: DiffSide = DiffSide.WorkingCopy,
        val textFirst: Boolean = false,
    ) : AppWindow()

    /** Commits that touched [file], each viewable as a diagram or text diff. */
    class History(val project: WorkspaceViewModel, val file: File) : AppWindow()

    class Project(val state: WorkspaceViewModel, revealed: Boolean = true) : AppWindow() {
        /** False while composed-but-hidden (the boot welcome waiting to see if a file arrives). */
        var revealed by mutableStateOf(revealed)
    }

    private companion object { var nextId = 1L }
}
