package cn.zhzgo.study.network

import android.util.Base64
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import java.io.IOException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Ephemeral per-request keys. No server storage secret is shipped in the APK. */
class QuestionTransportInterceptor : Interceptor {
    companion object {
        private const val VERSION = "study-question-v1"
        private val dataPath = Regex("^/api/data/(?:questions(?:/|$)|mistakes/?$|favorites(?:/practice)?/?$|recommendation/?$|progress/?$|exams/(?:records/?$|\\d+/(?:start|submit)/?$))", RegexOption.IGNORE_CASE)
        private val adminPath = Regex("^/api/admin/(?:questions|reports|exams)(?:/|$)", RegexOption.IGNORE_CASE)
        private fun encode(value: ByteArray) = Base64.encodeToString(value, Base64.NO_WRAP)
        private fun decode(value: String) = Base64.decode(value, Base64.NO_WRAP)
        private fun hex(value: ByteArray) = value.joinToString("") { "%02x".format(it.toInt() and 255) }
        private fun hmac(key: ByteArray, data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256")); doFinal(data)
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!dataPath.containsMatchIn(request.url.encodedPath) && !adminPath.containsMatchIn(request.url.encodedPath)) return chain.proceed(request)
        val pair = KeyPairGenerator.getInstance("EC").run { initialize(ECGenParameterSpec("secp256r1")); generateKeyPair() }
        val nonce = hex(ByteArray(16).also { SecureRandom().nextBytes(it) })
        val path = request.url.encodedPath + (request.url.encodedQuery?.let { "?$it" } ?: "")
        val tokenHash = hex(MessageDigest.getInstance("SHA-256").digest((request.header("Authorization") ?: "").toByteArray(Charsets.UTF_8)))
        val aad = "$VERSION\n${request.method}\n$path\n$nonce\n$tokenHash".toByteArray(Charsets.UTF_8)
        val response = chain.proceed(request.newBuilder().header("X-Study-Key", encode(pair.public.encoded)).header("X-Study-Nonce", nonce).build())
        if (!response.isSuccessful) return response
        try {
            val envelope = JSONObject(response.body?.string() ?: throw IOException("题库响应为空"))
            if (envelope.optString("protected") != VERSION || envelope.optString("request") != nonce) throw IOException("题库安全通道校验失败")
            val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(decode(envelope.getString("key"))))
            val shared = KeyAgreement.getInstance("ECDH").run { init(pair.private); doPhase(publicKey, true); generateSecret() }
            val prk = hmac(decode(envelope.getString("salt")), shared)
            shared.fill(0)
            val key = hmac(prk, aad + byteArrayOf(1))
            prk.fill(0)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, decode(envelope.getString("iv"))))
            cipher.updateAAD(aad)
            key.fill(0)
            val plain = cipher.doFinal(decode(envelope.getString("data")))
            return response.newBuilder().body(plain.toResponseBody("application/json; charset=utf-8".toMediaType())).build()
        } catch (error: Exception) {
            response.close()
            throw IOException("题库安全通道校验失败，请更新 App 或重试", error)
        }
    }
}
