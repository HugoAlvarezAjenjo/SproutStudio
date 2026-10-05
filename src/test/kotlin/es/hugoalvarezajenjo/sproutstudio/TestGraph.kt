package es.hugoalvarezajenjo.sproutstudio

import es.hugoalvarezajenjo.sproutstudio.app.AppGraph
import es.hugoalvarezajenjo.sproutstudio.features.document.Document
import es.hugoalvarezajenjo.sproutstudio.features.templates.TemplateThumbnails
import es.hugoalvarezajenjo.sproutstudio.features.workspace.WorkspaceViewModel
import java.io.File

/** The app's graph as tests see it: settings in memory (see the Gradle test task). */
object TestGraph {
    val graph: AppGraph get() = AppGraph.default
    val deps get() = graph.deps
    val thumbnails get() = TemplateThumbnails(graph.render, graph.diagram)

    fun workspace(): WorkspaceViewModel = graph.newWorkspace()
    fun document(file: File?, text: String) = Document(file, text, graph.documents)
    fun openDocument(file: File) = Document.open(file, graph.documents)
}
