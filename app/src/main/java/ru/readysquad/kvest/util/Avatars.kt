package ru.readysquad.kvest.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/** Своя иконка профиля: копируется во внутреннее хранилище, чтобы не зависеть от галереи. */
object Avatars {

    private const val FILE_NAME = "avatar_custom.png"
    private const val MAX_SIDE = 512

    /**
     * Сохранить выбранное изображение. Возвращает имя файла или null при ошибке.
     *
     * Фотография из галереи может быть на десятки мегапикселей, поэтому сначала
     * считаем её размеры и декодируем с понижением: иначе на телефоне не хватает
     * памяти, декодер возвращает null, и «своя картинка» молча не ставится.
     */
    fun save(context: Context, uri: Uri): String? {
        try {
            // первый проход: только размеры
            val probe = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, probe) }
            if (probe.outWidth <= 0 || probe.outHeight <= 0) return null

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(probe.outWidth, probe.outHeight)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return null

            val scaled = scaleDown(decoded)
            val file = File(context.filesDir, FILE_NAME)
            FileOutputStream(file).use { out ->
                scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (scaled != decoded) scaled.recycle()
            decoded.recycle()
            return FILE_NAME
        } catch (e: OutOfMemoryError) {
            return null
        } catch (e: Exception) {
            return null
        }
    }

    /** Во сколько раз уменьшить при декодировании, чтобы влезть в [MAX_SIDE]. */
    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        var longest = maxOf(width, height)
        while (longest / 2 >= MAX_SIDE) {
            longest /= 2
            sample *= 2
        }
        return sample
    }

    /** Загрузить сохранённую иконку. null — если своя иконка не задана. */
    fun load(context: Context, name: String?): Bitmap? {
        if (name.isNullOrBlank()) return null
        val file = File(context.filesDir, name)
        if (!file.exists()) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
    }

    /**
     * Круглая версия иконки — для круглых мест в интерфейсе.
     *
     * Раньше снимок целиком растягивался в квадрат, и портретное фото выглядело
     * сплюснутым, а при неудачных пропорциях круг читался как овал. Теперь сначала
     * вырезается квадрат по центру, и только потом он маскируется кругом.
     */
    fun circleBitmap(source: Bitmap, size: Int): Bitmap {
        val safeSize = size.coerceAtLeast(1)
        val output = Bitmap.createBitmap(safeSize, safeSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val radius = safeSize / 2f

        paint.color = Color.BLACK
        canvas.drawCircle(radius, radius, radius, paint)

        val square = centerSquare(source)
        val scaled = if (square.width == safeSize) {
            square
        } else {
            Bitmap.createScaledBitmap(square, safeSize, safeSize, true)
        }
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(scaled, 0f, 0f, paint)

        if (scaled != square && scaled != source) scaled.recycle()
        if (square != source) square.recycle()
        return output
    }

    /** Квадрат по центру изображения — чтобы пропорции не искажались. */
    private fun centerSquare(source: Bitmap): Bitmap {
        val side = minOf(source.width, source.height)
        if (side <= 0) return source
        if (source.width == side && source.height == side) return source
        val left = (source.width - side) / 2
        val top = (source.height - side) / 2
        return Bitmap.createBitmap(source, left, top, side, side)
    }

    private fun scaleDown(source: Bitmap): Bitmap {
        val max = maxOf(source.width, source.height)
        if (max <= MAX_SIDE) return source
        val ratio = MAX_SIDE.toFloat() / max
        return Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }
}
