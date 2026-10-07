package ru.readysquad.kvest.data

/**
 * Реальное экзаменационное задание из открытого банка.
 *
 * Отличается от захардкоженной [Task] тем, что это настоящая формулировка
 * с настоящим ответом, разбором, рисунком и ссылкой на источник.
 * Ответ — краткий: его вписывают в клетки бланка ответов № 1.
 *
 * Задания не хранятся в приложении: их отдаёт [Sdamgia] в момент решения.
 */
data class RealTask(
    val id: Long,
    val subjectCode: String,
    val exam: String,
    val topic: String,
    val number: String,
    val condition: String,
    val answer: String,
    val solution: String,
    val image: String?,
    val url: String
) : java.io.Serializable {
    val hasImage: Boolean get() = !image.isNullOrBlank()
}
