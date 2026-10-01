package es.hugoalvarezajenjo.sproutstudio.model

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

val DiagramExtensions = setOf("puml", "plantuml", "pu", "iuml", "wsd")

fun File.isDiagram() = isFile && extension.lowercase() in DiagramExtensions

/** One open file (or an unsaved new diagram). */
@Stable
class Document(file: File?, initialText: String) {
    var file by mutableStateOf(file)
    var value by mutableStateOf(TextFieldValue(initialText))
    var savedText by mutableStateOf(initialText)
        private set

    /** Line the editor should scroll to and select; consumed by the editor. */
    var jumpRequest by mutableStateOf<Int?>(null)

    val text: String get() = value.text
    val dirty: Boolean get() = text != savedText
    val name: String get() = file?.name ?: "Untitled.puml"
    val baseName: String get() = name.substringBeforeLast('.')
    val dir: File? get() = file?.parentFile

    /** Why the last save failed (disk full, read-only...); null once a save succeeds. */
    var saveError by mutableStateOf<String?>(null)
        private set

    /** Writes to [target]; on failure keeps the edits dirty and records [saveError]. */
    fun save(target: File? = file): Boolean {
        val f = target ?: return false
        val snapshot = text
        try {
            writeAtomically(f, snapshot)
        } catch (e: IOException) {
            saveError = e.message ?: e.javaClass.simpleName
            return false
        }
        file = f
        savedText = snapshot
        saveError = null
        return true
    }

    /** Autosave entry point: only file-backed documents with edits; untitled ones need Save As. */
    fun saveIfNeeded(): Boolean = file != null && dirty && save()

    /** Reload from disk when the file changed outside and we have no unsaved edits. */
    fun reloadIfChanged(): Boolean {
        val f = file ?: return false
        if (dirty || !f.exists()) return false
        val disk = f.readText()
        if (disk == savedText) return false
        value = TextFieldValue(disk, value.selection.let { if (it.end > disk.length) androidx.compose.ui.text.TextRange(disk.length) else it })
        savedText = disk
        return true
    }

    companion object {
        fun open(file: File) = Document(file, file.readText())

        /**
         * Write via a temp file in the same folder + rename, so a crash or a full disk mid-write
         * (more likely now that we save every second) never leaves a half-written diagram.
         */
        fun writeAtomically(f: File, content: String) {
            val dir = f.absoluteFile.parentFile ?: throw IOException("No folder for ${f.name}")
            val tmp = File(dir, ".${f.name}.sprout-tmp")
            try {
                tmp.writeText(content)
                try {
                    Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                tmp.delete()
            }
        }

        const val NEW_TEMPLATE = "@startuml\n' 👋 Welcome! Start typing — suggestions pop up as you go.\n\nactor Me\nparticipant Idea\n\nMe -> Idea: let's draw!\nIdea --> Me: ✨\n@enduml\n"
    }
}
