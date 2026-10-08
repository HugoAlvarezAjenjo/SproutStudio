package es.hugoalvarezajenjo.sproutstudio.ui.designsystem

import androidx.compose.ui.graphics.Color

/** Colors for each token category. */
data class SyntaxColors(
    val keyword: Color,
    val directive: Color,
    val preprocessor: Color,
    val string: Color,
    val comment: Color,
    val arrow: Color,
    val color: Color,
    val stereotype: Color,
    val symbol: Color,
    val error: Color,
    val bracketMatch: Color,
)
