package cn.zhzgo.study.ui.screens
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.zhzgo.study.data.*
import cn.zhzgo.study.network.RetrofitClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MistakesState(val subjects:List<Subject> = emptyList(),val progress:List<ProgressResponse> = emptyList(),
    val questions:List<Question> = emptyList(),val loading:Boolean=true,val paging:Boolean=false,val end:Boolean=false,val error:String?=null)
class MistakesViewModel(application:Application):AndroidViewModel(application) {
    private val api=RetrofitClient.create(application)
    private val prefs=StudyPreferences(application)
    val memory=prefs.memory.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),StudyMemory())
    private val _state=MutableStateFlow(MistakesState())
    val state=_state.asStateFlow()
    private var page=1
    private var subjectId:Int?=null
    private var refreshing=false
    fun resume(subject:Int?) {
        if (subjectId != subject || _state.value.loading || _state.value.subjects.isEmpty()) { refresh(subject); return }
        if (refreshing || _state.value.paging) return
        refreshing = true
        viewModelScope.launch {
            try {
                val progress = api.getProgress()
                val wrong = progress.filter { it.is_correct == 0 }.map { it.question_id }.toSet()
                _state.update { it.copy(progress = progress, questions = it.questions.filter { q -> q.id in wrong }) }
            } catch (e:CancellationException) { throw e }
              catch (_:Exception) { _state.update { it.copy(error = "复习记录暂未更新，请重试") } }
            finally { refreshing = false }
        }
    }
    fun refresh(subject:Int?) {
        if(refreshing) return
        refreshing=true; subjectId=subject; page=1
        viewModelScope.launch {
            _state.update{it.copy(loading=true,error=null)}
            try {
                val subjects=async{api.getSubjects(null)}; val progress=async{api.getProgress()}
                val questions=if(subject!=null)api.getMistakes(1,subject.takeIf{it>0},50)else emptyList()
                _state.value=MistakesState(subjects.await(),progress.await(),questions,false,false,questions.size<50)
            } catch(e:CancellationException){throw e}
              catch(e:Exception){_state.update{it.copy(loading=false,error="错题记录加载失败，请稍后重试")}}
            finally{refreshing=false}
        }
    }
    fun more() {
        if(_state.value.loading || _state.value.paging || _state.value.end || subjectId==null)return
        viewModelScope.launch {
            _state.update{it.copy(paging=true,error=null)}
            try {
                val next=api.getMistakes(page+1,subjectId?.takeIf{it>0},50)
                page++
                _state.update{it.copy(questions=(it.questions+next).distinctBy{q->q.id},paging=false,end=next.size<50)}
            }catch(e:CancellationException){throw e}
             catch(e:Exception){_state.update{it.copy(paging=false,error="下一页加载失败，可重试")}}
        }
    }
    fun mark(ids:Set<Int>,mastered:Boolean) { viewModelScope.launch{prefs.mark(ids,mastered)} }
}
