package cn.zhzgo.study.data
import org.junit.Assert.*
import org.junit.Test
class ExamWorkflowTest {
    @Test fun deadlineSurvivesRoundingAndExpiry() {
        assertEquals(2,ExamWorkflow.remainingSeconds(2000,999))
        assertEquals(1,ExamWorkflow.remainingSeconds(2000,1000))
        assertEquals(1,ExamWorkflow.remainingSeconds(2000,1999))
        assertEquals(0,ExamWorkflow.remainingSeconds(2000,2000))
        assertEquals(0,ExamWorkflow.remainingSeconds(2000,3000))
    }
    @Test fun submittedCountExcludesBlankAndUnknownQuestions() {
        assertEquals(setOf(1,3),ExamWorkflow.answeredIds(listOf(1,2,3,4),mapOf("1" to "A","2" to "  ","3" to "A,C","99" to "B")))
    }
    @Test fun multipleChoiceTogglesWithoutDuplicatesAndKeepsStableOrder() {
        assertEquals("A,C",ExamWorkflow.toggleMultiple("C, C , A","B").let{ExamWorkflow.toggleMultiple(it,"B")})
        assertEquals("C",ExamWorkflow.toggleMultiple("A,C","A"))
        assertEquals("",ExamWorkflow.toggleMultiple("A","A"))
    }
    @Test fun parsesPlainStringArrays() {
        assertEquals(linkedMapOf("A" to "甲","B" to "乙"),QuestionOptions.parse(listOf("甲","乙")))
    }
    @Test fun parsesLabeledLegacyObjects() {
        assertEquals(mapOf("C" to "第三项"),QuestionOptions.parse(listOf(mapOf("label" to "C","text" to "第三项"))))
    }
    @Test fun parsesJsonOptionMaps() {
        assertEquals(mapOf("A" to "First","B" to "Second"),QuestionOptions.parse("{\"A\":\"First\",\"B\":\"Second\"}"))
    }
    @Test fun jsonStringPrimitiveDoesNotRecurse() {
        assertTrue(QuestionOptions.parse("\"plain text\"").isEmpty())
    }
    @Test fun malformedOrMissingOptionsProduceSafeEmptyState() {
        assertTrue(QuestionOptions.parse(null).isEmpty())
        assertTrue(QuestionOptions.parse("{invalid}").isEmpty())
        assertTrue(QuestionOptions.parse(42).isEmpty())
    }
}
