package cn.zhzgo.study.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

sealed class NetworkEvent {
    data class ShowMessage(val message: String) : NetworkEvent()
    object ShowNoNetwork : NetworkEvent()
}

object GlobalMessageManager {
    private val lastNetworkMessage = java.util.concurrent.atomic.AtomicLong(0)
    private val _events = MutableSharedFlow<NetworkEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun showMessage(message: String) {
        _events.tryEmit(NetworkEvent.ShowMessage(message))
    }

    fun showNoNetwork() {
        val now = android.os.SystemClock.elapsedRealtime()
        val previous = lastNetworkMessage.get()
        if (previous != 0L && now - previous < 4000) return
        if (!lastNetworkMessage.compareAndSet(previous, now)) return
        _events.tryEmit(NetworkEvent.ShowNoNetwork)
    }
}

class NoNetworkException(message: String) : IOException(message)

class ConnectivityInterceptor(private val context: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (!isNetworkAvailable(context)) {
            GlobalMessageManager.showNoNetwork()
            throw NoNetworkException("网络连接不可用")
        }
        
        try {
            val response = chain.proceed(chain.request())
            if (!response.isSuccessful) {
                val errorMsg = when (response.code) {
                    401 -> "登录已过期，请重新登录"
                    403 -> "权限不足"
                    404 -> "资源不存在"
                    500 -> "服务器异常"
                    else -> "网络请求失败 (${response.code})"
                }
                // We don't necessarily want to snackbar EVERY API error (some might be handled locally)
                // but for 500 or 401 it's often useful.
                if (response.code >= 500 || response.code == 401) {
                    GlobalMessageManager.showMessage(errorMsg)
                }
            }
            return response
        } catch (e: IOException) {
            val errorMsg = if (e is java.net.SocketTimeoutException) "连接超时，请重试" else "连接服务器失败，请稍后重试"
            GlobalMessageManager.showMessage(errorMsg)
            throw e
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
