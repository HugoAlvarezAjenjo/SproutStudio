package es.hugoalvarezajenjo.sproutstudio.features.explorer

import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.ferr_bad_chars
import es.hugoalvarezajenjo.sproutstudio.resources.ferr_empty
import es.hugoalvarezajenjo.sproutstudio.resources.ferr_exists
import es.hugoalvarezajenjo.sproutstudio.resources.ferr_exists_in
import es.hugoalvarezajenjo.sproutstudio.resources.ferr_into_itself
import es.hugoalvarezajenjo.sproutstudio.resources.ferr_not_folder
import es.hugoalvarezajenjo.sproutstudio.resources.ferr_project_folder
import es.hugoalvarezajenjo.sproutstudio.resources.ferr_reserved
import es.hugoalvarezajenjo.sproutstudio.resources.ferr_trash_failed
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.text

import es.hugoalvarezajenjo.sproutstudio.services.files.FileError

/** The words for file-tree refusals, translated through strings.xml. */
fun FileError.describe(): String = when (this) {
    FileError.EmptyName -> text(Res.string.ferr_empty)
    FileError.ReservedName -> text(Res.string.ferr_reserved)
    FileError.BadCharacters -> text(Res.string.ferr_bad_chars)
    is FileError.AlreadyExists -> text(Res.string.ferr_exists, name)
    is FileError.AlreadyExistsIn -> text(Res.string.ferr_exists_in, name, folder)
    is FileError.NotAFolder -> text(Res.string.ferr_not_folder, name)
    FileError.IntoItself -> text(Res.string.ferr_into_itself)
    FileError.ProjectFolder -> text(Res.string.ferr_project_folder)
    is FileError.TrashFailed -> text(Res.string.ferr_trash_failed, name)
    is FileError.Failed -> detail
}
