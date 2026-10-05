package es.hugoalvarezajenjo.sproutstudio.features.templates

import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.Template
import es.hugoalvarezajenjo.sproutstudio.features.preview.DiagramImage
import es.hugoalvarezajenjo.sproutstudio.features.preview.DiagramSettings
import es.hugoalvarezajenjo.sproutstudio.services.render.RenderService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Where template thumbnails come from: the render service, in the preview's colour mode. */
class TemplateThumbnails(
    private val render: RenderService,
    val diagram: DiagramSettings,
    /** Decoding the PNG is CPU work, kept off the UI thread. */
    private val decode: CoroutineDispatcher,
) {
    /** The thumbnail of [template], or null if it doesn't render. */
    suspend fun image(template: Template, dark: Boolean): DiagramImage? {
        val bytes = try {
            render.thumbnail(template.previewSource, dark)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } ?: return null
        return withContext(decode) { DiagramImage.fromPng(bytes, 1.0) }
    }
}
