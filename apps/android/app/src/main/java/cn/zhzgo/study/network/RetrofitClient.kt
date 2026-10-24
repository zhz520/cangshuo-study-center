package cn.zhzgo.study.network

import android.content.Context
import cn.zhzgo.study.data.RefreshRequest
import cn.zhzgo.study.data.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.HttpException

class AuthInterceptor(private val userPreferences: UserPreferences) : Interceptor {
    companion object { private val lock = Any() }

    override fun intercept(chain: Interceptor.Chain): Response {
        val (token, originalRefresh) = runBlocking { userPreferences.sessionTokens.first() }
        
        val requestBuilder = chain.request().newBuilder()
        if (!token.isNullOrEmpty()) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }
        val response = chain.proceed(requestBuilder.build())
        
        // If 401 and this is NOT the refresh endpoint itself, try to refresh
        if (response.code == 401 && !token.isNullOrBlank() &&
            chain.request().url.encodedPath !in setOf("/api/auth/refresh", "/api/auth/login", "/api/auth/register", "/api/auth/qq-login")) {
            synchronized(lock) {
                // Re-check token — another thread may have refreshed it
                val currentToken = runBlocking { userPreferences.authToken.first() }
                if (currentToken == token) {
                    // Token hasn't changed, we need to refresh
                    val refreshToken = runBlocking { userPreferences.refreshToken.first() }
                    if (!refreshToken.isNullOrEmpty() && refreshToken == originalRefresh) {
                        try {
                            val refreshApi = createRefreshApi()
                            val refreshResponse = runBlocking {
                                refreshApi.refreshToken(RefreshRequest(refreshToken))
                            }
                            // Save new access token
                            val updated = runBlocking { userPreferences.replaceAccessToken(refreshToken, refreshResponse.token) }
                            if (!updated) return response
                            // Close the old response and retry with new token
                            response.close()
                            val newRequest = chain.request().newBuilder()
                                .header("Authorization", "Bearer ${refreshResponse.token}")
                                .build()
                            return chain.proceed(newRequest)
                        } catch (e: Exception) {
                            // A timeout or 5xx must not discard an otherwise valid session.
                            if (e is HttpException && e.code() in listOf(400, 401, 403)) {
                                runBlocking { userPreferences.clearAuthToken(refreshToken) }
                            } else {
                                response.close()
                                throw java.io.IOException("暂时无法刷新登录状态，请重试", e)
                            }
                        }
                    } else {
                        // No refresh token, clear auth
                        if (refreshToken.isNullOrEmpty() && originalRefresh.isNullOrEmpty()) runBlocking { userPreferences.clearAuthToken() }
                    }
                } else if (!currentToken.isNullOrBlank() && runBlocking { userPreferences.refreshToken.first() } == originalRefresh) {
                    // Token was refreshed by another thread, retry with new token
                    response.close()
                    val newRequest = chain.request().newBuilder()
                        .header("Authorization", "Bearer $currentToken")
                        .build()
                    return chain.proceed(newRequest)
                }
            }
        }
        return response
    }

    private fun createRefreshApi(): ApiService {
        return Retrofit.Builder()
            .baseUrl(RetrofitClient.BASE_URL)
            .client(OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .callTimeout(25, java.util.concurrent.TimeUnit.SECONDS).build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}

object RetrofitClient {
    const val BASE_URL = "https://studyapi.zhzgo.cn/"

    fun create(context: Context): ApiService {
        cachedApi?.let { return it }
        return synchronized(this) {
            cachedApi ?: build(context.applicationContext).also { cachedApi = it }
        }
    }

    @Volatile private var cachedApi: ApiService? = null

    private fun build(context: Context): ApiService {
        val logging = HttpLoggingInterceptor().apply {
            redactHeader("Authorization")
            val debug = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            level = if (debug) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        
        val userPreferences = UserPreferences(context)

        val client = OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor(ConnectivityInterceptor(context))
            .addInterceptor(logging)
            .addInterceptor(AuthInterceptor(userPreferences))
            .addInterceptor(QuestionTransportInterceptor())
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
