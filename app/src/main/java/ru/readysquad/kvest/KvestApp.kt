package ru.readysquad.kvest

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import ru.readysquad.kvest.data.Prefs
import ru.readysquad.kvest.data.Repository

class KvestApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val prefs = Prefs(this)
        AppCompatDelegate.setDefaultNightMode(prefs.themeMode)
        // Прогреваем базу: первый запрос создаёт схему и наполняет банк заданий.
        Repository.get(this)
    }
}
