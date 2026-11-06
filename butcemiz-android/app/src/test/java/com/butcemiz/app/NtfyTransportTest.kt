package com.butcemiz.app

import com.butcemiz.app.prefs.SyncConfig
import com.butcemiz.app.sync.NtfyTransport
import com.butcemiz.app.sync.PublishResult
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ntfy HTTP katmanı: yanıt örnekleri ntfy.sh'tan alınan gerçek biçimlerdir
 * ("open" olayı, 429 günlük kota hatası, 4 KB üstü gövdenin eke dönüşmesi).
 */
class NtfyTransportTest {
    private val server = MockWebServer()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.shutdown()

    private fun cfg(token: String? = null) = SyncConfig(server.url("/").toString().trimEnd('/'), "btzTopic123456789012345678901", "k", token)

    @Test
    fun publishSendsPlainBodyWithoutFilename() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":"QIYVPz0nkBfa","time":1793970977,"event":"message","topic":"t","message":"x"}"""))
        val r = NtfyTransport().publish(cfg("tk_secret"), "BTZ1.abc")
        assertEquals(PublishResult.Ok, r)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/btzTopic123456789012345678901", req.path)
        assertEquals("BTZ1.abc", req.body.readUtf8())
        assertEquals("Bearer tk_secret", req.getHeader("Authorization"))
        // Filename başlığı küçük mesajları 3 saatlik eke çevirirdi
        assertNull(req.getHeader("X-Filename"))
        assertNull(req.getHeader("Filename"))
    }

    @Test
    fun dailyQuotaErrorIsRecognized() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(429)
                .setBody("""{"code":42908,"http":429,"error":"limit reached: daily message quota reached; increase your limits with a paid plan, see https://ntfy.sh","link":"https://ntfy.sh/docs/publish/#limitations"}"""),
        )
        val r = NtfyTransport().publish(cfg(), "x")
        assertTrue(r is PublishResult.RateLimited && r.dailyQuota)
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"code":42901,"http":429,"error":"limit reached: too many requests"}"""))
        val r2 = NtfyTransport().publish(cfg(), "x")
        assertTrue(r2 is PublishResult.RateLimited && !r2.dailyQuota)
        server.enqueue(MockResponse().setResponseCode(500))
        assertTrue(NtfyTransport().publish(cfg(), "x") is PublishResult.Failed)
    }

    @Test
    fun pollParsesMessagesAndAttachments() = runBlocking {
        val body = """
            {"id":"QIYVPz0nkBfa","time":1793970977,"expires":1794014177,"event":"message","topic":"t","message":"BTZ1.small"}
            {"id":"PjOU230vjhjs","time":1793970977,"expires":1794014177,"event":"message","topic":"t","message":"You received a file: attachment.txt","attachment":{"name":"attachment.txt","type":"text/plain; charset=utf-8","size":5001,"expires":1793981777,"url":"https://ntfy.sh/file/PjOU230vjhjs.txt"}}
        """.trimIndent()
        server.enqueue(MockResponse().setBody(body))
        val events = NtfyTransport().poll(cfg(), "1793970900")
        val req = server.takeRequest()
        assertTrue(req.path!!.startsWith("/btzTopic123456789012345678901/json?"))
        assertTrue(req.path!!.contains("poll=1") && req.path!!.contains("since=1793970900"))
        assertEquals(2, events.size)
        assertEquals("BTZ1.small", events[0].message)
        assertEquals("https://ntfy.sh/file/PjOU230vjhjs.txt", events[1].attachment?.url)
        assertEquals(1793970977L, events[1].time)
    }

    @Test
    fun streamEmitsOpenThenCachedThenLive() = runBlocking {
        val body = listOf(
            """{"id":"9xy3u0XwaYL8","time":1793971024,"event":"open","topic":"t"}""",
            """{"id":"a1","time":1793971025,"event":"message","topic":"t","message":"BTZ1.one"}""",
            """{"id":"k1","time":1793971030,"event":"keepalive","topic":"t"}""",
            "bozuk satır",
            """{"id":"a2","time":1793971031,"event":"message","topic":"t","message":"BTZ1.two"}""",
        ).joinToString("\n") + "\n"
        server.enqueue(MockResponse().setBody(body).setChunkedBody(body, 37))
        val events = withTimeout(10_000) { NtfyTransport().stream(cfg(), "all").toList() }
        assertEquals(listOf("open", "message", "keepalive", "message"), events.map { it.event })
        assertEquals(listOf("BTZ1.one", "BTZ1.two"), events.filter { it.event == "message" }.map { it.message })
        assertTrue(server.takeRequest().path!!.contains("since=all"))
    }

    @Test
    fun streamFailsOnHttpError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        val r = runCatching { withTimeout(10_000) { NtfyTransport().stream(cfg(), "all").toList() } }
        assertTrue(r.isFailure)
    }

    @Test
    fun downloadsAttachment() = runBlocking {
        server.enqueue(MockResponse().setBody("BTZ1.large-encrypted-payload"))
        val text = NtfyTransport().download(cfg(), server.url("/file/abc.txt").toString())
        assertEquals("BTZ1.large-encrypted-payload", text)
        server.enqueue(MockResponse().setResponseCode(404))
        assertNull(NtfyTransport().download(cfg(), server.url("/file/expired.txt").toString()))
    }
}
