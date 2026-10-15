package es.hugoalvarezajenjo.sproutstudio

import java.io.File

/** Settings for a throwaway test repo, so tests don't depend on the machine's git config. */
object TestRepos {
    /**
     * Writes a fixed author, no signing and `autocrlf = false` into [dir]'s `.git/config`.
     * Without the last one, JGit picks up the system `core.autocrlf = true` that Git for Windows
     * (and the GitHub Windows runner) sets, and every checkout, rollback or stash pop writes `\r\n`.
     */
    fun configure(dir: File, name: String = "Test", email: String = "test@example.com") {
        File(dir, ".git/config").appendText(
            "[user]\n\tname = $name\n\temail = $email\n[commit]\n\tgpgsign = false\n[core]\n\tautocrlf = false\n",
        )
    }
}
