package es.hugoalvarezajenjo.sproutstudio.core.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * One persisted setting. Reading [value] is observable (Compose recomposes when it changes);
 * writing it updates the UI at once and saves it to the store.
 */
class Setting<T>(
    private val store: SettingsStore,
    private val key: String,
    val default: T,
    parse: (String) -> T?,
    private val format: (T) -> String,
) {
    private var state by mutableStateOf(store.getString(key)?.let(parse) ?: default)

    var value: T
        get() = state
        set(v) {
            if (v == state) return
            state = v
            store.putString(key, format(v))
        }

    val isDefault: Boolean get() = state == default

    fun reset() { value = default }
}

fun SettingsStore.boolean(key: String, default: Boolean) =
    Setting(this, key, default, String::toBooleanStrictOrNull, Boolean::toString)

fun SettingsStore.float(key: String, default: Float) =
    Setting(this, key, default, String::toFloatOrNull, Float::toString)

inline fun <reified E : Enum<E>> SettingsStore.enum(key: String, default: E) =
    Setting(this, key, default, { s -> enumValues<E>().firstOrNull { it.name == s } }, { it.name })

/** A short list of strings (one per line), e.g. recently used items. */
fun SettingsStore.stringList(key: String) =
    Setting(this, key, emptyList(), { s -> s.split('\n').filter { it.isNotEmpty() } }, { it.joinToString("\n") })
