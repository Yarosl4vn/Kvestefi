package ru.readysquad.kvest.data

import org.json.JSONObject

/**
 * Картинки по теме из Wikimedia Commons — свободный API без ключа:
 * https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrnamespace=6&prop=imageinfo
 *
 * Отдаём только растровые превью: для SVG сервис сам отдаёт PNG-миниатюру.
 */
object CommonsApi {

    private const val API = "https://commons.wikimedia.org/w/api.php"
    private val allowed = listOf(".jpg", ".jpeg", ".png", ".svg", ".gif")

    class Picture(val url: String, val title: String)

    fun images(query: String, limit: Int = 6): List<Picture> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()

        val url = API +
            "?action=query&generator=search&gsrsearch=" + enc(q) +
            "&gsrnamespace=6&gsrlimit=" + (limit * 2).coerceIn(2, 20) +
            "&prop=imageinfo&iiprop=url&iiurlwidth=640&format=json&utf8=1"

        val body = Http.get(url) ?: return emptyList()
        val pages = JSONObject(body).optJSONObject("query")?.optJSONObject("pages") ?: return emptyList()

        val out = ArrayList<Picture>()
        val keys = pages.keys()
        while (keys.hasNext()) {
            val page = pages.optJSONObject(keys.next()) ?: continue
            val title = page.optString("title")
            val lower = title.lowercase()
            if (allowed.none { lower.endsWith(it) }) continue

            val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
            val raw = info.optString("thumburl").ifBlank { info.optString("url") }
            if (raw.isBlank()) continue

            // убираем служебные параметры вроде utm_source
            val clean = raw.substringBefore('?')
            if (out.none { it.url == clean }) {
                out.add(Picture(clean, title.removePrefix("File:")))
            }
            if (out.size >= limit) break
        }
        return out
    }

    private fun enc(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")
}
