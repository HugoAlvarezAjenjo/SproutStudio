package es.hugoalvarezajenjo.sproutstudio.features.editor

import androidx.compose.runtime.Composable
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.DiagramKind
import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.diagram_activity
import es.hugoalvarezajenjo.sproutstudio.resources.diagram_class
import es.hugoalvarezajenjo.sproutstudio.resources.diagram_component
import es.hugoalvarezajenjo.sproutstudio.resources.diagram_gantt
import es.hugoalvarezajenjo.sproutstudio.resources.diagram_mindmap
import es.hugoalvarezajenjo.sproutstudio.resources.diagram_sequence
import es.hugoalvarezajenjo.sproutstudio.resources.diagram_state
import es.hugoalvarezajenjo.sproutstudio.resources.diagram_unknown
import es.hugoalvarezajenjo.sproutstudio.resources.diagram_usecase
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.tr

/** The diagram kind's name (status bar, completion popup), translated through strings.xml. */
@Composable
internal fun DiagramKind.describe(): String = tr(
    when (this) {
        DiagramKind.SEQUENCE -> Res.string.diagram_sequence
        DiagramKind.CLASS -> Res.string.diagram_class
        DiagramKind.ACTIVITY -> Res.string.diagram_activity
        DiagramKind.USECASE -> Res.string.diagram_usecase
        DiagramKind.STATE -> Res.string.diagram_state
        DiagramKind.COMPONENT -> Res.string.diagram_component
        DiagramKind.MINDMAP -> Res.string.diagram_mindmap
        DiagramKind.GANTT -> Res.string.diagram_gantt
        DiagramKind.UNKNOWN -> Res.string.diagram_unknown
    },
)
