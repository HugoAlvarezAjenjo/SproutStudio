package es.hugoalvarezajenjo.sproutstudio.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import es.hugoalvarezajenjo.sproutstudio.core.i18n.Language
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/*
 * Translated texts. They live in src/main/composeResources/values/strings.xml (English) and
 * values-es/strings.xml (Spanish); the build turns each <string name="x"> into Res.string.x.
 *
 * Screens show them with tr(Res.string.x), never stringResource directly: Compose resources
 * choose the language by the JVM's default locale and don't notice when it changes, so tr
 * also reads LocalLanguage, which recomposes it when the user picks another language.
 */

/** The interface language, provided once for every window by [ProvideLanguage]. */
val LocalLanguage = compositionLocalOf { Language.English }

/** Shows [content] (all of the app's windows) in [language], switching live when it changes. */
@Composable
fun ProvideLanguage(language: Language, content: @Composable () -> Unit) {
    // Before the content composes: the strings below look the language up from the JVM locale.
    language.makeDefault()
    CompositionLocalProvider(LocalLanguage provides language, content = content)
}

/** The text [res] in the interface language. */
@Composable
fun tr(res: StringResource): String {
    LocalLanguage.current
    return stringResource(res)
}

/** The text [res] with its %1$s, %2$d... placeholders filled in. */
@Composable
fun tr(res: StringResource, vararg args: Any): String {
    LocalLanguage.current
    return stringResource(res, *args)
}

/** The singular or plural form of [res] for [count], placeholders filled with [args]. */
@Composable
fun tr(res: PluralStringResource, count: Int, vararg args: Any): String {
    LocalLanguage.current
    return pluralStringResource(res, count, *args)
}

/**
 * The text [res] outside composition, for lists built in plain code such as the command
 * palette's commands. Same language as [tr]: both look it up from the JVM locale that
 * [ProvideLanguage] sets. Compose resources only offer a suspending lookup here; strings are
 * cached after the first read, so blocking for it is cheap.
 */
fun text(res: StringResource, vararg args: Any): String = runBlocking { getString(res, *args) }

/** [text] for the singular or plural form of [res] for [count]. */
fun text(res: PluralStringResource, count: Int, vararg args: Any): String =
    runBlocking { getPluralString(res, count, *args) }
