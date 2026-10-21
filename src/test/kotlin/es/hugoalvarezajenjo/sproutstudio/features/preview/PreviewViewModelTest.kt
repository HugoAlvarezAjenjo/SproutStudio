package es.hugoalvarezajenjo.sproutstudio.features.preview

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import es.hugoalvarezajenjo.sproutstudio.core.concurrency.AppDispatchers
import es.hugoalvarezajenjo.sproutstudio.core.settings.InMemorySettingsStore
import es.hugoalvarezajenjo.sproutstudio.services.render.ExportFormat
import es.hugoalvarezajenjo.sproutstudio.services.render.PreviewRequest
import es.hugoalvarezajenjo.sproutstudio.services.render.Problem
import es.hugoalvarezajenjo.sproutstudio.services.render.RenderResult
import es.hugoalvarezajenjo.sproutstudio.services.render.RenderService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class PreviewViewModelTest {

    /** Records what was asked of it; problems come back as one per "!" in the text. */
    private class FakeRender : RenderService {
        val previews = mutableListOf<PreviewRequest>()
        override suspend fun preview(key: Any, request: PreviewRequest): RenderResult {
            previews += request
            return RenderResult(null, diagramCount = 2, index = request.index.coerceAtMost(1), error = null)
        }
        override suspend fun problems(key: Any, source: String, baseDir: File?) =
            source.withIndex().filter { it.value == '!' }.map { Problem(it.index + 1, "bang", 0) }
        override suspend fun thumbnail(source: String, dark: Boolean): ByteArray? = null
        var exportFails = false
        override suspend fun export(source: String, baseDir: File?, index: Int, format: ExportFormat, target: File) {
            if (exportFails) throw java.io.IOException("disk full")
        }
        override suspend fun png(source: String, baseDir: File?, index: Int) = ByteArray(0)
    }

    private val text = mutableStateOf("@startuml\n@enduml")
    private val render = FakeRender()
    private val diagram = DiagramSettings(InMemorySettingsStore())

    private fun TestScope.viewModel(): PreviewViewModel {
        val d = StandardTestDispatcher(testScheduler)
        return PreviewViewModel(render, diagram, es.hugoalvarezajenjo.sproutstudio.platform.Platform.current(), AppDispatchers(d, d, d), debounceMs = 300) {
            PreviewViewModel.Source(text.value, null)
        }
    }

    /** Outside a running UI, snapshot changes have to be announced by hand. */
    private fun TestScope.change(block: () -> Unit) {
        block()
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    @Test
    fun exportReportsWhatHappened() = runTest {
        val vm = viewModel()
        runCurrent()
        val target = File("out.svg")
        vm.exportTo(ExportFormat.SVG, target)
        runCurrent()
        assertEquals(PreviewNotice.Saved(target), vm.notice)
        render.exportFails = true
        vm.exportTo(ExportFormat.SVG, target)
        runCurrent()
        assertEquals("disk full", (vm.notice as PreviewNotice.ExportFailed).cause.message)
        vm.clearNotice()
        assertEquals(null, vm.notice)
        vm.close()
    }

    @Test
    fun exportNameNumbersDiagramsOfAMultiDiagramFile() = runTest {
        val vm = viewModel()
        runCurrent() // the fake reports two diagrams
        assertEquals("pizza-1", vm.exportName("pizza"))
        vm.close()
    }

    @Test
    fun firstRenderIsImmediate() = runTest {
        val vm = viewModel()
        runCurrent()
        assertEquals(1, render.previews.size)
        assertEquals(true, vm.firstDone)
        vm.close()
    }

    @Test
    fun typingFastRendersOnlyTheTextYouStoppedOn() = runTest {
        val vm = viewModel()
        runCurrent()
        for (i in 1..5) {
            change { text.value = "@startuml\nA -> B$i\n@enduml" }
            advanceTimeBy(100) // faster than the debounce
        }
        advanceUntilIdle()
        assertEquals(listOf("@startuml\n@enduml", "@startuml\nA -> B5\n@enduml"), render.previews.map { it.source })
        vm.close()
    }

    @Test
    fun problemsFollowTheLatestText() = runTest {
        val vm = viewModel()
        runCurrent()
        change { text.value = "!" }
        advanceUntilIdle()
        assertEquals(1, vm.problems.size)
        change { text.value = "!!!" }
        advanceUntilIdle()
        assertEquals(3, vm.problems.size)
        vm.close()
    }

    @Test
    fun switchingDiagramOrColourSkipsTheDebounce() = runTest {
        val vm = viewModel()
        runCurrent()
        change { vm.nextDiagram() }
        assertEquals(1, render.previews.last().index, "rendered without waiting")
        change { diagram.toggle() }
        assertEquals(true, render.previews.last().dark)
        vm.close()
    }

    @Test
    fun closingStopsRendering() = runTest {
        val vm = viewModel()
        runCurrent()
        vm.close()
        change { text.value = "@startuml\nchanged\n@enduml" }
        advanceUntilIdle()
        assertEquals(1, render.previews.size)
    }
}
