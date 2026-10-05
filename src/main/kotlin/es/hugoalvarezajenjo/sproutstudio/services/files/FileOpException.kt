package es.hugoalvarezajenjo.sproutstudio.services.files

/** Thrown by [ProjectFiles] with the reason. */
class FileOpException(val error: FileError) : RuntimeException(error.toString())
