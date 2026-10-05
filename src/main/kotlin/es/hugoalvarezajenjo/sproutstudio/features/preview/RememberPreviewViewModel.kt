package es.hugoalvarezajenjo.sproutstudio.features.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import es.hugoalvarezajenjo.sproutstudio.app.AppGraph
import es.hugoalvarezajenjo.sproutstudio.features.preview.PreviewViewModel
import java.io.File

/**
 * Bridge for screens that don't have a ViewModel yet: a [PreviewViewModel] that follows
 * [text], closed when it leaves the composition. Goes away once those screens own theirs.
 */
@Composable
fun rememberPreviewViewModel(text: String, baseDir: File?, debounceMs: Long = 300): PreviewViewModel {
    val src = remember { mutableStateOf(PreviewViewModel.Source(text, baseDir)) }
    SideEffect { src.value = PreviewViewModel.Source(text, baseDir) }
    val vm = remember {
        val g = AppGraph.default
        PreviewViewModel(g.render, g.diagram, g.dispatchers, debounceMs = debounceMs) { src.value }
    }
    DisposableEffect(vm) { onDispose { vm.close() } }
    return vm
}
