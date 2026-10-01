package es.hugoalvarezajenjo.sproutstudio.model

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import java.io.File

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

    fun save(target: File? = file): Boolean {
        val f = target ?: return false
        f.writeText(text)
        file = f
        savedText = text
        return true
    }

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

        const val NEW_TEMPLATE = "@startuml\n' 👋 Welcome! Start typing — suggestions pop up as you go.\n\nactor Me\nparticipant Idea\n\nMe -> Idea: let's draw!\nIdea --> Me: ✨\n@enduml\n"
    }
}
