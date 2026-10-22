package cn.zhzgo.study.ui.viewmodels
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.zhzgo.study.data.Question
import cn.zhzgo.study.network.RetrofitClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class FavoritesState(val questions:List<Question> = emptyList(),val total:Int?=null,val loading:Boolean=false,
    val end:Boolean=false,val removing:Boolean=false,val error:String?=null)
class FavoritesViewModel(application:Application):AndroidViewModel(application) {
    private val api=RetrofitClient.create(application)
    private val _state=MutableStateFlow(FavoritesState())
    val state=_state.asStateFlow()
    private var page=0
    fun resume() {
        if (page == 0) { load(); return }
        if (_state.value.loading || _state.value.removing) return
        viewModelScope.launch {
            try {
                val ids = api.getFavoriteQuestionIds().toSet()
                _state.update { it.copy(questions = it.questions.filter { q -> q.id in ids }, total = ids.size) }
            } catch(e:CancellationException) { throw e }
              catch (_:Exception) { _state.update { it.copy(error = "收藏状态暂未更新，可手动刷新") } }
        }
    }
    fun load(append:Boolean=false,retainedError:String?=null) {
        if(_state.value.loading || _state.value.removing || (append && _state.value.end))return
        _state.update{it.copy(loading=true,error=retainedError)}
        viewModelScope.launch {
            try {
                val target=if(append)page+1 else 1
                val total=async{if(!append)api.getFavoriteQuestionIds().size else _state.value.total}
                val items=api.getFavoritePracticeQuestions(target,50)
                val count=total.await()
                _state.update{it.copy(questions=if(append)(it.questions+items).distinctBy{q->q.id}else items,
                    total=count,loading=false,end=items.size<50)}
                page=target
            }catch(e:CancellationException){throw e}
             catch(e:Exception){_state.update{it.copy(loading=false,error="收藏加载失败，请重试")}}
        }
    }
    fun remove(ids:Set<Int>,onDone:()->Unit={}) {
        if(_state.value.loading || _state.value.removing || ids.isEmpty())return
        _state.update{it.copy(removing=true,error=null)}
        viewModelScope.launch {
            var changed=false
            var failure:String?=null
            try {
                for(id in ids) {
                    api.removeFavoriteByQuestionId(id)
                    changed=true
                    _state.update{it.copy(questions=it.questions.filter{q->q.id!=id},total=it.total?.let{n->maxOf(0,n-1)})}
                }
            }catch(e:CancellationException){throw e}
             catch(e:Exception){failure="部分收藏未能取消，请检查列表后重试";_state.update{it.copy(error=failure)}}
            finally{_state.update{it.copy(removing=false)}}
            onDone()
            if(changed) {
                // Deletions shift offset pagination; restart at page one before continuing.
                page=0
                load(retainedError=failure)
            }
        }
    }
}
