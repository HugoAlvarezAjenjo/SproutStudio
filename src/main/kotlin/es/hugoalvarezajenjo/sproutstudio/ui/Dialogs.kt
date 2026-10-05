package es.hugoalvarezajenjo.sproutstudio.ui

import es.hugoalvarezajenjo.sproutstudio.app.AppGraph
import es.hugoalvarezajenjo.sproutstudio.model.DiagramExtensions
import java.io.File

/** Legacy access to the platform's dialogs; goes away as screens get ViewModels. */
object Dialogs {
    private val platform get() = AppGraph.default.platform

    val isMac: Boolean get() = platform.isMac

    fun openDiagram(): File? = platform.chooseFile("Open a diagram", DiagramExtensions)

    fun openFolder(): File? = platform.chooseFolder("Open a project folder")

    /** Adds `.puml` when the name has no diagram extension. */
    fun saveAs(suggested: String, dir: File?): File? {
        val f = platform.chooseSaveTarget("Save diagram", suggested, dir) ?: return null
        return if (f.extension.lowercase() in DiagramExtensions) f else File(f.parentFile, "${f.name}.puml")
    }
}
