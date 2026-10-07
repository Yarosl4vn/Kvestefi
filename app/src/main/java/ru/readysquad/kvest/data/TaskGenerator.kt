package ru.readysquad.kvest.data

import kotlin.random.Random

/**
 * Генератор заданий на русском языке.
 *
 * Бесплатного API с вопросами на русском нет, а англоязычные базы не подходят
 * для подготовки к ОГЭ. Поэтому вопросы для режима «Тренировка» строятся
 * на устройстве: числа подставляются в шаблон, правильный ответ считается
 * кодом, поэтому он всегда верный, а количество вопросов не ограничено.
 */
object TaskGenerator {

    private const val TOPIC = "Тренировка"
    private const val EXAM = "Тренировка"

    /** Для каких предметов умеем строить задания. */
    fun supports(subjectCode: String): Boolean = subjectCode in setOf(
        "math", "phys", "chem", "info", "geo"
    )

    /** Сгенерировать набор заданий. */
    fun generate(subjectCode: String, count: Int): List<Task> {
        val out = ArrayList<Task>(count)
        var guard = 0
        while (out.size < count && guard < count * 40) {
            guard++
            val task = when (subjectCode) {
                "math" -> mathTask()
                "phys" -> physTask()
                "chem" -> chemTask()
                "info" -> infoTask()
                "geo" -> geoTask()
                else -> null
            } ?: break
            if (out.none { it.question == task.question }) out.add(task)
        }
        return out
    }

    // ------------------------------------------------------------------ математика

    private fun mathTask(): Task = when (Random.nextInt(6)) {
        0 -> {
            val a = Random.nextInt(12, 90)
            val b = Random.nextInt(12, 90)
            numeric("math", "Алгебра", "Найдите значение выражения: $a + $b", a + b,
                "$a + $b = ${a + b}")
        }
        1 -> {
            val a = Random.nextInt(3, 15)
            val b = Random.nextInt(3, 12)
            numeric("math", "Алгебра", "Найдите значение выражения: $a · $b", a * b,
                "$a · $b = ${a * b}")
        }
        2 -> {
            val percent = listOf(10, 20, 25, 50, 75).random()
            val base = Random.nextInt(2, 21) * 40
            val answer = base * percent / 100
            numeric("math", "Задачи", "Найдите $percent% от числа $base", answer,
                "$base · $percent / 100 = $answer")
        }
        3 -> {
            val x = Random.nextInt(3, 40)
            val a = Random.nextInt(5, 30)
            numeric("math", "Уравнения", "Решите уравнение: x + $a = ${x + a}", x,
                "x = ${x + a} − $a = $x")
        }
        4 -> {
            val a = Random.nextInt(3, 20)
            val b = Random.nextInt(3, 20)
            numeric("math", "Геометрия", "Найдите площадь прямоугольника со сторонами $a см и $b см", a * b,
                "Площадь прямоугольника: $a · $b = ${a * b} см²")
        }
        else -> {
            // Четвёртое число подбираем так, чтобы сумма делилась на 4 —
            // иначе среднее арифметическое не целое и ответ нельзя считать верным.
            val first = List(3) { Random.nextInt(1, 20) }
            var last = Random.nextInt(1, 20)
            while ((first.sum() + last) % 4 != 0) last = Random.nextInt(1, 20)
            val numbers = first + last
            val sum = numbers.sum()
            val answer = sum / 4
            numeric("math", "Задачи", "Найдите среднее арифметическое чисел ${numbers.joinToString(", ")}",
                answer, "Сумма чисел ${sum}, делим на 4: $answer")
        }
    }

    // ------------------------------------------------------------------ физика

    private fun physTask(): Task = when (Random.nextInt(4)) {
        0 -> {
            val m = Random.nextInt(2, 16)
            numeric("phys", "Механика", "Найдите силу тяжести, действующую на тело массой $m кг (g = 10 Н/кг)",
                m * 10, "F = m · g = $m · 10 = ${m * 10} Н", unit = " Н")
        }
        1 -> {
            val t = Random.nextInt(2, 13)
            val v = Random.nextInt(2, 16)
            val s = v * t
            numeric("phys", "Механика", "Тело прошло $s м за $t с. Найдите скорость.", v,
                "v = S / t = $s / $t = $v м/с", unit = " м/с")
        }
        2 -> {
            val p = Random.nextInt(2, 20) * 10
            val t = Random.nextInt(2, 13)
            numeric("phys", "Электричество", "Какую работу совершит прибор мощностью $p Вт за $t секунд?",
                p * t, "A = P · t = $p · $t = ${p * t} Дж", unit = " Дж")
        }
        else -> {
            // Плотность в г/см³ и объём в см³ дают массу в граммах без дробей
            // и округлений — ответ всегда целый и проверяемый.
            val rho = listOf(1, 2, 3, 5, 8, 10).random()
            val v = listOf(2, 5, 10, 20, 50).random()
            val mass = rho * v
            numeric("phys", "Механика",
                "Найдите массу тела объёмом $v см³ при плотности $rho г/см³",
                mass, "m = ρ · V = $rho · $v = $mass г", unit = " г")
        }
    }

    // ------------------------------------------------------------------ химия

    private val compounds = listOf(
        "H₂O" to 18, "CO₂" to 44, "O₂" to 32, "N₂" to 28,
        "NaCl" to 58, "HCl" to 36, "CH₄" to 16, "CaCO₃" to 100, "H₂SO₄" to 98
    )

    private fun chemTask(): Task {
        val (formula, mr) = compounds.random()
        val others = compounds.filter { it.second != mr }.map { it.second }.shuffled().take(3)
        val options = (listOf(mr) + others).shuffled()
        return Task(
            id = 0,
            subjectCode = "chem",
            topic = TOPIC,
            exam = EXAM,
            difficulty = 1,
            question = "Найдите относительную молекулярную массу вещества $formula",
            options = options.map { it.toString() },
            correctIndex = options.indexOf(mr),
            explanation = "Складываем относительные атомные массы всех атомов в формуле $formula — получаем $mr."
        )
    }

    // ------------------------------------------------------------------ информатика

    private fun infoTask(): Task = when (Random.nextInt(3)) {
        0 -> {
            val n = Random.nextInt(5, 32)
            text("info", "Кодирование", "Запишите число $n в двоичной системе счисления",
                Integer.toBinaryString(n),
                listOf(
                    Integer.toBinaryString(n + 1),
                    Integer.toBinaryString(n + 2),
                    Integer.toBinaryString((n - 1).coerceAtLeast(1))
                ),
                "$n = ${Integer.toBinaryString(n)}₂")
        }
        1 -> {
            val bytes = Random.nextInt(2, 25)
            numeric("info", "Кодирование", "Сколько бит в $bytes байтах?", bytes * 8,
                "1 байт = 8 бит, значит $bytes · 8 = ${bytes * 8} бит", unit = " бит")
        }
        else -> {
            val kb = Random.nextInt(2, 12)
            numeric("info", "Кодирование", "Сколько байт в $kb Кбайтах?", kb * 1024,
                "1 Кбайт = 1024 байта, значит $kb · 1024 = ${kb * 1024} байт", unit = " байт")
        }
    }

    // ------------------------------------------------------------------ география

    private fun geoTask(): Task {
        val scale = listOf(10000, 25000, 50000, 100000).random()
        val cm = Random.nextInt(2, 13)
        val meters = cm.toLong() * scale / 100
        return numeric(
            "geo", "Карты",
            "Масштаб карты 1 : $scale. Отрезок на карте равен $cm см. Сколько метров на местности?",
            meters.toInt(),
            "В 1 см — $scale см = ${scale / 100} м. Значит $cm · ${scale / 100} = $meters м",
            unit = " м"
        )
    }

    // ------------------------------------------------------------------ сборка

    /** Задание с числовым ответом: неверные варианты — соседние числа. */
    private fun numeric(
        subject: String,
        topic: String,
        question: String,
        answer: Int,
        explanation: String,
        unit: String = ""
    ): Task {
        val options = LinkedHashSet<Int>()
        options.add(answer)
        val steps = listOf(1, -1, 2, -2, 5, -5, 10, -10, 100, -100)
        for (step in steps) {
            if (options.size >= 4) break
            val candidate = answer + step
            if (candidate == answer) continue
            if (candidate < 0 && answer >= 0) continue
            options.add(candidate)
        }
        var extra = 2
        while (options.size < 4) {
            options.add(answer + extra)
            extra++
        }
        val list = options.toList().shuffled()
        return Task(
            id = 0,
            subjectCode = subject,
            topic = topic,
            exam = EXAM,
            difficulty = 1,
            question = question,
            options = list.map { it.toString() + unit },
            correctIndex = list.indexOf(answer),
            explanation = explanation
        )
    }

    /** Задание с текстовым ответом: варианты задаются вручную. */
    private fun text(
        subject: String,
        topic: String,
        question: String,
        answer: String,
        distractors: List<String>,
        explanation: String
    ): Task {
        val options = (listOf(answer) + distractors.filter { it != answer }.distinct().take(3)).shuffled()
        return Task(
            id = 0,
            subjectCode = subject,
            topic = topic,
            exam = EXAM,
            difficulty = 1,
            question = question,
            options = options,
            correctIndex = options.indexOf(answer),
            explanation = explanation
        )
    }
}
