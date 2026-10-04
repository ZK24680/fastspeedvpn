package com.example.mywarpvpn.data.warp

import android.os.Build
import com.wireguard.crypto.KeyPair
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit
import java.util.Date
import java.util.Locale

/**
 * Experimental consumer WARP registration using Cloudflare's undocumented client endpoint.
 * This deliberately creates a fresh WireGuard identity for this installation; no bootstrap key
 * or pre-shared account token is included in the app.
 */
class CloudflareWarpRegistrar {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
        .callTimeout(TOTAL_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
        .build()

    fun createWireGuardConfig(): String {
        val keys = KeyPair()
        val privateKey = keys.privateKey.toBase64()
        val registration = request(
            method = "POST",
            path = "/reg",
            payload = JSONObject()
                .put("install_id", "")
                .put("tos", timestamp())
                .put("key", keys.publicKey.toBase64())
                .put("fcm_token", "")
                .put("type", "Android")
                .put("model", deviceModel())
                .put("locale", Locale.getDefault().toLanguageTag()),
        )

        val registrationId = registration.optString("id").takeIf(String::isNotBlank)
            ?: throw WarpRegistrationException()
        val accessToken = registration.optString("token").takeIf(String::isNotBlank)
            ?: throw WarpRegistrationException()

        request(
            method = "PATCH",
            path = "/reg/$registrationId",
            accessToken = accessToken,
            payload = JSONObject().put("warp_enabled", true),
        )
        val profile = request(
            method = "GET",
            path = "/reg/$registrationId",
            accessToken = accessToken,
        )
        if (!profile.optBoolean("warp_enabled", false)) throw WarpRegistrationException()
        return makeConfig(profile, privateKey)
    }

    private fun makeConfig(profile: JSONObject, privateKey: String): String {
        val config = profile.optJSONObject("config") ?: throw WarpRegistrationException()
        val addresses = config.optJSONObject("interface")
            ?.optJSONObject("addresses") ?: throw WarpRegistrationException()
        val ipv4 = addresses.optString("v4").takeIf(String::isNotBlank)
            ?: throw WarpRegistrationException()
        val ipv6 = addresses.optString("v6").takeIf(String::isNotBlank)
            ?: throw WarpRegistrationException()
        val peer = config.optJSONArray("peers")?.optJSONObject(0)
            ?: throw WarpRegistrationException()
        val serverKey = peer.optString("public_key").takeIf(String::isNotBlank)
            ?: throw WarpRegistrationException()
        val endpointObject = peer.optJSONObject("endpoint") ?: throw WarpRegistrationException()
        val endpoint = endpointObject.optString("host").takeIf(String::isNotBlank)
            ?: endpointObject.optString("v4").takeIf(String::isNotBlank)?.let { "$it:2408" }
            ?: endpointObject.optString("v6").takeIf(String::isNotBlank)?.let { "[$it]:2408" }
            ?: throw WarpRegistrationException()

        return """[Interface]
            |PrivateKey = $privateKey
            |Address = $ipv4, $ipv6
            |DNS = 1.1.1.1, 1.0.0.1
            |MTU = 1280
            |
            |[Peer]
            |PublicKey = $serverKey
            |AllowedIPs = 0.0.0.0/0, ::/0
            |Endpoint = $endpoint
            |PersistentKeepalive = 25
            |""".trimMargin()
    }

    private fun request(
        method: String,
        path: String,
        accessToken: String? = null,
        payload: JSONObject? = null,
    ): JSONObject {
        val builder = Request.Builder()
            .url(API_BASE + path)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .header("CF-Client-Version", CLIENT_VERSION)
        accessToken?.let { builder.header("Authorization", "Bearer $it") }
        val body = payload?.toString()?.toRequestBody(JSON_MEDIA_TYPE)
        val request = builder.method(method, body).build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                val statusCode = response.code
                val responseStream = response.body?.byteStream()
                    ?: throw WarpRegistrationException(statusCode)
                val responseText = responseStream.use { it.readUtf8Bounded() }
                if (!response.isSuccessful) throw WarpRegistrationException(statusCode)
                try {
                    JSONObject(responseText)
                } catch (_: Exception) {
                    throw WarpRegistrationException(statusCode)
                }
            }
        } catch (error: WarpRegistrationException) {
            throw error
        } catch (_: Exception) {
            throw WarpRegistrationException()
        }
    }

    private fun timestamp(): String = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        Locale.US,
    ).format(Date())

    private fun deviceModel(): String = "${Build.MANUFACTURER} ${Build.MODEL}"
        .trim()
        .take(MAX_MODEL_LENGTH)

    private fun InputStream.readUtf8Bounded(): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var total = 0
        try {
            while (true) {
                val read = read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_RESPONSE_BYTES) throw WarpRegistrationException()
                output.write(buffer, 0, read)
            }
            return output.toString(Charsets.UTF_8.name())
        } finally {
            buffer.fill(0)
            output.toByteArray().fill(0)
        }
    }

    companion object {
        private const val API_BASE = "https://api.cloudflareclient.com/v0a1922"
        private const val CLIENT_VERSION = "a-6.3-1922"
        private const val USER_AGENT = "FastSpeed-Android/1.0"
        private const val TIMEOUT_MS = 20_000
        private const val TOTAL_TIMEOUT_MS = 45_000
        private const val MAX_RESPONSE_BYTES = 256 * 1024
        private const val MAX_MODEL_LENGTH = 128
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

class WarpRegistrationException(val httpStatus: Int? = null) : Exception()
