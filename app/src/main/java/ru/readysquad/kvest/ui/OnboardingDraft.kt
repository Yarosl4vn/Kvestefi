package ru.readysquad.kvest.ui

import ru.readysquad.kvest.util.Greetings

/**
 * Черновик настроек онбординга. Живёт в памяти процесса и переживает
 * пересоздание Activity (например, при переключении тёмной темы).
 */
object OnboardingDraft {
    var step: Int = 0
    var name: String = ""
    var avatar: String = ""
    var gender: String = Greetings.MALE
    var subjects: MutableSet<String> = HashSet()
    var topics: MutableSet<String> = HashSet()
    var goal: Int = 3

    fun reset() {
        step = 0
        name = ""
        avatar = ""
        gender = Greetings.MALE
        subjects = HashSet()
        topics = HashSet()
        goal = 3
    }
}
