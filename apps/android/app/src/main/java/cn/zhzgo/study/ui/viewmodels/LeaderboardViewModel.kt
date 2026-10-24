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
            _totalQuestionsRanking.value=emptyList();_accuracyRanking.value=emptyList();_scoreRanking.value=emptyList()
            _hasMore.value=false;_moreTypes.value=emptySet()
            loadRankings(context)
        }
    }

    private var rankingJob:Job?=null
    private var generation=0
    private var page=1
    private val _hasMore=MutableStateFlow(false)
    val hasMore=_hasMore.asStateFlow()
    private val _moreTypes=MutableStateFlow<Set<Int>>(emptySet())
    val moreTypes=_moreTypes.asStateFlow()
    fun resume(context:Context) { if(_totalQuestionsRanking.value.isEmpty()&&_accuracyRanking.value.isEmpty()&&_scoreRanking.value.isEmpty()&&!_isLoading.value)loadRankings(context) }
    fun loadMore(context:Context) { if(!_isLoading.value && _hasMore.value) loadRankings(context,true) }
    fun loadRankings(context:Context,append:Boolean=false) {
        rankingJob?.cancel();val version=++generation
        _isLoading.value=true;_error.value=null
        val subject=_selectedSubjectId.value
        val targetPage=if(append)page+1 else 1
        rankingJob=viewModelScope.launch {
            try {
                val api=RetrofitClient.create(context)
                val total=async{api.getLeaderboard("total",subjectId=subject,page=targetPage,limit=50)}
                val accuracy=async{api.getLeaderboard("accuracy",subjectId=subject,page=targetPage,limit=50)}
                val score=async{api.getLeaderboard("score",subjectId=subject,page=targetPage,limit=50)}
                val t=total.await();val a=accuracy.await();val s=score.await()
                if(version==generation){
                    _totalQuestionsRanking.value=(if(append)_totalQuestionsRanking.value+t else t).distinctBy{it.user_id}
                    _accuracyRanking.value=(if(append)_accuracyRanking.value+a else a).distinctBy{it.user_id}
                    _scoreRanking.value=(if(append)_scoreRanking.value+s else s).distinctBy{it.user_id}
                    page=targetPage;_hasMore.value=listOf(t,a,s).any{it.size==50}
                    _moreTypes.value=listOf(t,a,s).mapIndexedNotNull{i,rows->i.takeIf{rows.size==50}}.toSet()
                }
            }catch(e:CancellationException){throw e}catch(e:Exception){if(version==generation)_error.value="排行加载失败，请重试"}
            finally{if(version==generation)_isLoading.value=false}
        }
    }
}
