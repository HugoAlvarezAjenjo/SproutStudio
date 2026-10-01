package es.hugoalvarezajenjo.sproutstudio.render

/**
 * Defense-in-depth for the hard "fully offline" constraint (REQUIREMENTS C1).
 *
 * PlantUML itself runs under the ALLOWLIST security profile with no URL allowlist,
 * which already refuses remote resources. On top of that we refuse, before PlantUML
 * ever sees the text, any directive that would try to reach the network.
 */
object OfflineGuard {

    data class Violation(val line: Int, val message: String)

    // Any preprocessor include whose target is a URL, plus the legacy !includeurl.
    private val includeDirective = Regex("""^\s*!(include\w*|import)\b\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val urlTarget = Regex("""^\s*[<"]?\s*(https?|ftp|file)://""", RegexOption.IGNORE_CASE)

    /** Returns the first network-reaching directive, or null when the source is safe. 1-based line. */
    fun check(source: String): Violation? {
        source.lineSequence().forEachIndexed { idx, raw ->
            val m = includeDirective.find(raw) ?: return@forEachIndexed
            val directive = m.groupValues[1].lowercase()
            val target = m.groupValues[2]
            if (directive == "includeurl" || urlTarget.containsMatchIn(target)) {
                return Violation(
                    line = idx + 1,
                    message = "SproutStudio works fully offline: remote includes are not allowed. " +
                        "Save the file locally and use !include with a local path.",
                )
            }
        }
        return null
    }
}
