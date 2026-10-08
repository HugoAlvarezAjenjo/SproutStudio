package es.hugoalvarezajenjo.sproutstudio.features.explorer

import es.hugoalvarezajenjo.sproutstudio.services.files.FileError

/** The words for file-tree refusals, ready to move to string resources. */
fun FileError.describe(): String = when (this) {
    FileError.EmptyName -> "Enter a name"
    FileError.ReservedName -> "That name is reserved"
    FileError.BadCharacters -> "A name can't contain / \\ or :"
    is FileError.AlreadyExists -> "“$name” already exists here"
    is FileError.AlreadyExistsIn -> "“$name” already exists in $folder"
    is FileError.NotAFolder -> "“$name” is not a folder"
    FileError.IntoItself -> "A folder can't be moved into itself"
    FileError.ProjectFolder -> "The project folder itself can't be changed here"
    is FileError.TrashFailed -> "Couldn't move “$name” to the Trash"
    is FileError.Failed -> detail
}
