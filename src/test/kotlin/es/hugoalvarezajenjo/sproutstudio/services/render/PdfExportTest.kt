package es.hugoalvarezajenjo.sproutstudio.services.render

import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.Inflater
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PdfExportTest {

    init {
        System.setProperty("java.awt.headless", "true")
    }

    private fun exportPdf(src: String, index: Int = 0): ByteArray {
        val target = Files.createTempFile("sprout", ".pdf").toFile().apply { deleteOnExit() }
        runBlocking { PlantUmlEngine.export(src, null, index, ExportFormat.PDF, target) }
        return target.readBytes()
    }

    /** The PDF's page content, with every Flate-compressed stream inflated. */
    private fun contentOf(pdf: ByteArray): String {
        val raw = String(pdf, Charsets.ISO_8859_1)
        val out = StringBuilder(raw)
        var at = 0
        while (true) {
            val s = raw.indexOf("stream", at).takeIf { it >= 0 } ?: break
            var start = s + "stream".length
            if (raw.startsWith("\r\n", start)) start += 2 else if (raw[start] == '\n') start += 1
            val end = raw.indexOf("endstream", start).takeIf { it >= 0 } ?: break
            runCatching {
                val inf = Inflater().apply { setInput(pdf, start, end - start) }
                val buf = ByteArray(64 * 1024)
                val bytes = ByteArrayOutputStream()
                while (!inf.finished()) {
                    val n = inf.inflate(buf)
                    if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
                    bytes.write(buf, 0, n)
                }
                out.append('\n').append(bytes.toString(Charsets.ISO_8859_1))
            }
            at = end + "endstream".length
        }
        return out.toString()
    }

    @Test
    fun `exports a diagram as a real PDF document`() {
        val pdf = exportPdf("@startuml\nAlice -> Bob: hello\n@enduml\n")
        val text = String(pdf, Charsets.ISO_8859_1)
        assertTrue(text.startsWith("%PDF-"), "not a PDF header: ${text.take(20)}")
        assertTrue(text.trimEnd().endsWith("%%EOF"), "PDF is truncated")
    }

    @Test
    fun `the PDF is vector, with its text as real text and not a picture`() {
        val content = contentOf(exportPdf("@startuml\nAlice -> Bob: hello\n@enduml\n"))
        assertTrue("/Font" in content, "no font: the text was not written as text")
        assertTrue("/Subtype/Image" !in content.replace(" ", ""), "the diagram was embedded as an image")
        for (word in listOf("Alice", "Bob", "hello")) assertTrue(word in content, "missing text '$word'")
    }

    @Test
    fun `default text uses a sans-serif font, like the preview, not Times`() {
        val content = contentOf(exportPdf("@startuml\nAlice -> Bob: hello\n@enduml\n"))
        assertTrue("Helvetica" in content, "default text is not Helvetica")
        assertTrue("Times" !in content, "default text fell back to Times")
    }

    @Test
    fun `an explicit SansSerif font is sans-serif too, not Times`() {
        // PlantUML's PDF backend maps any name containing "serif" to Times, "SansSerif" included.
        val src = "@startuml\nskinparam defaultFontName \"SansSerif\"\ntitle Big title\nAlice -> Bob: hello\n@enduml\n"
        val content = contentOf(exportPdf(src))
        assertTrue("Helvetica" in content, "SansSerif is not Helvetica")
        assertTrue("Times" !in content, "SansSerif fell back to Times")
    }

    @Test
    fun `a font the diagram asks for still wins`() {
        val content = contentOf(exportPdf("@startuml\nskinparam defaultFontName Courier\nAlice -> Bob: hello\n@enduml\n"))
        assertTrue("Courier" in content, "the diagram's own font was overridden")
    }

    @Test
    fun `exports the chosen diagram of a multi-diagram file`() {
        val src = "@startuml\nAlice -> Bob: first\n@enduml\n\n@startuml\nclass Second\n@enduml\n"
        val second = contentOf(exportPdf(src, index = 1))
        assertTrue("Second" in second)
        assertTrue("first" !in second, "exported the first diagram instead of the second")
    }

    @Test
    fun `exports a C4 diagram from the bundled library`() {
        val src = """
            @startuml
            !include <C4/C4_Context>
            Person(user, "Customer")
            System(app, "Shop")
            Rel(user, app, "Buys")
            @enduml
        """.trimIndent()
        val content = contentOf(exportPdf(src))
        assertTrue("Customer" in content && "Shop" in content)
    }

    @Test
    fun `refuses remote includes like the other formats`() {
        val target = File.createTempFile("sprout", ".pdf").apply { deleteOnExit() }
        assertFailsWith<IllegalStateException> {
            runBlocking {
                PlantUmlEngine.export(
                    "@startuml\n!include https://example.com/x.puml\n@enduml\n", null, 0, ExportFormat.PDF, target,
                )
            }
        }
    }
}
