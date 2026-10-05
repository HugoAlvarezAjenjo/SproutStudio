package es.hugoalvarezajenjo.sproutstudio.services.render

/**
 * Defense-in-depth for the hard "fully offline" constraint.
 *
 * PlantUML itself runs under the ALLOWLIST security profile with no URL allowlist,
 * which already refuses remote resources. On top of that we refuse, before PlantUML
 * ever sees the text, any directive that would try to reach the network.
 */
object OfflineGuard {

    /** A directive that would reach the network, at 1-based [line]. */
    data class Violation(val line: Int)

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
                return Violation(line = idx + 1)
            }
        }
        return null
    }
}
