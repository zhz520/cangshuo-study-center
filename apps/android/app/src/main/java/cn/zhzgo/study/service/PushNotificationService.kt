package cn.zhzgo.study.service

import android.content.Context
import android.content.Intent
import cn.jpush.android.api.CmdMessage
import cn.jpush.android.api.CustomMessage
import cn.jpush.android.api.JPushInterface
import cn.jpush.android.api.JPushMessage
import cn.jpush.android.api.NotificationMessage
import cn.jpush.android.service.JPushMessageReceiver
import cn.zhzgo.study.MainActivity
import cn.zhzgo.study.data.FcmTokenRequest
import cn.zhzgo.study.network.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

class PushNotificationService : JPushMessageReceiver() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onRegister(context: Context, registrationId: String) {
        super.onRegister(context, registrationId)
        // Send Registration ID to server
        scope.launch {
            try {
                val apiService = RetrofitClient.create(context)
                apiService.postFcmToken(FcmTokenRequest(registrationId))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onNotifyMessageOpened(context: Context, message: NotificationMessage) {
        super.onNotifyMessageOpened(context, message)
        try {
            val extras = message.notificationExtras
            if (!extras.isNullOrEmpty()) {
                val json = JSONObject(extras)
                val type = json.optString("type")
                val articleId = json.optString("articleId")

                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    if (type == "article_comment" && articleId.isNotEmpty()) {
                        putExtra("navigate_to", "article_detail")
                        putExtra("article_id", articleId.toInt())
                    }
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
