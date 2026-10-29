package es.hugoalvarezajenjo.sproutstudio.features.outline

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TableRows
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.ui.graphics.vector.ImageVector
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.SymbolIndex

/** The icon for a symbol's [kind] (the keyword that declared it, see `Symbol.kind`). */
internal fun outlineIcon(kind: String): ImageVector = when (kind) {
    "actor" -> Icons.Outlined.Person
    "database", "collections", "storage" -> Icons.Outlined.Storage
    "queue" -> Icons.Outlined.TableRows
    "class", "abstract class", "interface", "enum", "annotation", "struct", "object", "entity" -> Icons.Outlined.DataObject
    "package", "namespace", "folder", "frame", "rectangle" -> Icons.Outlined.Folder
    "node" -> Icons.Outlined.Dns
    "cloud" -> Icons.Outlined.Cloud
    "component", "artifact" -> Icons.Outlined.Widgets
    "usecase" -> Icons.Outlined.RadioButtonUnchecked
    "state" -> Icons.Outlined.AccountTree
    "procedure", "function" -> Icons.Outlined.Functions
    SymbolIndex.MACRO, SymbolIndex.VARIABLE -> Icons.Outlined.Code
    else -> Icons.Outlined.CropSquare // participant, boundary, control, and names only used
}
