package es.hugoalvarezajenjo.sproutstudio.features.git

import es.hugoalvarezajenjo.sproutstudio.core.concurrency.AppDispatchers
import es.hugoalvarezajenjo.sproutstudio.services.git.CommitInfo
import es.hugoalvarezajenjo.sproutstudio.services.git.GitAuthor
import es.hugoalvarezajenjo.sproutstudio.services.git.GitRepository
import es.hugoalvarezajenjo.sproutstudio.services.git.GitService
import es.hugoalvarezajenjo.sproutstudio.services.git.GitStatus
import es.hugoalvarezajenjo.sproutstudio.services.git.StashInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import androidx.compose.runtime.snapshots.Snapshot
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Ordering guarantees of the git ViewModel. The first two are guards: the single git thread
 * already kept these in order before the refactor, and the lock + generation keep them so if
 * that thread ever changes. The HEAD-text one was a real bug (text cached under the wrong commit).
 */
class GitViewModelTest {

    /** A repository whose status() can be held until a latch opens. */
    private class FakeRepo(val name: String) : GitRepository {
        var head = "c1"
        @Volatile var current = name
        @Volatile var branchList = listOf(name)
        @Volatile var hold: CountDownLatch? = null
        @Volatile var statusCalls = 0
        val texts = mutableMapOf<String, String>()
        override val workTree = File("/tmp/$name")
        override fun relPath(f: File) = f.name
        override fun branch() = current
        override fun headId() = head
        override fun status(scope: File?): GitStatus {
            statusCalls++
            val h = head
            hold?.await()
            return GitStatus(current, h, emptyList(), emptySet())
        }
        override fun headText(f: File) = texts[head]
        override fun textAt(rev: String, f: File) = texts[rev]
        override fun author() = GitAuthor("a", "a@b", false)
        override fun commit(files: List<File>, message: String, amend: Boolean) = "x"
        override fun rollback(f: File) = Unit
        override fun lastCommit(): CommitInfo? = null
        override fun undoLastCommit() = error("unused")
        override fun history(f: File, max: Int) = emptyList<CommitInfo>()
        override fun stash(message: String?) = false
        override fun stashes() = emptyList<StashInfo>()
        override fun popStash(index: Int) = Unit
        override fun dropStash(index: Int) = Unit
        override fun branches() = branchList
        override fun checkout(name: String) = Unit
        override fun createBranch(name: String) = Unit
        override fun deleteBranch(name: String) = Unit
        override fun close() = Unit
    }

    private val repos = mutableMapOf<String, FakeRepo>()
    private val service = object : GitService {
        override fun find(dir: File) = repos[dir.name]
        override fun init(dir: File, gitignore: Boolean) = error("unused")
    }
    private val vm = GitViewModel(service, AppDispatchers(Dispatchers.Unconfined, Dispatchers.IO, Dispatchers.Default), refreshDelayMs = 0)

    @AfterTest fun close() = vm.close()

    @Test
    fun aStatusReadForAClosedFolderNeverLands() = runBlocking {
        withTimeout(5000) {
            val a = FakeRepo("a").also { repos["a"] = it }
            vm.open(File("/x/a"))
            assertEquals("a", vm.status.branch)

            // A refresh of "a" is slow on the disk...
            val gate = CountDownLatch(1)
            a.hold = gate
            val calls = a.statusCalls
            val slow = launch(Dispatchers.Default) { vm.refresh() }
            while (a.statusCalls == calls) yield()
            // ...and meanwhile the project is closed.
            val closing = launch(Dispatchers.Default) { vm.open(null) }
            delay(200) // let the close run as far as it can while the read is stuck
            gate.countDown()
            slow.join(); closing.join()

            assertNull(vm.repo)
            assertEquals(GitStatus.Empty, vm.status, "the old folder's status must not come back")
        }
    }

    @Test
    fun overlappingRefreshesEndOnTheNewestState() = runBlocking {
        withTimeout(5000) {
            val a = FakeRepo("a").also { repos["a"] = it }
            vm.open(File("/x/a"))
            // First refresh reads HEAD c1 and stalls; a commit then moves HEAD to c2.
            val gate = CountDownLatch(1)
            a.hold = gate
            val calls = a.statusCalls
            val first = launch(Dispatchers.Default) { vm.refresh() }
            while (a.statusCalls == calls) yield()
            a.head = "c2"
            a.hold = null
            val second = launch(Dispatchers.Default) { vm.refresh() }
            delay(200) // the newer read would finish first
            gate.countDown()
            first.join(); second.join()
            assertEquals("c2", vm.status.head, "a slow, older read must not overwrite a newer one")
        }
    }

    @Test
    fun aRefreshNeverShowsTheNewBranchBeforeTheBranchList() = runBlocking {
        val a = FakeRepo("a").also { repos["a"] = it }
        vm.open(File("/x/a"))
        // A branch is created and checked out: status and branch list both change.
        a.current = "new"
        a.branchList = listOf("a", "new")
        // What anyone reading the ViewModel could see at each state write that becomes visible.
        val seen = mutableListOf<Pair<String?, List<String>>>()
        val observer = Snapshot.registerGlobalWriteObserver { seen += vm.status.branch to vm.branches }
        try {
            vm.refresh()
        } finally {
            observer.dispose()
        }
        assertEquals("new", vm.status.branch)
        assertEquals(listOf("a", "new"), vm.branches)
        val torn = seen.filter { (branch, list) -> branch == "new" && "new" !in list }
        assertEquals(emptyList(), torn, "the status showed the new branch while the list was still the old one")
    }

    @Test
    fun headTextIsReadAtTheCommitTheStatusShows() = runBlocking {
        val a = FakeRepo("a").also { repos["a"] = it }
        a.texts["c1"] = "old"
        a.texts["c2"] = "new"
        vm.open(File("/x/a"))
        // HEAD moves on disk before the status is re-read: the gutter still compares with c1.
        a.head = "c2"
        val seen = AtomicReference<String?>()
        seen.set(vm.headText(File("/x/a/d.puml")))
        assertEquals("old", seen.get())
        vm.refresh()
        assertEquals("new", vm.headText(File("/x/a/d.puml")))
    }
}
