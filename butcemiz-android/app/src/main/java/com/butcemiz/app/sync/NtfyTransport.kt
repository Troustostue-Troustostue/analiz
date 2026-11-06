package com.butcemiz.app.sync

import com.butcemiz.app.prefs.SyncConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class NtfyAttachment(val url: String = "", val size: Long = 0, val expires: Long = 0)

@Serializable
data class NtfyEvent(
    val id: String = "",
    val time: Long = 0,
    val event: String = "",
    val message: String? = null,
    val attachment: NtfyAttachment? = null,
)

sealed interface PublishResult {
    data object Ok : PublishResult
    data class RateLimited(val dailyQuota: Boolean, val message: String) : PublishResult
    data class Failed(val code: Int, val message: String) : PublishResult
}

class HttpStatusException(val code: Int, message: String) : IOException(message)

/**
 * ntfy (https://ntfy.sh) üzerinden yayınla/abone ol. ntfy yalnızca bir aktarıcıdır:
 * mesajları 12 saat önbellekte tutar, 4 KB üstü gövdeleri otomatik olarak ek dosyaya çevirir.
 */
class NtfyTransport(baseClient: OkHttpClient = OkHttpClient()) {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    private val client: OkHttpClient = baseClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    /** Akış bağlantısı: sunucu her ~45 sn'de bir "keepalive" gönderir. */
    private val streamClient: OkHttpClient = baseClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(110, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private fun base(cfg: SyncConfig) = cfg.server.trimEnd('/') + "/" + cfg.topic

    private fun Request.Builder.auth(cfg: SyncConfig): Request.Builder =
        if (!cfg.token.isNullOrBlank()) header("Authorization", "Bearer ${cfg.token}") else this

    suspend fun publish(cfg: SyncConfig, body: String): PublishResult = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(base(cfg))
            // Not: "Filename" başlığı GÖNDERİLMEZ; aksi halde küçük mesajlar da 3 saatlik eke dönüşür.
            // 4 KB üstü gövdeler sunucu tarafından otomatik olarak eke çevrilir.
            .post(body.toRequestBody("text/plain; charset=utf-8".toMediaType()))
            .auth(cfg)
            .build()
        try {
            client.newCall(req).execute().use { r ->
                val text = r.body?.string().orEmpty()
                when {
                    r.isSuccessful -> PublishResult.Ok
                    r.code == 429 -> PublishResult.RateLimited(text.contains("quota", ignoreCase = true), text.take(200))
                    else -> PublishResult.Failed(r.code, text.take(200))
                }
            }
        } catch (e: IOException) {
            PublishResult.Failed(-1, e.message ?: "Ağ hatası")
        }
    }

    /** Önbellekteki mesajları tek seferde çeker (arka plan işçisi için). */
    suspend fun poll(cfg: SyncConfig, since: String): List<NtfyEvent> = withContext(Dispatchers.IO) {
        val url = (base(cfg) + "/json").toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("poll", "1")
            .addQueryParameter("since", since)
            .build()
        val req = Request.Builder().url(url).get().auth(cfg).build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw HttpStatusException(r.code, "poll ${r.code}")
            val body = r.body?.string().orEmpty()
            body.lineSequence().filter { it.isNotBlank() }.mapNotNull { parse(it) }.toList()
        }
    }

    /** Canlı akış. Toplayıcı iptal edildiğinde bağlantı kapanır. */
    fun stream(cfg: SyncConfig, since: String): Flow<NtfyEvent> = callbackFlow {
        val url = (base(cfg) + "/json").toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("since", since)
            .build()
        val call = streamClient.newCall(Request.Builder().url(url).get().auth(cfg).build())
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                close(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { r ->
                    if (!r.isSuccessful) {
                        close(HttpStatusException(r.code, "stream ${r.code}"))
                        return
                    }
                    try {
                        val src = r.body!!.source()
                        while (true) {
                            val line = src.readUtf8Line() ?: break
                            if (line.isBlank()) continue
                            val ev = parse(line) ?: continue
                            if (trySendBlocking(ev).isFailure) break
                        }
                        close()
                    } catch (e: IOException) {
                        close(e)
                    }
                }
            }
        })
        awaitClose { call.cancel() }
    }

    suspend fun download(cfg: SyncConfig, url: String): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).get().auth(cfg).build()
            client.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
        } catch (e: IOException) {
            null
        }
    }

    private fun parse(line: String): NtfyEvent? = try {
        json.decodeFromString(NtfyEvent.serializer(), line)
    } catch (e: Exception) {
        null
    }
}
