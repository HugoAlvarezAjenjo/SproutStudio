package es.hugoalvarezajenjo.sproutstudio.services.git

import java.io.File

/** The work tree's top-level `.gitignore`, as a file: git reads it on the next status. */
object GitIgnore {

    /** The rule that ignores exactly [relPath] ("docs/a.puml"): anchored to the root, `/` for folders. */
    fun ruleFor(relPath: String, isDirectory: Boolean): String =
        "/" + relPath.replace('\\', '/').trim('/') + if (isDirectory) "/" else ""

    /**
     * Adds the rule for [relPath] to [workTree]'s `.gitignore` (creating it), on its own line.
     * Returns false when the rule is already there, so nothing is written twice.
     */
    fun add(workTree: File, relPath: String, isDirectory: Boolean): Boolean {
        val rule = ruleFor(relPath, isDirectory)
        val f = File(workTree, ".gitignore")
        val old = if (f.exists()) f.readText() else ""
        if (old.lineSequence().any { it.trim() == rule }) return false
        val sep = if (old.isEmpty() || old.endsWith("\n")) "" else "\n"
        f.writeText("$old$sep$rule\n")
        return true
    }
}
