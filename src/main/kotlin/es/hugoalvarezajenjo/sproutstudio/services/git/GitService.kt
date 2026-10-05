package es.hugoalvarezajenjo.sproutstudio.services.git

import java.io.File

/** Finds and creates repositories. Blocking, like [GitRepository]. */
interface GitService {
    /** The repository [dir] belongs to (itself or a parent), or null if it isn't in one. */
    fun find(dir: File): GitRepository?

    /** `git init` in [dir], on branch main; with [gitignore], a starter .gitignore if there's none. */
    fun init(dir: File, gitignore: Boolean = false): GitRepository
}
