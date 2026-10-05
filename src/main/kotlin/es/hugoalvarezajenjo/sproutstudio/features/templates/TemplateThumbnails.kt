package es.hugoalvarezajenjo.sproutstudio.features.templates

import es.hugoalvarezajenjo.sproutstudio.features.preview.DiagramSettings
import es.hugoalvarezajenjo.sproutstudio.services.render.RenderService

/** Where template thumbnails come from: the render service, in the preview's colour mode. */
class TemplateThumbnails(val render: RenderService, val diagram: DiagramSettings)
