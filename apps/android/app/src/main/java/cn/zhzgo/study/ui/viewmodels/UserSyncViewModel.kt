package cn.zhzgo.study.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.zhzgo.study.data.UserPreferences
import cn.zhzgo.study.network.ApiService
import cn.zhzgo.study.network.RetrofitClient
import cn.jpush.android.api.JPushInterface
import cn.zhzgo.study.data.FcmTokenRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class UserSyncViewModel(application: Application) : AndroidViewModel(application) {
    private val apiService = RetrofitClient.create(application)
    private val userPreferences = UserPreferences(application)
    
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    private var hasSyncedThisSession = false

    fun syncUserIfNeeded() {
        if (hasSyncedThisSession) return
        
        viewModelScope.launch {
            val token = userPreferences.authToken.first()
            if (token.isNullOrEmpty()) return@launch

            _isSyncing.value = true
            try {
                // Fetch the "me" profile to get the latest subscription status
                val response = apiService.getUserProfile("me")
                
                // Update local status with all available fields
                val u = response.user
                userPreferences.saveUser(
                    cn.zhzgo.study.data.User(
                        id = u.id,
                        username = u.username,
                        role = u.role,
                        avatar_icon = u.avatar_icon,
                        nickname = u.nickname,
                        slogan = u.slogan,
                        qq_openid = u.qq_openid,
                        email = u.email,
                        subscription_end_date = u.subscription_end_date
                    )
                )
                
                hasSyncedThisSession = true
            } catch (e: Exception) {
                // Silently fail, we'll try again next time or when manually viewing profile
                e.printStackTrace()
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun syncPushToken(registrationId: String? = null) {
        viewModelScope.launch {
            val token = userPreferences.authToken.first()
            if (token.isNullOrEmpty()) return@launch

            val rid = registrationId ?: JPushInterface.getRegistrationID(getApplication())
            if (rid.isNullOrEmpty()) {
                android.util.Log.e("UserSyncViewModel", "Push RID is empty, skipping sync")
                return@launch
            }

            try {
                apiService.postFcmToken(FcmTokenRequest(rid))
                android.util.Log.d("UserSyncViewModel", "Push RID synced successfully: $rid")
            } catch (e: Exception) {
                android.util.Log.e("UserSyncViewModel", "Failed to sync Push RID: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}
