package cn.zhzgo.study.data
import org.junit.Assert.*
import org.junit.Test
class PracticeWorkflowTest {
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
