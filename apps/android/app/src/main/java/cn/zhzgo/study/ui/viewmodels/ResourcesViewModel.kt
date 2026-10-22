package cn.zhzgo.study.ui.viewmodels
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.zhzgo.study.data.Article
import cn.zhzgo.study.network.RetrofitClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ResourcesState(val articles:List<Article> = emptyList(),val categories:List<String> = emptyList(),
    val category:String?=null,val loading:Boolean=false,val end:Boolean=false,val error:String?=null)
class ResourcesViewModel(application:Application):AndroidViewModel(application) {
    private val api=RetrofitClient.create(application)
    private val _state=MutableStateFlow(ResourcesState())
    val state=_state.asStateFlow()
    private var page=0
    private var generation=0
    private var request:Job?=null
    fun resume() { if (page == 0 && !_state.value.loading) load() }
    fun category(value:String?) {if(_state.value.category!=value){_state.update{it.copy(category=value,articles=emptyList(),end=false)};load()}}
    fun load(append:Boolean=false) {
        if(append&&(_state.value.loading||_state.value.end))return
        request?.cancel()
        val version=++generation
        val target=if(append)page+1 else 1
        val category=_state.value.category
        _state.update{it.copy(loading=true,error=null)}
        request=viewModelScope.launch {
            try {
                val categories=async {
                    try{api.getResourceCategories().map{it.name}}
                    catch(e:CancellationException){throw e}catch(e:Exception){_state.value.categories}
                }
                val fetched=api.getArticles(category,target,20)
                val names=categories.await()
                if(version==generation) {
                    _state.update{it.copy(articles=if(append)(it.articles+fetched).distinctBy{a->a.id}else fetched,
                        categories=(it.categories+names+fetched.mapNotNull{a->a.category}).distinct(),loading=false,end=fetched.size<20)}
                    page=target
                }
            }catch(e:CancellationException){throw e}
             catch(e:Exception){if(version==generation)_state.update{it.copy(loading=false,error="资源加载失败，请检查网络后重试")}}
        }
    }
}
