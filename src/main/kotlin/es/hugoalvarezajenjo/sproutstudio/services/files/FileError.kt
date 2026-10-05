package es.hugoalvarezajenjo.sproutstudio.services.files

/** Why a file-tree operation was refused or failed. The UI words it. */
sealed interface FileError {
    data object EmptyName : FileError
    data object ReservedName : FileError
    /** `/ \ :` or control characters. */
    data object BadCharacters : FileError
    data class AlreadyExists(val name: String) : FileError
    data class AlreadyExistsIn(val name: String, val folder: String) : FileError
    data class NotAFolder(val name: String) : FileError
    data object IntoItself : FileError
    /** The project folder can't be renamed, moved or deleted from its own tree. */
    data object ProjectFolder : FileError
    data class TrashFailed(val name: String) : FileError
    data class Failed(val detail: String) : FileError
}
