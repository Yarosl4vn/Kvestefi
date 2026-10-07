package ru.readysquad.kvest.util

import android.content.Context
import android.content.Intent
import android.widget.Toast

/** Отправка результата в мессенджеры и соцсети. */
object Share {

    private const val VK = "com.vkontakte.android"
    private const val TG = "org.telegram.messenger"
    private const val TG_X = "org.thunderdog.challegram"

    fun text(score: Int, subject: String, correct: Int, total: Int, grade: Int): String {
        return "Я набрал $score очков в «Квестефи»!\n" +
            "$subject: $correct из $total верно — прогноз оценки «$grade».\n" +
            "Готовлюсь к ОГЭ, ЕГЭ и ВПР. Проверь себя!\n" +
            "#КвестМарафон #ОГЭ2026"
    }

    fun generic(c: Context, body: String) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, body)
            putExtra(Intent.EXTRA_SUBJECT, "Квест-марафон")
        }
        c.startActivity(Intent.createChooser(i, "Поделиться результатом"))
    }

    fun toVk(c: Context, body: String) = to(c, body, arrayOf(VK), "VK")

    fun toTelegram(c: Context, body: String) = to(c, body, arrayOf(TG, TG_X), "Telegram")

    private fun to(c: Context, body: String, packages: Array<String>, label: String) {
        val pm = c.packageManager
        for (p in packages) {
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                setPackage(p)
                putExtra(Intent.EXTRA_TEXT, body)
            }
            if (i.resolveActivity(pm) != null) {
                c.startActivity(i)
                return
            }
        }
        Toast.makeText(c, "$label не установлен — открываю общее меню", Toast.LENGTH_SHORT).show()
        generic(c, body)
    }
}
