package es.hugoalvarezajenjo.sproutstudio.app

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import kotlin.test.AfterTest

/** What the `--export` and `--check` tests share: a temporary folder, and the terminal output. */
abstract class BatchTest {

    protected val dir: File = Files.createTempDirectory("batch").toFile()
    private val out = ByteArrayOutputStream()
    private val err = ByteArrayOutputStream()

    @AfterTest
    fun cleanUp() { dir.deleteRecursively() }

    /** A [Batch] that prints here, with paths shown relative to [dir]. */
    protected val batch get() = Batch(PrintStream(out, true), PrintStream(err, true), workingDir = dir)

    protected val stdout get() = out.toString()
    protected val stderr get() = err.toString()

    protected fun write(path: String, text: String) = dir.resolve(path).apply { parentFile.mkdirs(); writeText(text) }

    protected fun diagram(body: String = "A -> B: hi") = "@startuml\n$body\n@enduml\n"

    protected fun files(root: File = dir) =
        root.walkTopDown().filter { it.isFile }.map { it.relativeTo(root).invariantSeparatorsPath }.toSet()
}
