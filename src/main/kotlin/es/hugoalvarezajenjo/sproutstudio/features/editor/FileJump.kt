package es.hugoalvarezajenjo.sproutstudio.features.editor

import java.io.File

/** A file Go to Declaration wants open, and what to select in it (null: just open it). */
data class FileJump(val file: File, val range: IntRange? = null)
