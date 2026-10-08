package es.hugoalvarezajenjo.sproutstudio.services.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.io.File

/** [GitService] on JGit: in-process, no `git` binary needed on the user's machine. */
class JGitService : GitService {

    override fun find(dir: File): GitRepository? = runCatching {
        val b = FileRepositoryBuilder().readEnvironment().findGitDir(dir.absoluteFile)
        if (b.gitDir == null) return null
        val repo = b.setMustExist(true).build()
        if (repo.isBare) { repo.close(); null } else JGitRepository(repo)
    }.getOrNull()

    override fun init(dir: File, gitignore: Boolean): GitRepository {
        val g = jgit { Git.init().setDirectory(dir.absoluteFile).setInitialBranch("main").call() }
        val ig = File(dir, ".gitignore")
        if (gitignore && !ig.exists()) ig.writeText(DEFAULT_GITIGNORE)
        return JGitRepository(g.repository)
    }

    companion object {
        /** Litter that never belongs in a diagrams repo; exports are left for you to decide. */
        const val DEFAULT_GITIGNORE =
            "# macOS\n.DS_Store\n\n# SproutStudio's temp file while saving\n.*.sprout-tmp\n\n# IDEs\n.idea/\n.vscode/\n"
    }
}
