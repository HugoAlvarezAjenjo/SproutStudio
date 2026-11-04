package es.hugoalvarezajenjo.sproutstudio.app

import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.DiagramBlocks
import es.hugoalvarezajenjo.sproutstudio.services.files.ProjectFiles
import es.hugoalvarezajenjo.sproutstudio.services.render.ExportFormat
import es.hugoalvarezajenjo.sproutstudio.services.render.PlantUmlEngine
import es.hugoalvarezajenjo.sproutstudio.services.render.Problem
import es.hugoalvarezajenjo.sproutstudio.services.render.RenderError
import java.io.File
import java.io.PrintStream

/**
 * `sproutstudio --export`: writes every diagram of the given files and folders, with no window.
 *
 * - One diagram in a file → `name.svg`; several → `name-1.svg`, `name-2.svg`, numbered as the
 *   preview numbers them.
 * - A diagram with errors is reported as `path:line: message`, like a compiler, and skipped; the
 *   rest are still exported.
 * - Folders are searched the way the project tree is (no hidden or build folders); files there
 *   without a diagram (include files) are skipped quietly.
 *
 * Progress goes to [out], problems to [err]. [run] returns the process exit code.
 */
class BatchExport(
    private val out: PrintStream = System.out,
    private val err: PrintStream = System.err,
    private val workingDir: File = File("").absoluteFile,
) {

    /** One file to export, and the folder its images go to. [explicit]: named on the command line. */
    private data class Job(val file: File, val outDir: File, val explicit: Boolean)

    private var exported = 0
    private var failed = 0

    fun run(cmd: CommandLine.Export): Int {
        val jobs = jobsFor(cmd) ?: return EXIT_USAGE
        jobs.forEach { export(it, cmd.format) }
        when {
            failed > 0 -> out.println("Exported $exported, failed $failed.")
            exported == 0 -> err.println("No diagrams found.")
            else -> out.println("Exported $exported.")
        }
        return if (failed > 0 || exported == 0) EXIT_FAILED else EXIT_OK
    }

    private fun jobsFor(cmd: CommandLine.Export): List<Job>? {
        val missing = cmd.inputs.filterNot { it.exists() }
        if (missing.isNotEmpty()) {
            missing.forEach { err.println("Not found: ${it.path}") }
            return null
        }
        return cmd.inputs.flatMap { input ->
            val f = input.absoluteFile
            if (f.isFile) listOf(Job(f, cmd.out?.absoluteFile ?: f.parentFile, explicit = true))
            else diagramsUnder(f).map { d ->
                // Under --out the folders below the input are kept, so two `a.puml` don't collide.
                val dir = cmd.out?.absoluteFile?.resolve(d.parentFile.relativeTo(f).path) ?: d.parentFile
                Job(d, dir, explicit = false)
            }
        }
    }

    private fun export(job: Job, format: ExportFormat) {
        val f = job.file
        val text = f.readText()
        val blocks = DiagramBlocks.of(text)
        if (blocks.isEmpty()) {
            if (job.explicit) fail("${shown(f)}: no @start… @end… diagram")
            return
        }
        val problems = PlantUmlEngine.collectProblemsBlocking(text, f.parentFile)
        // A remote include stops the whole file: one message, every diagram in it failed.
        problems.firstOrNull { it.kind == RenderError.Kind.OFFLINE_INCLUDE }?.let {
            err.println("${shown(f)}:${it.line}: ${messageOf(it)}")
            failed += blocks.size
            return
        }
        blocks.indices.forEach { i ->
            val broken = problems.filter { it.diagramIndex == i }
            if (broken.isNotEmpty()) {
                broken.forEach { err.println("${shown(f)}:${it.line ?: blocks[i].startLine + 1}: ${messageOf(it)}") }
                failed++ // one diagram, however many errors it has
                return@forEach
            }
            val name = if (blocks.size == 1) f.nameWithoutExtension else "${f.nameWithoutExtension}-${i + 1}"
            val target = job.outDir.resolve("$name.${format.extension}")
            runCatching {
                job.outDir.mkdirs()
                PlantUmlEngine.export(text, f.parentFile, i, format, target)
            }.onSuccess {
                exported++
                out.println("${shown(f)} → ${shown(target)}")
            }.onFailure {
                fail("${shown(f)}: diagram ${i + 1}: ${it.message ?: it.javaClass.simpleName}")
            }
        }
    }

    /** A diagram that couldn't be exported, with why. */
    private fun fail(message: String) {
        failed++
        err.println(message)
    }

    private fun messageOf(p: Problem): String = when {
        p.kind == RenderError.Kind.OFFLINE_INCLUDE -> "remote !include refused: SproutStudio works offline"
        p.message.isBlank() -> "syntax error"
        else -> p.message
    }

    /** Relative to where the command runs, when that's shorter: what the user typed. */
    private fun shown(f: File): String =
        f.relativeToOrNull(workingDir)?.path?.takeUnless { it.startsWith("..") } ?: f.path

    /** The project tree's own search: skips hidden, build and dependency folders. */
    private fun diagramsUnder(dir: File): List<File> = ProjectFiles(trash = { false }).allDiagrams(dir, limit = Int.MAX_VALUE)

    companion object {
        const val EXIT_OK = 0
        const val EXIT_FAILED = 1
        const val EXIT_USAGE = 2
    }
}
