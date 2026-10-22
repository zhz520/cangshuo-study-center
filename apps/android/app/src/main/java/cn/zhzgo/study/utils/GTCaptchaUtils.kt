package cn.zhzgo.study.utils

import android.content.Context
import android.util.Log
import com.geetest.captcha.GTCaptcha4Client
import com.geetest.captcha.GTCaptcha4Config
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class GeetestResult(
    val lotNumber: String,
    val captchaOutput: String,
    val passToken: String,
    val genTime: String,
    val clientType: String = "android"
)

object GTCaptchaUtils {
    const val CAPTCHA_ID = "7cd4c51c66ac575453d118a8bb489715"
    private const val TAG = "GTCaptchaUtils"

    suspend fun verify(context: Context): GeetestResult = suspendCancellableCoroutine { continuation ->
        val config = GTCaptcha4Config.Builder()
            .setCanceledOnTouchOutside(true)
            .build()
            
        val client = GTCaptcha4Client.getClient(context)
        
        client.init(CAPTCHA_ID, config)
            .addOnSuccessListener { status, response ->
                if (status) {
                    try {
                        val json = if (response is JSONObject) response else JSONObject(response.toString())
                        val result = GeetestResult(
                            lotNumber = json.optString("lot_number", ""),
                            captchaOutput = json.optString("captcha_output", ""),
                            passToken = json.optString("pass_token", ""),
                            genTime = json.optString("gen_time", "")
                        )
                        continuation.resume(result)
                    } catch (e: Exception) {
                        Log.e(TAG, "Parsing geetest data failed", e)
                        continuation.resumeWithException(Exception("解析极验数据失败"))
                    }
                } else {
                    continuation.resumeWithException(Exception("行为验证失败"))
                }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Geetest Verify Error: $error")
                continuation.resumeWithException(Exception("验证错误"))
            }
        
        continuation.invokeOnCancellation {
            client.destroy()
        }
        
        client.verifyWithCaptcha()
    }
}
