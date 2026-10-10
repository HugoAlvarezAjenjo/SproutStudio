package es.hugoalvarezajenjo.sproutstudio.features.editor

import es.hugoalvarezajenjo.sproutstudio.domain.text.FindQuery
import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.find_match_case
import es.hugoalvarezajenjo.sproutstudio.resources.find_regex
import es.hugoalvarezajenjo.sproutstudio.resources.find_words
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ToolButton
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.tr
import androidx.compose.runtime.Composable

/** IntelliJ's three search toggles (match case, whole words, regex), for the find bar and Find in Files. */
@Composable
fun FindOptions(query: FindQuery, onChange: (FindQuery) -> Unit) {
    ToolButton(null, tooltip = tr(Res.string.find_match_case), label = "Cc", selected = query.matchCase) {
        onChange(query.copy(matchCase = !query.matchCase))
    }
    ToolButton(null, tooltip = tr(Res.string.find_words), label = "W", selected = query.wholeWords) {
        onChange(query.copy(wholeWords = !query.wholeWords))
    }
    ToolButton(null, tooltip = tr(Res.string.find_regex), label = ".*", selected = query.regex) {
        onChange(query.copy(regex = !query.regex))
    }
}
