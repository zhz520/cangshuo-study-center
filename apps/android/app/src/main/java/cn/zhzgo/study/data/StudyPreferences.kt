package cn.zhzgo.study.data
import android.content.Context
import androidx.datastore.preferences.core.*
import com.google.gson.Gson
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

data class RecentSubject(val id: Int, val name: String, val majorId: Int)
data class StudySession(val subjectId: Int, val subjectName: String, val questionId: Int, val index: Int)
data class DailyPractice(val seconds:Long=0,val outcomes:Map<String,Boolean> = emptyMap())
data class StudyMemory(
    val recent: List<RecentSubject> = emptyList(), val pinned: Set<Int> = emptySet(),
    val mastered: Set<Int> = emptySet(), val session: StudySession? = null,
    val dailyGoal: Int = 30, val drafts: Map<Int,String> = emptyMap(),
    val activity: Map<String,DailyPractice> = emptyMap()
)
/** Only navigation metadata and personal review marks. Never stores question stems or solutions. */
class StudyPreferences(private val context: Context) {
    private val gson = Gson()
    private fun key(p: Preferences): Preferences.Key<String> {
        val account = p[stringPreferencesKey("user_info")].orEmpty()
        val scope = MessageDigest.getInstance("SHA-256").digest(account.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
        return stringPreferencesKey("study_workspace_$scope")
    }
    private fun read(p: Preferences): StudyMemory = runCatching {
        p[key(p)]?.let { gson.fromJson(it, StudyMemory::class.java) }
    }.getOrNull() ?: StudyMemory()
    val memory = context.dataStore.data.map(::read)
    private suspend fun update(transform: (StudyMemory) -> StudyMemory) {
        context.dataStore.edit { p -> p[key(p)] = gson.toJson(transform(read(p))) }
    }
    suspend fun visit(subject: Subject) = update { old ->
        old.copy(recent=(listOf(RecentSubject(subject.id,subject.name,subject.major_id))+old.recent.filter{it.id!=subject.id}).take(6))
    }
    suspend fun pin(id: Int) = update { it.copy(pinned=if(id in it.pinned) it.pinned-id else it.pinned+id) }
    suspend fun mark(ids: Set<Int>, mastered: Boolean) = update { it.copy(mastered=if(mastered) it.mastered+ids else it.mastered-ids) }
    suspend fun goal(value: Int) = update { it.copy(dailyGoal=value.coerceIn(5,200)) }
    suspend fun checkpoint(session: StudySession) = update { it.copy(session=session) }
    suspend fun draft(id: Int, answer: String) = update { old ->
        old.copy(drafts=(old.drafts + (id to answer)).entries.toList().takeLast(100).associate{it.toPair()})
    }
    suspend fun resetSubject(subjectId: Int, ids: Set<Int>) = update {
        it.copy(session=it.session?.takeUnless{s->s.subjectId==subjectId}, drafts=it.drafts-ids, mastered=it.mastered-ids)
    }
    suspend fun clearPracticeHistory() = update { it.copy(session=null,drafts=emptyMap(),mastered=emptySet(),activity=emptyMap()) }
    private fun recentActivity(activity:Map<String,DailyPractice>):Map<String,DailyPractice> = activity.toSortedMap().entries.toList().takeLast(30).associate{it.toPair()}
    suspend fun addStudySeconds(account:String,seconds:Long) {
        if(seconds<=0)return
        context.dataStore.edit { p ->
            if(p[stringPreferencesKey("user_info")]!=account)return@edit
            val memory=read(p);val date=java.time.LocalDate.now().toString()
            val day=memory.activity[date] ?: DailyPractice()
            p[key(p)]=gson.toJson(memory.copy(activity=recentActivity(memory.activity+(date to day.copy(seconds=day.seconds+seconds)))))
        }
    }
    suspend fun recordOutcomes(outcomes:Map<String,Boolean>) = update { memory ->
        val date=java.time.LocalDate.now().toString();val day=memory.activity[date] ?: DailyPractice()
        // Keep the first answer to each question per day; this is a personal trend, never a ranking score.
        val merged=outcomes+day.outcomes
        memory.copy(activity=recentActivity(memory.activity+(date to day.copy(outcomes=merged))))
    }
    suspend fun removeDraft(id: Int) = update { it.copy(drafts=it.drafts-id) }
}
