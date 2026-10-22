package cn.zhzgo.study.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.zhzgo.study.data.UserProfileResponse
import cn.zhzgo.study.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import cn.zhzgo.study.data.UserPreferences

class UserProfileViewModel : ViewModel() {
    private val _profile = MutableStateFlow<UserProfileResponse?>(null)
    val profile: StateFlow<UserProfileResponse?> = _profile.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun fetchProfile(context: Context, userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val api = RetrofitClient.create(context)
                val response = api.getUserProfile(userId)
                _profile.value = response
                
                // Sync to local preferences if it's the current user
                val prefs = UserPreferences(context)
                val currentUserName = prefs.userName.first() ?: ""
                if (currentUserName == response.user.username || userId == "me") {
                    response.user.subscription_end_date?.let { 
                        prefs.setSubscriptionEndDate(it)
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "获取用户信息失败"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun redeemSubscription(context: Context, code: String, userId: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val api = RetrofitClient.create(context)
                val response = api.redeemSubscription(cn.zhzgo.study.data.SubscriptionRedeemRequest(code))
                onSuccess(response.message)
                
                // Update local storage immediately
                response.subscription_end_date?.let {
                    UserPreferences(context).setSubscriptionEndDate(it)
                }
                
                // Refresh profile to get new date
                fetchProfile(context, userId)
            } catch (e: retrofit2.HttpException) {
                val errorMsg = try {
                    val jObjError = org.json.JSONObject(e.response()?.errorBody()?.string() ?: "")
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
}
