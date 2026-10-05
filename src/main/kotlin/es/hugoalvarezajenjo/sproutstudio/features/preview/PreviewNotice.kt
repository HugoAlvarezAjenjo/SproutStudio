package es.hugoalvarezajenjo.sproutstudio.features.preview

import java.io.File

/** What the last copy or export did, shown briefly over the preview; the view words it. */
sealed interface PreviewNotice {
    data object Copied : PreviewNotice
    data class CopyFailed(val cause: Throwable) : PreviewNotice
    data class Saved(val file: File) : PreviewNotice
    data class ExportFailed(val cause: Throwable) : PreviewNotice
    /** Copy/export asked for while the diagram doesn't render. */
    data object NothingToExport : PreviewNotice
}
