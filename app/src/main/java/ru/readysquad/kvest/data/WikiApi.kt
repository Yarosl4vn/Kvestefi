package ru.readysquad.kvest.data

import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/**
 * Материалы по темам из свободных проектов Викимедиа. Ключ не нужен.
 *
 *  - Википедия — статьи с коротким вступлением (3 предложения, без «воды»):
 *    https://ru.wikipedia.org/w/api.php?action=query&generator=search&prop=extracts&exsentences=3
 *  - Викисловарь — толкование слова, берём только раздел «Значение»:
 *    https://ru.wiktionary.org/w/api.php?action=query&titles=…&prop=extracts&explaintext=1
 *  - краткая справка по статье: https://ru.wikipedia.org/api/rest_v1/page/summary/{title}
 *
 * Картинки по теме — см. [CommonsApi].
 * Все методы блокирующие, вызывать только из фонового потока.
 */
object WikiApi {

    private const val API = "https://ru.wikipedia.org/w/api.php"
    private const val REST = "https://ru.wikipedia.org/api/rest_v1/page/summary/"
    private const val WIKT = "https://ru.wiktionary.org/w/api.php"

    /** Сколько знаков оставляем в карточке, чтобы не было «воды». */
    private const val SHORT_TEXT = 420

    class Hit(val title: String, val extract: String, val url: String)

    // ------------------------------------------------------------------ Википедия

    fun search(query: String, limit: Int = 12): List<Hit> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()

        val url = API +
            "?action=query&generator=search&gsrsearch=" + enc(q) +
            "&gsrlimit=" + limit.coerceIn(1, 20) +
            "&prop=extracts&exsentences=3&explaintext=1&format=json&utf8=1"

        val body = get(url) ?: throw IOException("Нет ответа от ru.wikipedia.org")
        val pages = JSONObject(body)
            .optJSONObject("query")
            ?.optJSONObject("pages")
            ?: return emptyList()

        val collected = ArrayList<Triple<Int, String, String>>()
        val keys = pages.keys()
        while (keys.hasNext()) {
            val page = pages.optJSONObject(keys.next()) ?: continue
            val title = page.optString("title")
            if (title.isEmpty()) continue
            collected.add(Triple(page.optInt("index", 99), title, shorten(clean(page.optString("extract")))))
        }
        return collected.sortedBy { it.first }.map { Hit(it.second, it.third, articleUrl(it.second)) }
    }

    fun summary(title: String): String {
        val body = get(REST + enc(title.replace(' ', '_'))) ?: throw IOException("Нет ответа от ru.wikipedia.org")
        return shorten(clean(JSONObject(body).optString("extract")))
    }

    fun articleUrl(title: String): String =
        "https://ru.wikipedia.org/wiki/" + enc(title.replace(' ', '_'))

    // ------------------------------------------------------------------ Викисловарь

    /**
     * Короткое толкование слова. Возвращает пустую строку, если статьи нет
     * или раздел «Значение» не найден.
     */
    fun definition(word: String): String {
        val w = word.trim()
        if (w.isEmpty()) return ""
        val url = WIKT +
            "?action=query&titles=" + enc(w) +
            "&prop=extracts&explaintext=1&redirects=1&format=json&utf8=1"

        val body = get(url) ?: throw IOException("Нет ответа от ru.wiktionary.org")
        val pages = JSONObject(body).optJSONObject("query")?.optJSONObject("pages") ?: return ""
        val keys = pages.keys()
        if (!keys.hasNext()) return ""
        val extract = pages.optJSONObject(keys.next())?.optString("extract").orEmpty()
        return meanings(extract)
    }

    /** Достаёт строки раздела «Значение» — остальные разделы пропускаем. */
    private fun meanings(extract: String): String {
        if (extract.isBlank()) return ""
        val out = ArrayList<String>()
        var inside = false

        // обычный цикл, а не forEach: нужен break при выходе из раздела «Значение»
        for (raw in extract.split("\n")) {
            val line = raw.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("=")) {
                if (inside) break
                if (line.contains("Значение", ignoreCase = true)) inside = true
                continue
            }
            if (!inside) continue
            if (out.size >= 3) break

            // примеры идут после знака ◆ — их не показываем
            val value = line.substringBefore("◆").trim().trimEnd(',', ';').trim()
            if (value.isNotEmpty()) out.add("• " + value)
        }
        return out.joinToString("\n")
    }

    // ------------------------------------------------------------------ служебное

    private fun get(url: String): String? = Http.get(url)

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    /**
     * Убирает из текста формулы-обломки: блоки {\displaystyle ...},
     * служебные символы LaTeX и лишние переводы строк.
     */
    fun clean(raw: String): String {
        if (raw.isEmpty()) return ""
        val marker = "{\\displaystyle"
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            if (raw.startsWith(marker, i)) {
                var depth = 0
                var j = i + marker.length
                while (j < raw.length) {
                    val c = raw[j]
                    when {
                        c == '{' -> { depth++; sb.append(c) }
                        c == '}' -> {
                            if (depth == 0) { j++; break } else { depth--; sb.append(c) }
                        }
                        else -> sb.append(c)
                    }
                    j++
                }
                i = j
            } else {
                sb.append(raw[i])
                i++
            }
        }
        return sb.toString()
            .replace("\\", "")
            .replace("{", " ")
            .replace("}", " ")
            .replace("^", "")
            .replace("_", " ")
            .replace("~", " ")
            .replace(Regex("[ \\t\\u00A0]+"), " ")
            .replace(Regex("\n[ \\t]*"), "\n")
            .replace(Regex("\n{2,}"), "\n")
            .trim()
    }

    /** Обрезаем по границе предложения, чтобы карточка оставалась короткой. */
    private fun shorten(text: String): String {
        if (text.length <= SHORT_TEXT) return text
        val cut = text.substring(0, SHORT_TEXT)
        val stop = cut.lastIndexOfAny(charArrayOf('.', '!', '?', '\n'))
        val body = if (stop > SHORT_TEXT / 2) cut.substring(0, stop + 1) else cut
        return body.trimEnd() + "…"
    }
}
