package es.hugoalvarezajenjo.sproutstudio.features.preview

import androidx.compose.runtime.MutableState
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

    private fun TestScope.viewModel(pin: MutableState<Int?> = mutableStateOf(null)): PreviewViewModel {
        val d = StandardTestDispatcher(testScheduler)
        return PreviewViewModel(render, diagram, es.hugoalvarezajenjo.sproutstudio.platform.Platform.current(), AppDispatchers(d, d, d), debounceMs = 300, pin = pin) {
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

    /** Lines 1-3 are diagram 1, line 4 is outside both, lines 5-7 are diagram 2. */
    private val twoDiagrams = "@startuml\nA -> B\n@enduml\n\n@startuml\nC -> D\n@enduml"

    @Test
    fun previewFollowsTheCaretToItsDiagram() = runTest {
        text.value = twoDiagrams
        val vm = viewModel()
        runCurrent()
        change { vm.followCaret(6) }
        assertEquals(1, vm.index)
        assertEquals(1, render.previews.last().index, "rendered without waiting")
        change { vm.followCaret(4) } // between the two: the shown one stays
        assertEquals(1, vm.index)
        change { vm.followCaret(1) }
        assertEquals(0, vm.index)
        vm.close()
    }

    @Test
    fun aPinnedDiagramStaysWhereverTheCaretGoes() = runTest {
        text.value = twoDiagrams
        val pin = mutableStateOf<Int?>(null)
        val vm = viewModel(pin)
        runCurrent()
        vm.togglePin()
        assertEquals(true, vm.pinned)
        assertEquals(0, pin.value)
        change { vm.followCaret(6) }
        assertEquals(0, vm.index)
        // The arrows are asked for on purpose: they move the pin.
        change { vm.nextDiagram() }
        assertEquals(1, vm.index)
        assertEquals(1, pin.value)
        vm.togglePin()
        assertEquals(null, pin.value)
        change { vm.followCaret(1) }
        assertEquals(0, vm.index)
        vm.close()
    }

    @Test
    fun aNewPreviewStartsOnThePinnedDiagram() = runTest {
        text.value = twoDiagrams
        val vm = viewModel(mutableStateOf(1)) // the tab comes back to the front
        runCurrent()
        assertEquals(1, vm.index)
        assertEquals(1, render.previews.single().index)
        change { vm.followCaret(1) }
        assertEquals(1, vm.index)
        vm.close()
    }

    @Test
    fun aPinPastTheLastDiagramMovesToIt() = runTest {
        text.value = twoDiagrams
        val pin = mutableStateOf<Int?>(5) // its diagram was deleted
        val vm = viewModel(pin)
        runCurrent() // the fake has two diagrams and clamps the index to 1
        assertEquals(1, vm.index)
        assertEquals(1, pin.value)
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
