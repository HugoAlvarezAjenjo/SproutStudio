package es.hugoalvarezajenjo.sproutstudio.services.git

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitIgnoreTest {
    private val dir: File = Files.createTempDirectory("ignore").toFile()
    private val file get() = File(dir, ".gitignore")

    @AfterTest fun cleanup() { dir.deleteRecursively() }

    @Test fun `a rule matches exactly that path from the root, folders with a slash`() {
        assertEquals("/docs/a.puml", GitIgnore.ruleFor("docs/a.puml", isDirectory = false))
        assertEquals("/docs/", GitIgnore.ruleFor("docs", isDirectory = true))
        assertEquals("/docs/a.puml", GitIgnore.ruleFor("docs\\a.puml", isDirectory = false))
    }

    @Test fun `adding creates the file`() {
        assertTrue(GitIgnore.add(dir, "a.puml", isDirectory = false))
        assertEquals("/a.puml\n", file.readText())
    }

    @Test fun `adding keeps what was there and starts a new line`() {
        file.writeText(".DS_Store")
        GitIgnore.add(dir, "a.puml", isDirectory = false)
        assertEquals(".DS_Store\n/a.puml\n", file.readText())
    }

    @Test fun `a rule already there is not written twice`() {
        GitIgnore.add(dir, "docs", isDirectory = true)
        assertFalse(GitIgnore.add(dir, "docs", isDirectory = true))
        assertEquals("/docs/\n", file.readText())
    }
}
