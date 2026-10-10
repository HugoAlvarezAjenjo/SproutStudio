package es.hugoalvarezajenjo.sproutstudio.features.search

import es.hugoalvarezajenjo.sproutstudio.core.concurrency.AppDispatchers
import es.hugoalvarezajenjo.sproutstudio.domain.text.FindQuery
import es.hugoalvarezajenjo.sproutstudio.services.files.ProjectFiles
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val dir = createTempDirectory("search").toFile()
    private val open = mutableMapOf<File, String>()
    private var root: File? = dir

    @AfterTest fun cleanUp() { dir.deleteRecursively() }

    private fun file(path: String, text: String) = File(dir, path).apply { parentFile.mkdirs(); writeText(text) }.absoluteFile

    private fun TestScope.vm(): SearchViewModel {
        val d = StandardTestDispatcher(testScheduler)
        return SearchViewModel(ProjectFiles { true }, AppDispatchers(d, d, d), { root }, { open.toMap() })
    }

    @Test
    fun searchesEveryDiagramInTheProjectButNotOtherFiles() = runTest {
        file("a.puml", "Order")
        file("sub/b.iuml", "order")
        file("notes.txt", "order")
        val vm = vm()
        vm.changeQuery(FindQuery("order"))
        advanceUntilIdle()
        assertEquals(listOf("a.puml", "b.iuml"), vm.result.files.map { File(it.path).name })
        vm.close()
    }

    @Test
    fun anOpenTabIsSearchedWithItsUnsavedText() = runTest {
        val a = file("a.puml", "old text")
        open[a] = "new Order text"
        val vm = vm()
        vm.changeQuery(FindQuery("Order"))
        advanceUntilIdle()
        val hit = vm.result.files.single().hits.single()
        assertEquals("Order", open.getValue(a).substring(hit.range))
        vm.close()
    }

    @Test
    fun typingWaitsForAPauseAndOnlyTheLastQueryRuns() = runTest {
        file("a.puml", "ab abc")
        val vm = vm()
        vm.changeQuery(FindQuery("a"))
        advanceTimeBy(SearchViewModel.DEBOUNCE_MS / 2)
        vm.changeQuery(FindQuery("abc"))
        advanceTimeBy(SearchViewModel.DEBOUNCE_MS / 2)
        runCurrent()
        assertTrue(vm.result.files.isEmpty(), "nothing runs before the pause")
        advanceUntilIdle()
        assertEquals(1, vm.result.hitCount)
        vm.close()
    }

    @Test
    fun clearingTheQueryOrClosingTheProjectEmptiesTheList() = runTest {
        file("a.puml", "x")
        val vm = vm()
        vm.changeQuery(FindQuery("x"))
        advanceUntilIdle()
        assertEquals(1, vm.result.hitCount)
        vm.changeQuery(FindQuery(""))
        assertTrue(vm.result.files.isEmpty())

        vm.changeQuery(FindQuery("x"))
        advanceUntilIdle()
        root = null
        vm.refresh()
        assertTrue(vm.result.files.isEmpty())
        vm.close()
    }

    @Test
    fun openSeedsTheQueryFromASingleLineSelection() = runTest {
        file("a.puml", "Order")
        val vm = vm()
        vm.open("Order")
        advanceUntilIdle()
        assertEquals("Order", vm.query.text)
        assertEquals(1, vm.result.hitCount)
        vm.open("two\nlines")
        assertEquals("Order", vm.query.text)
        vm.close()
    }
}
