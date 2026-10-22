package cn.zhzgo.study.ui.viewmodels

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.zhzgo.study.data.LeaderboardUser
import cn.zhzgo.study.data.UserPreferences
import android.content.Context
import android.app.Application
import cn.zhzgo.study.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import cn.zhzgo.study.data.Subject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException

class LeaderboardViewModel(application: Application) : AndroidViewModel(application) {
    private val userPreferences = UserPreferences(application)

    private val _currentUser = MutableStateFlow<String?>("")
    val currentUser: StateFlow<String?> = _currentUser.asStateFlow()

    private val _subjects = MutableStateFlow<List<Pair<String, String>>>(listOf("all" to "全部科目"))
    val subjects: StateFlow<List<Pair<String, String>>> = _subjects.asStateFlow()

    private val _selectedSubjectId = MutableStateFlow("all")
    val selectedSubjectId: StateFlow<String> = _selectedSubjectId.asStateFlow()

    init {
        viewModelScope.launch {
            userPreferences.userName.collect { name ->
                _currentUser.value = name
            }
        }
    }

    private val _totalQuestionsRanking = MutableStateFlow<List<LeaderboardUser>>(emptyList())
    val totalQuestionsRanking: StateFlow<List<LeaderboardUser>> = _totalQuestionsRanking.asStateFlow()

    private val _accuracyRanking = MutableStateFlow<List<LeaderboardUser>>(emptyList())
    val accuracyRanking: StateFlow<List<LeaderboardUser>> = _accuracyRanking.asStateFlow()

    private val _scoreRanking = MutableStateFlow<List<LeaderboardUser>>(emptyList())
    val scoreRanking: StateFlow<List<LeaderboardUser>> = _scoreRanking.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Init block removed, call explicitly from UI

    fun loadSubjects(context: Context) {
        viewModelScope.launch {
            try {
                val api = RetrofitClient.create(context)
                val fetchedSubjects = api.getSubjects(null)
                val newList = mutableListOf("all" to "全部科目")
                newList.addAll(fetchedSubjects.map { it.id.toString() to it.name })
                _subjects.value = newList
            } catch (e: Exception) {
                // Ignore error, fallback to 'all'
            }
        }
    }

    fun selectSubject(subjectId: String, context: Context) {
        if (_selectedSubjectId.value != subjectId) {
            _selectedSubjectId.value = subjectId
            loadRankings(context)
        }
    }

    private var rankingJob:Job?=null
    private var generation=0
    fun loadRankings(context:Context) {
        rankingJob?.cancel();val version=++generation
        _isLoading.value=true;_error.value=null
        val subject=_selectedSubjectId.value
        rankingJob=viewModelScope.launch {
            try {
                val api=RetrofitClient.create(context)
                val total=async{api.getLeaderboard("total",subjectId=subject,limit=100)}
                val accuracy=async{api.getLeaderboard("accuracy",subjectId=subject,limit=100)}
                val score=async{api.getLeaderboard("score",subjectId=subject,limit=100)}
                val t=total.await();val a=accuracy.await();val s=score.await()
                if(version==generation){_totalQuestionsRanking.value=t;_accuracyRanking.value=a;_scoreRanking.value=s}
            }catch(e:CancellationException){throw e}catch(e:Exception){if(version==generation)_error.value="排行加载失败，请重试"}
            finally{if(version==generation)_isLoading.value=false}
        }
    }
}
