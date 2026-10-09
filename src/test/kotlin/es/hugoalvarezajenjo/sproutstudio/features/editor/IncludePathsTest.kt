package es.hugoalvarezajenjo.sproutstudio.features.editor

import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.CompletionEngine
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class IncludePathsTest {

    private val root = Files.createTempDirectory("includepaths").toFile().apply { deleteOnExit() }
    private val main = root.resolve("main.puml").apply { writeText("") }

    init {
        root.resolve("common").mkdir()
        root.resolve("common/styles.puml").writeText("")
        root.resolve(".hidden.puml").writeText("")
        root.resolve("other.puml").writeText("")
    }

    @Test fun `lists the folder next to the file, without itself or hidden entries`() {
        val names = assertNotNull(IncludePaths.list(main.path, "")).map { it.name }.sorted()
        assertEquals(listOf("common", "other.puml"), names)
    }

    @Test fun `lists sub and parent folders`() {
        assertEquals(listOf("styles.puml"), IncludePaths.list(main.path, "common/")?.map { it.name })
        val fromSub = root.resolve("common/styles.puml").path
        assertEquals(listOf("common", "main.puml", "other.puml"), IncludePaths.list(fromSub, "../")?.map { it.name }?.sorted())
    }

    @Test fun `a missing folder or an unsaved file lists nothing`() {
        assertNull(IncludePaths.list(main.path, "nope/"))
        assertNull(IncludePaths.list(null, ""))
    }

    @Test fun `completion walks real folders`() {
        val text = "!include com"
        val r = assertNotNull(CompletionEngine.complete(text, text.length, file = main.path, paths = IncludePaths))
        assertEquals(listOf("common/"), r.items.map { it.label })
    }
}
