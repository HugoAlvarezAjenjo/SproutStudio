package es.hugoalvarezajenjo.sproutstudio.domain.session

/**
 * The tabs a project window had open, to reopen them next time: in tab order, with the one that
 * was showing. Paths are relative to the project folder, so a moved project keeps its session.
 */
data class ProjectSession(val tabs: List<OpenTab>, val active: Int) {

    /** A text form for the settings store: one line per field, the path last so it may hold tabs. */
    fun encode(): String = buildString {
        append(HEADER)
        append('\n').append(ACTIVE).append('\t').append(active)
        for (t in tabs) {
            append('\n').append(TAB).append('\t').append(t.selectionStart).append('\t').append(t.selectionEnd)
                .append('\t').append(t.path)
        }
    }

    /** The same session without its last tab (to fit a size limit); the active tab stays in range. */
    fun dropLast(): ProjectSession = ProjectSession(tabs.dropLast(1), active.coerceAtMost((tabs.size - 2).coerceAtLeast(0)))

    companion object {
        private const val HEADER = "v1"
        private const val ACTIVE = "active"
        private const val TAB = "tab"

        /** Null for anything this version didn't write: a newer format, a damaged value. */
        fun decode(s: String): ProjectSession? {
            val lines = s.split('\n')
            if (lines.firstOrNull() != HEADER) return null
            var active = 0
            val tabs = ArrayList<OpenTab>()
            for (line in lines.drop(1)) {
                val f = line.split('\t', limit = 4)
                when {
                    f[0] == ACTIVE && f.size == 2 -> active = f[1].toIntOrNull() ?: return null
                    f[0] == TAB && f.size == 4 && f[3].isNotEmpty() ->
                        tabs += OpenTab(f[3], f[1].toIntOrNull() ?: return null, f[2].toIntOrNull() ?: return null)
                    else -> return null
                }
            }
            return ProjectSession(tabs, active.coerceIn(0, (tabs.size - 1).coerceAtLeast(0)))
        }
    }
}

/** One reopened tab: its file, relative to the project folder, and the selection it had. */
data class OpenTab(val path: String, val selectionStart: Int, val selectionEnd: Int)
