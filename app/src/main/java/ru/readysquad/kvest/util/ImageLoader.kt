package ru.readysquad.kvest.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import ru.readysquad.kvest.R
import ru.readysquad.kvest.data.Http
import java.util.concurrent.Executors

/** Небольшой загрузчик картинок для материалов по теме: кэш в памяти + фоновые потоки. */
object ImageLoader {

    private val cache = LruCache<String, Bitmap>(24)
    private val io = Executors.newFixedThreadPool(3)
    private val ui = Handler(Looper.getMainLooper())

    /**
     * Заранее скачивает картинку в кэш.
     *
     * Нужно, чтобы рисунок следующего задания был готов до того, как пользователь
     * до него дойдёт: иначе карточка открывается с пустой рамкой.
     */
    fun prefetch(url: String?, referer: String? = null) {
        if (url.isNullOrBlank() || cache.get(url) != null) return
        io.execute {
            val bytes = if (referer.isNullOrBlank()) Http.getBytes(url) else Http.getImage(url, referer)
            val bitmap = bytes?.let {
                try {
                    BitmapFactory.decodeByteArray(it, 0, it.size)
                } catch (e: Exception) {
                    null
                }
            }
            if (bitmap != null) cache.put(url, bitmap)
        }
    }

    /**
     * Загружает картинку в фоне и подставляет её в [view].
     *
     * @param referer нужен для картинок открытых банков заданий: они отдают
     *   изображение только тому, кто пришёл со страницы задания
     * @param onFail вызывается, если картинку получить не удалось, —
     *   чтобы экран мог убрать пустую рамку
     */
    fun load(url: String, view: ImageView, referer: String? = null, onFail: (() -> Unit)? = null) {
        cache.get(url)?.let {
            view.setImageBitmap(it)
            return
        }
        view.setImageResource(R.drawable.bg_stat)

        io.execute {
            val bytes = if (referer.isNullOrBlank()) Http.getBytes(url) else Http.getImage(url, referer)
            val bitmap = bytes?.let {
                try {
                    BitmapFactory.decodeByteArray(it, 0, it.size)
                } catch (e: Exception) {
                    null
                }
            }
            if (bitmap != null) {
                cache.put(url, bitmap)
                ui.post { view.setImageBitmap(bitmap) }
            } else {
                ui.post { onFail?.invoke() }
            }
        }
    }
}
