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
 * `sproutstudio --export` and `--check`: go through every diagram of the given files and folders,
 * with no window. Both validate the same way; `--export` then writes the valid ones.
 *
 * - A diagram with errors is reported as `path:line: message`, like a compiler; the rest go on.
 * - Folders are searched the way the project tree is (no hidden or build folders); files there
 *   without a diagram (include files) are skipped quietly.
 * - Export: one diagram in a file → `name.svg`; several → `name-1.svg`, `name-2.svg`, numbered as
 *   the preview numbers them.
 *
 * Progress goes to [out], problems to [err]. Each [run] counts on its own and returns the exit code.
 */
class Batch(
    private val out: PrintStream = System.out,
    private val err: PrintStream = System.err,
    private val workingDir: File = File("").absoluteFile,
) {

    /** One file to go through, and the folder its images go to. [explicit]: named on the command line. */
    private data class Job(val file: File, val outDir: File, val explicit: Boolean)

    /** A file's diagrams that passed validation, by index, with what [PlantUmlEngine] needs to draw them. */
    private class Valid(val text: String, val count: Int, val indices: List<Int>)

    /** Diagrams that went through (exported, or valid) and diagrams that didn't, in one run. */
    private class Tally {
        var passed = 0
        var failed = 0
    }

    fun run(cmd: CommandLine.Export): ExitCode {
        val jobs = jobsFor(cmd.inputs, cmd.out) ?: return ExitCode.USAGE
        val t = Tally()
        jobs.forEach { job -> validate(job, t)?.let { export(job, it, cmd.format, t) } }
        return finish(t, if (t.failed > 0) "Exported ${t.passed}, failed ${t.failed}." else "Exported ${t.passed}.")
    }

    fun run(cmd: CommandLine.Check): ExitCode {
        val jobs = jobsFor(cmd.inputs, out = null) ?: return ExitCode.USAGE
        val t = Tally()
        jobs.forEach { validate(it, t)?.let { v -> t.passed += v.indices.size } }
        return finish(t, if (t.failed > 0) "${t.passed} OK, ${t.failed} with errors." else "${t.passed} OK.")
    }

    private fun finish(t: Tally, summary: String): ExitCode {
        if (t.passed == 0 && t.failed == 0) err.println("No diagrams found.") else out.println(summary)
        return if (t.failed > 0 || t.passed == 0) ExitCode.FAILED else ExitCode.OK
    }

    private fun jobsFor(inputs: List<File>, out: File?): List<Job>? {
        val missing = inputs.filterNot { it.exists() }
        if (missing.isNotEmpty()) {
            missing.forEach { err.println("Not found: ${it.path}") }
            return null
        }
        return inputs.flatMap { input ->
            val f = input.absoluteFile
            if (f.isFile) listOf(Job(f, out?.absoluteFile ?: f.parentFile, explicit = true))
            else diagramsUnder(f).map { d ->
                // Under --out the folders below the input are kept, so two `a.puml` don't collide.
                val dir = out?.absoluteFile?.resolve(d.parentFile.relativeTo(f).path) ?: d.parentFile
                Job(d, dir, explicit = false)
            }
        }
    }

    /** Reports every broken diagram of [job]'s file (counted in [t]); null when none is left. */
    private fun validate(job: Job, t: Tally): Valid? {
        val f = job.file
        val text = f.readText()
        val blocks = DiagramBlocks.of(text)
        if (blocks.isEmpty()) {
            if (job.explicit) fail(t, "${shown(f)}: no @start… @end… diagram")
            return null
        }
        val problems = PlantUmlEngine.collectProblemsBlocking(text, f.parentFile)
        // A remote include stops the whole file: one message, every diagram in it failed.
        problems.firstOrNull { it.kind == RenderError.Kind.OFFLINE_INCLUDE }?.let {
            err.println("${shown(f)}:${it.line}: ${messageOf(it)}")
            t.failed += blocks.size
            return null
        }
        val valid = blocks.indices.filter { i ->
            val broken = problems.filter { it.diagramIndex == i }
            broken.forEach { err.println("${shown(f)}:${it.line ?: blocks[i].startLine + 1}: ${messageOf(it)}") }
            if (broken.isNotEmpty()) t.failed++ // one diagram, however many errors it has
            broken.isEmpty()
        }
        return Valid(text, blocks.size, valid).takeIf { valid.isNotEmpty() }
    }

    private fun export(job: Job, valid: Valid, format: ExportFormat, t: Tally) {
        val f = job.file
        valid.indices.forEach { i ->
            val name = DiagramBlocks.exportName(f.nameWithoutExtension, i, valid.count)
            val target = job.outDir.resolve("$name.${format.extension}")
            runCatching {
                job.outDir.mkdirs()
                PlantUmlEngine.export(valid.text, f.parentFile, i, format, target)
            }.onSuccess {
                t.passed++
                out.println("${shown(f)} → ${shown(target)}")
            }.onFailure {
                fail(t, "${shown(f)}: diagram ${i + 1}: ${it.message ?: it.javaClass.simpleName}")
            }
        }
    }

    /** A diagram that didn't pass, with why. */
    private fun fail(t: Tally, message: String) {
        t.failed++
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
}
