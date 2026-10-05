package es.hugoalvarezajenjo.sproutstudio.services.files

import java.io.File

/** One visible row of the project tree. */
data class TreeNode(val file: File, val depth: Int, val isDir: Boolean)
