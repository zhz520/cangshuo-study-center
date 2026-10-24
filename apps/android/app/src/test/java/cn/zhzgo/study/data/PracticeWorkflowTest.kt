package cn.zhzgo.study.data
import org.junit.Assert.*
import org.junit.Test
class PracticeWorkflowTest {
    @Test fun answerSheetHasDisjointPagesAndHandlesPartialLastPage() {
        assertEquals(3,PracticeWorkflow.sheetPageCount(121))
        assertEquals((0..49).toList(),PracticeWorkflow.sheetIndices(121,0))
        assertEquals((50..99).toList(),PracticeWorkflow.sheetIndices(121,1))
        assertEquals((100..120).toList(),PracticeWorkflow.sheetIndices(121,2))
        assertEquals((0..19).toList(),PracticeWorkflow.sheetIndices(20,0))
        assertEquals(emptyList<Int>(),PracticeWorkflow.sheetIndices(0,0))
    }
    @Test fun resumesAtBothSidesOfEveryPageBoundary() {
        for(page in 0..100) {
            assertEquals(page+1,PracticeWorkflow.pageForIndex(page*50))
            assertEquals(page+1,PracticeWorkflow.pageForIndex(page*50+49))
        }
    }
    @Test fun handlesCorruptNegativeCheckpointWithoutInvalidPage() {
        assertEquals(1,PracticeWorkflow.pageForIndex(-50))
        assertTrue(PracticeWorkflow.pageForIndex(Int.MAX_VALUE)>0)
    }
    @Test fun reviewQueueCannotExpandIntoUnboundedNetworkRequests() {
        val queue=PracticeWorkflow.reviewQueue(listOf(-1,0)+List(100){it+1}+listOf(1,2))
        assertEquals((1..20).toList(),queue)
    }
    @Test fun reviewQueuePreservesSelectedOrderAndRemovesDuplicates() {
        assertEquals(listOf(9,3,7),PracticeWorkflow.reviewQueue(listOf(9,3,9,7,0)))
    }
    @Test fun restoresMultiselectAsCollectionInsteadOfSingleChoice() {
        assertEquals(setOf("A","C"),PracticeWorkflow.restoreDraft("multiple","A,C"))
        assertEquals(emptySet<String>(),PracticeWorkflow.restoreDraft("multiple",""))
    }
    @Test fun preservesFillAnswerWhitespaceAndUnicode() {
        val answer="  积分 x + π\n下一行  "
        assertEquals(answer,PracticeWorkflow.restoreDraft("fill",answer))
    }
}
