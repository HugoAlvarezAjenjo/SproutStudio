package es.hugoalvarezajenjo.sproutstudio

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The architecture rules, checked on the source text (no library needed):
 *
 *  - Layers depend one way: features → services → domain; core and platform are shared.
 *  - domain is plain Kotlin: no Compose (except the TextFieldValue/TextRange value types),
 *    no coroutines, no JGit, no AWT.
 *  - services do IO but never touch UI state: no Compose, no AWT/Swing (that's platform).
 *  - One public class per file, named after it. Next to it only small value types (data/enum
 *    classes, sealed subtypes), private helpers and extensions. One public screen Composable
 *    per file outside the design system.
 */
class ArchitectureTest {

    private val base = "es.hugoalvarezajenjo.sproutstudio"
    private val srcRoot = File("src/main/kotlin/es/hugoalvarezajenjo/sproutstudio")

    private class Source(val file: File, val pkg: String, val text: String) {
        val imports: List<String> = Regex("""^import\s+([\w.]+)""", RegexOption.MULTILINE).findAll(text).map { it.groupValues[1] }.toList()
    }

    private val sources: List<Source> by lazy {
        srcRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.map { f ->
            val text = f.readText()
            val pkg = Regex("""^package\s+([\w.]+)""", RegexOption.MULTILINE).find(text)?.groupValues?.get(1) ?: ""
            Source(f, pkg, text)
        }.toList()
    }

    private val managed = listOf("app", "core", "domain", "services", "platform", "features", "ui.designsystem")

    private fun layer(name: String) = sources.filter { it.pkg == "$base.$name" || it.pkg.startsWith("$base.$name.") }

    private fun assertNoImports(layer: String, forbidden: List<String>, allowed: Set<String> = emptySet()) {
        val bad = layer(layer).flatMap { s ->
            s.imports.filter { i -> i !in allowed && forbidden.any { i == it || i.startsWith("$it.") } }
                .map { "${s.file.name}: import $it" }
        }
        assertTrue(bad.isEmpty(), "$layer must not depend on:\n" + bad.joinToString("\n"))
    }

    @Test
    fun domainIsPlainKotlin() = assertNoImports(
        "domain",
        listOf("androidx.compose", "kotlinx.coroutines", "org.eclipse.jgit", "java.awt", "javax.swing",
            "$base.core", "$base.services", "$base.platform", "$base.features", "$base.ui", "$base.app"),
        // Value types and a pure diff algorithm: no UI, no IO.
        allowed = setOf("androidx.compose.ui.text.TextRange", "androidx.compose.ui.text.input.TextFieldValue",
            "org.eclipse.jgit.diff.Edit", "org.eclipse.jgit.diff.HistogramDiff", "org.eclipse.jgit.diff.RawText",
            "org.eclipse.jgit.diff.RawTextComparator"),
    )

    @Test
    fun coreDependsOnNothingOfTheApp() = assertNoImports(
        "core",
        listOf("$base.domain", "$base.services", "$base.platform", "$base.features", "$base.ui", "$base.app",
            "androidx.compose.ui", "androidx.compose.foundation", "androidx.compose.material", "androidx.compose.material3"),
    )

    @Test
    fun servicesNeverTouchUi() = assertNoImports(
        "services",
        listOf("androidx.compose", "java.awt", "javax.swing", "$base.features", "$base.ui", "$base.app", "$base.platform"),
    )

    @Test
    fun platformKnowsNoFeature() = assertNoImports(
        "platform",
        listOf("androidx.compose", "$base.services", "$base.features", "$base.ui", "$base.app"),
    )

    @Test
    fun featuresDontReachUpToApp() = assertNoImports("features", listOf("$base.app"))

    @Test
    fun designSystemKnowsNoFeature() = assertNoImports(
        "ui.designsystem",
        listOf("$base.services", "$base.features", "$base.app", "$base.domain"),
    )

    @Test
    fun everyFileIsInALayer() {
        val outside = sources.filter { s -> managed.none { s.pkg == "$base.$it" || s.pkg.startsWith("$base.$it.") } }
        assertTrue(outside.isEmpty(), "files outside the layers: " + outside.joinToString { "${it.file.name} (${it.pkg})" })
    }

    @Test
    fun onlyMainReadsTheGlobalGraph() {
        // Everything else gets its dependencies handed in; AppGraph.default is for main() alone.
        val readers = sources.filter { "AppGraph.default" in it.text && it.file.name !in setOf("Main.kt", "AppGraph.kt") }
        assertTrue(readers.isEmpty(), "AppGraph.default read in: " + readers.joinToString { it.file.name })
    }

    @Test
    fun textsGoThroughTr() {
        // stringResource alone doesn't follow a live language change; tr does (see Strings.kt).
        val direct = sources.filter { s ->
            s.file.name != "Strings.kt" && Regex("""\b(plural)?[sS]tringResource\(""").containsMatchIn(s.text)
        }
        assertTrue(direct.isEmpty(), "use tr(Res.string.x) instead of stringResource in: " + direct.joinToString { it.file.name })
    }

    // ── One class per file ────────────────────────────────────────────────────────────────

    private val typeDecl = Regex(
        """^((?:public|internal|private)\s+)?((?:data|enum|sealed|abstract|open|value|annotation|fun)\s+)*(class|interface|object)\s+(\w+)([^\n{]*)""",
        RegexOption.MULTILINE,
    )
    private val composableDecl = Regex("""^@Composable\s*\n((?:public|internal|private)\s+)?fun\s+(?:[\w.<>]+\.)?(\w+)""", RegexOption.MULTILINE)

    @Test
    fun onePublicClassPerFileNamedAfterIt() {
        val bad = mutableListOf<String>()
        for (s in managed.flatMap(::layer)) {
            val name = s.file.nameWithoutExtension
            val types = typeDecl.findAll(s.text)
                .filter { it.groupValues[1].trim().let { v -> v != "private" && v != "internal" } }
                .toList()
            if (types.isEmpty()) continue
            val names = types.map { it.groupValues[4] }
            if (name !in names) {
                // A screen file (ProblemsPanel.kt holding fun ProblemsPanel) may carry the small
                // value types of its own API.
                val screen = Regex("""^fun\s+$name\(""", RegexOption.MULTILINE).containsMatchIn(s.text)
                val allSmall = types.all { "data" in it.groupValues[2] || "enum" in it.groupValues[2] }
                if (!(screen && allSmall)) bad += "${s.file.name}: no public type named $name (has ${names.joinToString()})"
                continue
            }
            for (t in types) {
                val n = t.groupValues[4]
                if (n == name) continue
                val modifiers = t.groupValues[2]
                val small = "data" in modifiers || "enum" in modifiers
                // Sealed subtypes declared at top level next to their parent are fine too.
                val subtypeOfLocal = names.any { p -> p != n && Regex("""[:,]\s*$p\b""").containsMatchIn(t.groupValues[5]) }
                if (!small && !subtypeOfLocal) bad += "${s.file.name}: second public class $n (only data/enum classes or subtypes may sit next to $name)"
            }
        }
        if (bad.isNotEmpty()) fail("One public class per file:\n" + bad.joinToString("\n"))
    }

    @Test
    fun onePublicScreenComposablePerFile() {
        val bad = mutableListOf<String>()
        for (s in managed.filter { it != "ui.designsystem" }.flatMap(::layer)) {
            val public = composableDecl.findAll(s.text)
                .filter { it.groupValues[1].trim().let { v -> v != "private" && v != "internal" } }
                .map { it.groupValues[2] }.toList()
            // remember* helpers return state for the screen; they aren't screens themselves.
            val screens = public.filterNot { it.startsWith("remember") }
            if (screens.size > 1) bad += "${s.file.name}: ${screens.joinToString()} (split them, or make helpers private)"
            if (screens.size == 1 && typeDecl.findAll(s.text).none() && screens[0] != s.file.nameWithoutExtension) {
                bad += "${s.file.name}: holds ${screens[0]}, so it should be named ${screens[0]}.kt"
            }
        }
        if (bad.isNotEmpty()) fail("One screen Composable per file:\n" + bad.joinToString("\n"))
    }
}
