package ru.readysquad.kvest.data

import org.json.JSONObject
import java.net.URLEncoder

/**
 * Материалы для повторения: открытый каталог курсов Stepik.
 *
 * Публичный интерфейс `stepik.org/api/courses` не требует ключа и отдаёт
 * название, описание, обложку и число записавшихся. Курсы не скачиваются и не
 * сохраняются: пользователь открывает их на сайте Stepik по ссылке.
 *
 * Все методы блокирующие — вызывать только из фонового потока.
 */
object Stepik {

    private const val API = "https://stepik.org/api/courses"

    data class Course(
        val id: Long,
        val title: String,
        val summary: String,
        val url: String,
        val cover: String?,
        val learners: Int,
        val isPaid: Boolean
    )

    data class Result(val courses: List<Course>, val error: String? = null)

    /**
     * Ищет курсы по запросу.
     *
     * Каталог иногда отвечает не с первого раза, поэтому одна повторная попытка
     * делается автоматически. Если и она не прошла, в тексте ошибки возвращаем
     * причину от [Http], чтобы было понятно: нет сети, таймаут или код ответа.
     */
    fun search(query: String, limit: Int = 8): Result {
        val q = query.trim()
        if (q.isEmpty()) return Result(emptyList(), "Пустой запрос")
        val url = try {
            "$API?search=" + URLEncoder.encode(q, "UTF-8") + "&page=1"
        } catch (e: Exception) {
            return Result(emptyList(), "Не удалось подготовить запрос")
        }

        var reason: String? = null
        repeat(2) { attempt ->
            val body = Http.get(url)
            if (body != null) {
                val result = parse(body, limit)
                if (result.courses.isNotEmpty()) return result
                reason = result.error
            } else {
                reason = Http.lastError
            }
            if (attempt == 0) Thread.sleep(600)
        }
        return Result(
            emptyList(),
            "Stepik не ответил" + if (reason.isNullOrBlank()) "" else " ($reason)"
        )
    }

    /** Разбор ответа Stepik. Битые записи пропускаем. */
    fun parse(body: String, limit: Int = 8): Result {
        val courses = ArrayList<Course>()
        try {
            val root = JSONObject(body)
            val array = root.optJSONArray("courses")
                ?: return Result(emptyList(), "Пустой ответ Stepik")
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val title = o.optString("title").trim()
                if (title.isEmpty()) continue
                val id = o.optLong("id", 0L)
                if (id == 0L) continue
                courses.add(
                    Course(
                        id = id,
                        title = title,
                        summary = o.optString("summary").trim().replace(Regex("\\s+"), " "),
                        url = "https://stepik.org/course/$id/promo",
                        cover = if (o.isNull("cover")) null else o.optString("cover").trim().ifBlank { null },
                        learners = o.optInt("learners_count", 0),
                        isPaid = o.optBoolean("is_paid", false)
                    )
                )
            }
        } catch (e: Exception) {
            return Result(emptyList(), "Не удалось прочитать ответ Stepik")
        }
        courses.sortByDescending { it.learners }
        return Result(courses.take(limit))
    }
}
