package cn.zhzgo.study.ui.viewmodels
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.zhzgo.study.data.*
import cn.zhzgo.study.network.RetrofitClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
class ArticleDetailViewModel:ViewModel() {
    private val _article=MutableStateFlow<Article?>(null);val article=_article.asStateFlow()
    private val _comments=MutableStateFlow<List<Comment>>(emptyList());val comments=_comments.asStateFlow()
    private val _isLoading=MutableStateFlow(false);val isLoading=_isLoading.asStateFlow()
    private val _isCommentsLoading=MutableStateFlow(false);val isCommentsLoading=_isCommentsLoading.asStateFlow()
    private val _isPosting=MutableStateFlow(false);val isPosting=_isPosting.asStateFlow()
    private val _deleting=MutableStateFlow<Set<Int>>(emptySet());val deleting=_deleting.asStateFlow()
    private val _error=MutableStateFlow<String?>(null);val error=_error.asStateFlow()
    private val _commentError=MutableStateFlow<String?>(null);val commentError=_commentError.asStateFlow()
    private var commentsJob:Job?=null;private var version=0
    fun fetchArticleDetail(context:Context,articleId:Int) {
        if(_isLoading.value)return
        _isLoading.value=true;_error.value=null
        viewModelScope.launch {
            try{_article.value=RetrofitClient.create(context).getArticleDetail(articleId);fetchComments(context,articleId)}
            catch(e:CancellationException){throw e}catch(e:Exception){_error.value="文章加载失败，请重试"}
            finally{_isLoading.value=false}
        }
    }
    fun fetchComments(context:Context,articleId:Int) {
        commentsJob?.cancel();val generation=++version
        _isCommentsLoading.value=true;_commentError.value=null
        commentsJob=viewModelScope.launch {
            try{val data=RetrofitClient.create(context).getComments(articleId);if(generation==version)_comments.value=data}
            catch(e:CancellationException){throw e}catch(e:Exception){if(generation==version)_commentError.value="评论加载失败，请重试"}
            finally{if(generation==version)_isCommentsLoading.value=false}
        }
    }
    fun postComment(context:Context,articleId:Int,content:String,onSuccess:(String)->Unit) {
        if(_isPosting.value||content.isBlank())return
        _isPosting.value=true;_commentError.value=null
        viewModelScope.launch {
            try{val response=RetrofitClient.create(context).postComment(articleId,CommentRequest(content));fetchComments(context,articleId);onSuccess(response.message)}
            catch(e:CancellationException){throw e}catch(e:Exception){_commentError.value="评论发送失败，内容已保留，请重试"}
            finally{_isPosting.value=false}
        }
    }
    fun deleteComment(context:Context,commentId:Int,articleId:Int) {
        if(commentId in _deleting.value)return
        _deleting.update{it+commentId};_commentError.value=null
        viewModelScope.launch {
            try{RetrofitClient.create(context).deleteComment(commentId);fetchComments(context,articleId)}
            catch(e:CancellationException){throw e}catch(e:Exception){_commentError.value="评论删除失败，请重试"}
            finally{_deleting.update{it-commentId}}
        }
    }
}
