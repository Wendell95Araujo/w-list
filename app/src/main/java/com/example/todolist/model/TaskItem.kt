package com.example.todolist.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

enum class TaskDateCategory {
    OVERDUE,
    TODAY,
    TOMORROW,
    THIS_WEEK,
    THIS_MONTH,
    NEXT_MONTHS,
    COMPLETED
}

@Entity(tableName = "tasks_table")
data class TaskItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    @ColumnInfo(name = "nameNormalized") val nameNormalized: String = normalize(name),
    val description: String,
    val datetime: String,
    var isCompleted: Boolean = false,
    val displayOrder: Long = System.currentTimeMillis()
) {
    companion object {
        fun normalize(text: String): String {
            return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replace(Regex("\\p{InCombiningDiacriticalMarks}"), "")
                .lowercase()
        }
    }

    fun getDateCategory(): TaskDateCategory {
        if (isCompleted) {
            return TaskDateCategory.COMPLETED
        }
        if (datetime.isBlank()) {
            return TaskDateCategory.NEXT_MONTHS
        }

        return try {
            val format = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val taskCalendar = Calendar.getInstance().apply { time = format.parse(datetime)!! }
            val now = Calendar.getInstance()

            val todayStart = Calendar.getInstance().apply { clearTime() }
            val tomorrowStart = (todayStart.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
            val dayAfterTomorrowStart = (tomorrowStart.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }

            val endOfWeek = (todayStart.clone() as Calendar).apply {
                set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                add(Calendar.WEEK_OF_YEAR, 1)
            }

            val endOfMonth = (todayStart.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.MONTH, 1)
            }

            when {
                taskCalendar.before(now) -> TaskDateCategory.OVERDUE
                taskCalendar.before(tomorrowStart) -> TaskDateCategory.TODAY
                taskCalendar.before(dayAfterTomorrowStart) -> TaskDateCategory.TOMORROW
                taskCalendar.before(endOfWeek) -> TaskDateCategory.THIS_WEEK
                taskCalendar.before(endOfMonth) -> TaskDateCategory.THIS_MONTH
                else -> TaskDateCategory.NEXT_MONTHS
            }
        } catch (e: Exception) {
            TaskDateCategory.NEXT_MONTHS
        }
    }
}

private fun Calendar.clearTime() {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}