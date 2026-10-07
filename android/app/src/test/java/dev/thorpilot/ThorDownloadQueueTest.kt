package dev.thorpilot

import org.junit.Assert.*
import org.junit.Test

class ThorDownloadQueueTest {
    private val tree="content://com.android.externalstorage.documents/tree/4A21-0000%3AROMs"
    @Test fun fifoFillsOnlyAvailableSlots() {
        assertEquals(listOf("b"),ThorDownloadQueue.next(listOf("a","b","c"),setOf("a"),2))
        assertEquals(emptyList<String>(),ThorDownloadQueue.next(listOf("c"),setOf("a","b"),2))
        assertEquals(listOf("c"),ThorDownloadQueue.next(listOf("c","d"),setOf("b"),2))
    }
    @Test fun loweringLimitNeverAdmitsUntilExistingWorkersDrain() {
        assertEquals(emptyList<String>(),ThorDownloadQueue.next(listOf("c"),setOf("a","b"),1))
        assertEquals(emptyList<String>(),ThorDownloadQueue.next(listOf("c"),setOf("b"),1))
        assertEquals(listOf("c"),ThorDownloadQueue.next(listOf("c"),emptySet(),1))
    }
    @Test fun noDuplicateAdmissionAndUnsupportedLimitRejected() {
        assertEquals(listOf("a","b"),ThorDownloadQueue.next(listOf("a","a","b"),emptySet(),3))
        assertThrows(IllegalArgumentException::class.java) { ThorDownloadQueue.next(emptyList(),emptySet(),4) }
    }
    @Test fun treeDocumentVariantsAndUnicodeCaseCollide() {
        val a=ThorDownloadQueue.targetKey(tree,"nds/Café.nds")
        val b=ThorDownloadQueue.targetKey("$tree/document/4A21-0000%3AROMs","NDS/CAFE\u0301.NDS")
        assertEquals(a,b)
        assertNotEquals(a,ThorDownloadQueue.targetKey(tree.replace("4A21-0000","primary"),"nds/Café.nds"))
    }
    @Test fun platformAliasesResolveToSameOwnershipKey() {
        val hash="a".repeat(64)
        fun destination(platform:String)=RomSyncPlan.plan(listOf(RomSyncPlan.Entry("$platform/Game.nds",1,hash)),emptyList()).single().destinationPath!!
        assertEquals(ThorDownloadQueue.targetKey(tree,destination("ds")),ThorDownloadQueue.targetKey(tree,destination("nds")))
    }
    @Test fun repeatedCompletionAlwaysAdvancesBoundedQueue() {
        val waiting=(1..100).map { "job-$it" }.toMutableList()
        val active=linkedSetOf<String>(); val completed=mutableSetOf<String>()
        while(waiting.isNotEmpty() || active.isNotEmpty()) {
            val next=ThorDownloadQueue.next(waiting,active,3)
            next.forEach { assertTrue(active.add(it)); waiting.remove(it) }
            assertTrue(active.size <= 3)
            val done=active.last(); active.remove(done); assertTrue(completed.add(done))
        }
        assertEquals(100,completed.size)
    }
}
