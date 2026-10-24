package cn.zhzgo.study.ui.screens

import android.app.Application
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.zhzgo.study.data.SubscriptionRedeemRequest
import cn.zhzgo.study.data.User
import cn.zhzgo.study.data.UserPreferences
import cn.zhzgo.study.network.RetrofitClient
import cn.zhzgo.study.ui.components.isUserPlus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- ViewModel ---

class SubscriptionViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = UserPreferences(application)

    private val _subscriptionEndDate = MutableStateFlow<String?>(null)
    val subscriptionEndDate = _subscriptionEndDate.asStateFlow()
    val userRole = prefs.userRole

    init {
        viewModelScope.launch { prefs.subscriptionEndDate.collect { _subscriptionEndDate.value = it } }
    }

    fun redeemSubscription(code: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.create(getApplication()).redeemSubscription(SubscriptionRedeemRequest(code))
                response.subscription_end_date?.let { prefs.setSubscriptionEndDate(it) }
                onSuccess(response.message)
            } catch (e: retrofit2.HttpException) {
                val errorMsg = try {
                    val jObjError = JSONObject(e.response()?.errorBody()?.string() ?: "")
                    jObjError.getString("error")
                } catch (e: Exception) {
                    "兑换失败"
                }
                onError(errorMsg)
            } catch (e: Exception) {
                onError(e.message ?: "兑换失败")
            }
        }
    }

    fun refreshStatus(onFinished: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.create(getApplication()).getUserProfile("me")
                val u = response.user
                
                prefs.saveUser(User(id=u.id,username=u.username,role=u.role,avatar_icon=u.avatar_icon,
                    nickname=u.nickname,slogan=u.slogan,qq_openid=u.qq_openid,email=u.email,subscription_end_date=u.subscription_end_date))

                onFinished(true, "订阅状态已刷新")
            } catch (e: Exception) {
                onFinished(false, "刷新失败: ${e.localizedMessage ?: "网络异常"}")
            }
        }
    }
}

@Composable
fun SubscriptionScreen(onBack:()->Unit,onPersonalize:()->Unit={}) {
    val model:SubscriptionViewModel=viewModel()
    val end by model.subscriptionEndDate.collectAsState()
    val role by model.userRole.collectAsState(initial=null)
    val active=role=="admin"||isUserPlus(end)
    var code by remember{mutableStateOf("")}
    var redeem by remember{mutableStateOf(false)}
    var busy by remember{mutableStateOf(false)}
    var message by remember{mutableStateOf<String?>(null)}
    var error by remember{mutableStateOf<String?>(null)}
    cn.zhzgo.study.ui.components.RefreshOnResume { model.refreshStatus{ok,msg->if(!ok)error=msg} }
    cn.zhzgo.study.ui.components.StudyPage("会员与权益",onBack,actions={TextButton(onClick={if(!busy){busy=true;model.refreshStatus{ok,msg->busy=false;if(ok){message=msg;error=null}else error=msg}}},enabled=!busy){Text("刷新")}}){padding->
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=24.dp)) {
            item {
                Text(if(active)"Plus 已开通" else "为专注学习多一步支持",style=MaterialTheme.typography.headlineMedium)
                Text(if(role=="admin")"管理员学习权益已启用" else if(active)"到期 ${end.orEmpty().take(10)}" else "按需要选择，保留你的学习节奏。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp,bottom=24.dp))
                if(busy)cn.zhzgo.study.ui.components.StudyLoading(Modifier.fillMaxWidth())
                message?.let{Text(it,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(vertical=12.dp))}
                error?.let{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(vertical=12.dp))}
                cn.zhzgo.study.ui.components.SectionLabel("权益概览")
                cn.zhzgo.study.ui.components.DetailPair("题库练习","免费：每科前 20 题\nPlus：完整练习")
                cn.zhzgo.study.ui.components.DetailPair("AI 题目讲解","按账号当日可用次数使用")
                cn.zhzgo.study.ui.components.DetailPair("学习辅助","学情建议与进阶工具")
                cn.zhzgo.study.ui.components.DetailPair("个性化","专属问候、首页内容与练习反馈")
                if(active) TextButton(onClick=onPersonalize){Text("设置我的个性化偏好")}
                Spacer(Modifier.height(24.dp))
                Text("管理会员",style=MaterialTheme.typography.titleLarge)
                Text("已有激活码可在此兑换。会员状态同步后自动生效。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp,bottom=12.dp))
                OutlinedButton(onClick={redeem=true;error=null},enabled=!busy,modifier=Modifier.heightIn(min=44.dp)){Text("兑换激活码")}
            }
        }
    }
    if(redeem)AlertDialog(onDismissRequest={if(!busy)redeem=false},title={Text("兑换激活码")},text={
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(code,{code=it;error=null},Modifier.fillMaxWidth(),singleLine=true,label={Text("激活码")},enabled=!busy,shape=MaterialTheme.shapes.medium)
            error?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
        }
    },confirmButton={TextButton(onClick={busy=true;model.redeemSubscription(code.trim(),{msg->busy=false;redeem=false;code="";message=msg;error=null},{msg->busy=false;error=msg})},enabled=code.isNotBlank()&&!busy){Text(if(busy)"正在兑换…" else "兑换")}},
        dismissButton={TextButton(onClick={redeem=false},enabled=!busy){Text("取消")}})
}
