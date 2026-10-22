package cn.zhzgo.study.ui.screens
import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.zhzgo.study.data.*
import cn.zhzgo.study.network.RetrofitClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
class ExamViewModel(application:Application):AndroidViewModel(application) {
    private val apiService=RetrofitClient.create(application)
    private val _papers=MutableStateFlow<List<ExamPaper>>(emptyList());val papers=_papers.asStateFlow()
    private val _records=MutableStateFlow<List<ExamRecord>>(emptyList());val records=_records.asStateFlow()
    private val _currentExam=MutableStateFlow<ExamStartResponse?>(null);val currentExam=_currentExam.asStateFlow()
    private val _submitResult=MutableStateFlow<ExamSubmitResponse?>(null);val submitResult=_submitResult.asStateFlow()
    private val _userAnswers=MutableStateFlow<Map<String,String>>(emptyMap());val userAnswers=_userAnswers.asStateFlow()
    private val _isLoading=MutableStateFlow(false);val isLoading=_isLoading.asStateFlow()
    private val _recordsLoading=MutableStateFlow(false);val recordsLoading=_recordsLoading.asStateFlow()
    private val _error=MutableStateFlow<String?>(null);val error=_error.asStateFlow()
    private val _isAbandoned=MutableStateFlow(false);val isAbandoned=_isAbandoned.asStateFlow()
    private val _isAbandoning=MutableStateFlow(false);val isAbandoning=_isAbandoning.asStateFlow()
    private val _isSubmitting=MutableStateFlow(false);val isSubmitting=_isSubmitting.asStateFlow()
    private var deadline=0L
    fun remainingSeconds():Int=ExamWorkflow.remainingSeconds(deadline,SystemClock.elapsedRealtime())
    private fun requestError(e:Exception,fallback:String):String {
        return try {
            (e as? retrofit2.HttpException)?.response()?.errorBody()?.string()?.let{org.json.JSONObject(it).optString("error").takeIf{s->s.isNotBlank()}} ?: fallback
        }catch(e:Exception){fallback}
    }
    fun loadPapers() {
        if(_isLoading.value)return
        _isLoading.value=true;_error.value=null
        viewModelScope.launch {
            try{_papers.value=apiService.getExamPapers()}
            catch(e:CancellationException){throw e}catch(e:Exception){_error.value=requestError(e,"试卷加载失败，请重试")}
            finally{_isLoading.value=false}
        }
    }
    fun loadRecords() {
        if(_recordsLoading.value)return
        _recordsLoading.value=true
        viewModelScope.launch {
            try{_records.value=apiService.getExamRecords()}
            catch(e:CancellationException){throw e}catch(e:Exception){_error.value=requestError(e,"考试记录加载失败，请重试")}
            finally{_recordsLoading.value=false}
        }
    }
    fun startExam(paperId:Int,onSuccess:()->Unit) {
        if(_isLoading.value || _isSubmitting.value)return
        _isLoading.value=true;_error.value=null
        viewModelScope.launch {
            try {
                val response=apiService.startExam(paperId)
                _currentExam.value=response;_userAnswers.value=emptyMap();_submitResult.value=null
                _isAbandoned.value=false;_isAbandoning.value=false
                deadline=SystemClock.elapsedRealtime()+response.paper.duration_minutes.toLong()*60_000L
                onSuccess()
            }catch(e:CancellationException){throw e}catch(e:Exception){_error.value=requestError(e,"开始考试失败，请重试")}
            finally{_isLoading.value=false}
        }
    }
    fun setAnswer(questionId:Int,answer:String) {
        if(_isSubmitting.value || _isAbandoned.value || remainingSeconds()<=0)return
        if(_currentExam.value?.questions?.none{it.id==questionId}!=false)return
        _userAnswers.update{it+(questionId.toString() to answer)}
    }
    fun submitExam(recordId:Int,onSuccess:()->Unit) {
        if(_isSubmitting.value || _isAbandoned.value || _currentExam.value?.record_id!=recordId)return
        _isSubmitting.value=true;_error.value=null
        val paperId=_currentExam.value?.paper?.id ?: return
        val answers=_userAnswers.value.toMap()
        viewModelScope.launch {
            try {
                val response=apiService.submitExam(recordId,ExamSubmitRequest(answers))
                _submitResult.value=response
                _currentExam.value=null
                response.details?.let { details ->
                    runCatching { StudyPreferences(getApplication()).recordOutcomes(details.associate{"exam:$paperId:${it.question_id}" to it.is_correct}) }
                }
                onSuccess()
            }catch(e:CancellationException){throw e}catch(e:Exception){_error.value=requestError(e,"交卷失败，答案已保留，请重试")}
            finally{_isSubmitting.value=false}
        }
    }
    fun abandonExam(recordId:Int) {
        if(_isAbandoning.value || _isSubmitting.value || _currentExam.value?.record_id!=recordId)return
        _isAbandoned.value=true;_isAbandoning.value=true;_error.value=null
        val total=_currentExam.value?.paper?.total_score ?: 0
        val unanswered=_currentExam.value?.questions?.size ?: 0
        viewModelScope.launch {
            try {
                apiService.abandonExam(recordId)
                _submitResult.value=ExamSubmitResponse(recordId,0,total,0,0,unanswered,false,null)
                _currentExam.value=null
            }catch(e:CancellationException){throw e}catch(e:Exception){_error.value=requestError(e,"中止状态同步失败，请重试")}
            finally{_isAbandoning.value=false}
        }
    }
    fun clearError(){_error.value=null}
    fun clearCurrentExam(){_currentExam.value=null;_userAnswers.value=emptyMap();_submitResult.value=null;_isAbandoned.value=false;deadline=0}
}
