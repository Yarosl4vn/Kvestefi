package ru.readysquad.kvest.data

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * Небольшой HTTP-клиент для открытых API. Все методы блокирующие — только из фонового потока.
 *
 * Важно: значения заголовков обязаны быть ASCII. Android проверяет их и на строку
 * с кириллицей выбрасывает исключение — запрос не уходит вообще. Раньше в
 * User-Agent стояло «учебное приложение», из-за чего часть API молча не отвечала.
 *
 * [lastError] хранит причину последней неудачи, чтобы экран мог показать её
 * пользователю, а не общее «проверь интернет».
 */
object Http {

    private const val UA = "Kvestefi/1.7 (Android; educational app)"

    /** Открытые банки заданий отдают содержимое только обычному браузеру. */
    private const val BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/122.0 Safari/537.36"

    private const val CONNECT_TIMEOUT = 12_000
    private const val READ_TIMEOUT = 25_000

    /** Причина последней неудачи: код ответа, таймаут или тип исключения. */
    @Volatile
    var lastError: String? = null
        private set

    /** Скачивает текст по ссылке. null — если сети нет или сервер ответил ошибкой. */
    fun get(url: String): String? = fetch(url, UA, "application/json", null, "utf-8")

    /**
     * Скачивает HTML-страницу от имени браузера.
     * Так работают открытые банки заданий: они отдают разметку только обычному клиенту.
     */
    fun getHtml(url: String, referer: String? = null): String? =
        fetch(url, BROWSER_UA, "text/html,application/xhtml+xml", referer, null)

    /** Картинки банков заданий тоже скачиваем как браузер. */
    fun getImage(url: String, referer: String? = null): ByteArray? = fetchBytes(url, BROWSER_UA, "image/*", referer)

    /** Скачивает двоичные данные, например картинку. */
    fun getBytes(url: String): ByteArray? = fetchBytes(url, UA, "*/*", null)

    // ------------------------------------------------------------------ внутри

    private fun fetch(
        url: String,
        ua: String,
        accept: String,
        referer: String?,
        charset: String?
    ): String? {
        val bytes = fetchBytes(url, ua, accept, referer) ?: return null
        if (charset != null) return String(bytes, java.nio.charset.Charset.forName(charset))
        // у HTML кодировку берём из заголовка, иначе считаем UTF-8
        val declared = lastCharset
        return try {
            String(bytes, java.nio.charset.Charset.forName(declared ?: "utf-8"))
        } catch (e: Exception) {
            String(bytes, Charsets.UTF_8)
        }
    }

    @Volatile
    private var lastCharset: String? = null

    private fun fetchBytes(url: String, ua: String, accept: String, referer: String?): ByteArray? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", ua)
                setRequestProperty("Accept", accept)
                setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9")
                // без явного запрета сервер может отдать gzip, а разбирать его придётся вручную
                setRequestProperty("Accept-Encoding", "identity")
                if (!referer.isNullOrBlank()) setRequestProperty("Referer", referer)
            }
            val code = conn.responseCode
            lastCharset = charsetOf(conn.contentType)
            if (code !in 200..299) {
                lastError = "сервер ответил $code"
                return null
            }
            val encoding = conn.contentEncoding
            val raw = conn.inputStream
            val bytes = if (encoding != null && encoding.contains("gzip", true)) {
                GZIPInputStream(raw).use { readAll(it) }
            } else {
                readAll(raw)
            }
            if (bytes.isEmpty()) {
                lastError = "пустой ответ сервера"
                return null
            }
            lastError = null
            bytes
        } catch (e: java.net.SocketTimeoutException) {
            lastError = "превышено время ожидания"
            null
        } catch (e: Exception) {
            lastError = when (e) {
                is java.net.UnknownHostException -> "нет соединения"
                is java.net.ConnectException -> "сервер недоступен"
                else -> "ошибка: " + (e.javaClass.simpleName ?: "неизвестно")
            }
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun readAll(stream: java.io.InputStream): ByteArray {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        while (true) {
            val n = stream.read(chunk)
            if (n <= 0) break
            buffer.write(chunk, 0, n)
        }
        stream.close()
        return buffer.toByteArray()
    }

    private fun charsetOf(contentType: String?): String? = try {
        contentType?.substringAfter("charset=", "")?.trim()?.takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        null
    }
}
