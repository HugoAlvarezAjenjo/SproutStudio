package es.hugoalvarezajenjo.sproutstudio.features.explorer

import java.io.File

/** A file-tree operation waiting for the user's input (a name, or a confirmation). */
sealed class FileAction {
    data class NewDiagram(val dir: File) : FileAction()
    data class NewFolder(val dir: File) : FileAction()
    data class Rename(val target: File) : FileAction()
    data class Delete(val target: File) : FileAction()
}
