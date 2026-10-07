package ru.readysquad.kvest.data

/**
 * Источник заданий.
 *
 * Сейчас работает только офлайн-версия: банк заданий лежит в [SeedData] и попадает
 * в таблицу `tasks` при создании базы.
 *
 * Официального API у ФИПИ нет — открытые банки заданий (ege.fipi.ru/bank и
 * oge.fipi.ru/bank) это веб-приложения без публичного интерфейса. Чтобы подключить
 * реальный источник, достаточно реализовать этот интерфейс и записать полученные
 * задания в таблицу `tasks` — весь остальной код приложения менять не придётся.
 *
 * Варианты реализации:
 *  1. Свой backend-парсер банка ФИПИ (BeautifulSoup / lxml) + кэш в своей БД.
 *  2. Готовый MCP-сервер для банка ФИПИ, например github.com/MasterGiGiK/fipi-mcp.
 *  3. ИИ-генерация задач по кодификатору ФИПИ — с сохранением в ту же таблицу.
 */
interface TaskSource {

    /** Короткое имя источника, показывается в интерфейсе. */
    val name: String

    /** Умеет ли источник работать без сети. */
    val isOffline: Boolean

    /** Получить набор заданий по предмету. */
    fun load(subjectCode: String, limit: Int): List<Task>
}

/** Офлайн-источник: встроенный банк заданий из [SeedData]. */
class OfflineTaskSource(private val repository: Repository) : TaskSource {

    override val name: String = "Встроенный банк"
    override val isOffline: Boolean = true

    override fun load(subjectCode: String, limit: Int): List<Task> =
        repository.shuffledTasks(subjectCode, limit)
}
