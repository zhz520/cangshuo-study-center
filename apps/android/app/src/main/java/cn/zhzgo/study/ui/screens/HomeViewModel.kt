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
import kotlinx.coroutines.supervisorScope

data class HomeState(val home: HomeDataResponse?=null, val daily: DailyContentResponse?=null,
    val stats: UserStats?=null, val detailed: DetailedStatsResponse?=null, val loading:Boolean=true, val error:String?=null)
class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val api = RetrofitClient.create(application)
    private val prefs = StudyPreferences(application)
    private val user = UserPreferences(application)
    val memory = prefs.memory.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),StudyMemory())
    val userName = user.nickname.combine(user.userName) { nickname,name -> nickname?.takeIf{it.isNotBlank()} ?: name ?: "同学" }
        .stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),"同学")
    private val _state=MutableStateFlow(HomeState())
    val state=_state.asStateFlow()
    private var refreshing=false
    fun refresh() {
        if(refreshing) return
        refreshing=true
        viewModelScope.launch {
            _state.update{it.copy(loading=true,error=null)}
            supervisorScope {
                suspend fun <T> optional(block:suspend()->T):T? = try{block()}catch(e:CancellationException){throw e}catch(e:Exception){null}
                val home=async{optional{api.getHomeData()}}; val daily=async{optional{api.getDailyContent()}}
                val stats=async{optional{api.getUserStats()}}; val detail=async{optional{api.getDetailedStats()}}
                val h=home.await(); val d=daily.await(); val s=stats.await(); val a=detail.await()
                val previous = _state.value
                _state.value=HomeState(h ?: previous.home,d ?: previous.daily,s ?: previous.stats,a ?: previous.detailed,false,if(s==null || a==null) "学习数据暂时无法更新" else null)
            }
            refreshing=false
        }
    }
    fun setGoal(value:Int) { viewModelScope.launch{prefs.goal(value)} }
}
