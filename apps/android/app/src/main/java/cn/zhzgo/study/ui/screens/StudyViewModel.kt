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

data class BankState(val majors: List<Major> = emptyList(), val subjects: List<Subject> = emptyList(),
    val progress: List<ProgressResponse> = emptyList(), val loading: Boolean = true, val error: String? = null)
class StudyViewModel(application: Application) : AndroidViewModel(application) {
    private val api = RetrofitClient.create(application)
    private val prefs = StudyPreferences(application)
    val memory = prefs.memory.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StudyMemory())
    private val _state = MutableStateFlow(BankState())
    val state = _state.asStateFlow()
    private var refreshing=false
    fun refresh() {
        if(refreshing) return
        refreshing=true
        viewModelScope.launch {
            _state.update { it.copy(loading=true,error=null) }
            try {
                val majors=async{api.getMajors()}; val subjects=async{api.getSubjects(null)}
                val progress=async{api.getProgress()}
                _state.value=BankState(majors.await(),subjects.await(),progress.await(),false)
            } catch(e: CancellationException) { throw e }
              catch(e: Exception) { _state.update{it.copy(loading=false,error="题库加载失败，请检查网络后重试")} }
            finally {refreshing=false}
        }
    }
    fun visit(subject: Subject) { viewModelScope.launch{prefs.visit(subject)} }
    fun pin(id: Int) { viewModelScope.launch{prefs.pin(id)} }
}
