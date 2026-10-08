package es.hugoalvarezajenjo.sproutstudio.features.preview

import androidx.compose.runtime.getValue

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import es.hugoalvarezajenjo.sproutstudio.features.workspace.WorkspaceDeps
import es.hugoalvarezajenjo.sproutstudio.features.preview.PreviewViewModel
import java.io.File

/**
 * A [PreviewViewModel] that follows [text], owned by the composition: created with it, closed
 * when it leaves. For the preview of a document tab and of the quick-preview and diff windows.
 */
@Composable
fun rememberPreviewViewModel(deps: WorkspaceDeps, text: String, baseDir: File?, debounceMs: Long = 300): PreviewViewModel {
    val src = remember { mutableStateOf(PreviewViewModel.Source(text, baseDir)) }
    SideEffect { src.value = PreviewViewModel.Source(text, baseDir) }
    val vm = remember { PreviewViewModel(deps.render, deps.diagram, deps.platform, deps.dispatchers, debounceMs = debounceMs) { src.value } }
    DisposableEffect(vm) { onDispose { vm.close() } }
    return vm
}
