package cn.zhzgo.study.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.zhzgo.study.data.Question
import cn.zhzgo.study.data.Subject
import cn.zhzgo.study.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException

data class SearchUiState(
    val query: String = "",
    val results: List<Question> = emptyList(),
    val isLoading: Boolean = false,
    val isSearching: Boolean = false,
    val error: String? = null,
    val page: Int = 1,
    val hasReachedEnd: Boolean = false,
    val selectedType: String = "all",
    val selectedSubjectId: Int = 0,
    val hasSearched: Boolean = false,
    val subjects: List<Subject> = emptyList()
)

class SearchViewModel(application:Application):AndroidViewModel(application) {
    private val api=RetrofitClient.create(application)
    private val _uiState=MutableStateFlow(SearchUiState());val uiState=_uiState.asStateFlow()
    private var request:Job?=null
    private var version=0
    private var activeQuery=""
    init {viewModelScope.launch{try{_uiState.update{it.copy(subjects=api.getSubjects(null))}}catch(e:CancellationException){throw e}catch(e:Exception){}}}
    fun onQueryChange(value:String){_uiState.update{it.copy(query=value)}}
    fun setTypeFilter(value:String){if(value!=_uiState.value.selectedType){_uiState.update{it.copy(selectedType=value)};search()}}
    fun setSubjectFilter(value:Int){if(value!=_uiState.value.selectedSubjectId){_uiState.update{it.copy(selectedSubjectId=value)};search()}}
    fun search(){load(false)}
    fun loadMore(){load(true)}
    private fun load(append:Boolean) {
        val state=_uiState.value
        if(append&&(state.isSearching||state.isLoading||state.hasReachedEnd||state.query.trim()!=activeQuery))return
        if(!append&&state.query.isBlank()&&state.selectedType=="all"&&state.selectedSubjectId==0) {
            request?.cancel();version++;_uiState.update{it.copy(results=emptyList(),isSearching=false,isLoading=false,hasSearched=false,error=null)};return
        }
        request?.cancel();val generation=++version
        val query=state.query.trim();val page=if(append)state.page+1 else 1
        activeQuery=query
        _uiState.update{it.copy(isSearching=!append,isLoading=append,error=null,hasSearched=true)}
        request=viewModelScope.launch {
            try {
                val results=api.searchQuestions(keyword=query.takeIf{it.isNotBlank()},type=state.selectedType.takeUnless{it=="all"},subjectId=state.selectedSubjectId.takeIf{it>0},page=page)
                if(generation==version)_uiState.update{it.copy(results=if(append)(it.results+results).distinctBy{q->q.id}else results,page=page,isLoading=false,isSearching=false,hasReachedEnd=results.size<50)}
            }catch(e:CancellationException){throw e}catch(e:Exception){if(generation==version)_uiState.update{it.copy(isLoading=false,isSearching=false,error="搜索请求失败，请稍后重试")}}
        }
    }
}
