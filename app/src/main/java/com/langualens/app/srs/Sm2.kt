package com.langualens.app.srs

import com.langualens.app.data.SavedItem
import kotlin.math.max
import kotlin.math.roundToInt

/** Grades match Anki's four buttons. */
enum class Grade(val q: Int) { AGAIN(0), HARD(3), GOOD(4), EASY(5) }

object Sm2 {

    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun apply(item: SavedItem, grade: Grade): SavedItem {
        var ease = item.ease
        var interval = item.intervalDays
        var reps = item.reps
        var lapses = item.lapses

        if (grade == Grade.AGAIN) {
            reps = 0
            lapses += 1
            interval = 0
            ease = max(1.3, ease - 0.2)
            return item.copy(
                ease = ease,
                intervalDays = interval,
                reps = reps,
                lapses = lapses,
                dueAt = System.currentTimeMillis() + 10 * 60 * 1000 // 10 minutes
            )
        }

        reps += 1
        interval = when {
            reps == 1 -> 1
            reps == 2 -> if (grade == Grade.EASY) 6 else 3
            else -> {
                val multiplier = when (grade) {
                    Grade.HARD -> 1.2
                    Grade.EASY -> ease * 1.3
                    else -> ease
                }
                max(1.0, interval * multiplier).roundToInt()
            }
        }

        val q = grade.q
        ease = max(1.3, ease + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02)))

        return item.copy(
            ease = ease,
            intervalDays = interval,
            reps = reps,
            lapses = lapses,
            dueAt = System.currentTimeMillis() + interval * DAY_MS
        )
    }

    fun previewLabel(item: SavedItem, grade: Grade): String {
        val next = apply(item, grade)
        val days = ((next.dueAt - System.currentTimeMillis()) / DAY_MS.toDouble())
        return when {
            days < 1 -> "10m"
            days < 30 -> "${days.roundToInt()}d"
            else -> "${(days / 30).roundToInt()}mo"
        }
    }
}
