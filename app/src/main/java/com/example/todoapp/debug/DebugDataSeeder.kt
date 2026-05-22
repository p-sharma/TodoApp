package com.example.todoapp.debug

import com.example.todoapp.data.HistoryDao
import com.example.todoapp.data.TaskHistory
import com.example.todoapp.data.TodoDao
import com.example.todoapp.data.TodoTask
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seeds both database tables with realistic sample data for manual UI testing.
 *
 * Calling seed() wipes the current contents of daily_tasks and task_history,
 * then inserts 7 days of history (2 open-ended + 1 time-bound per day) and
 * 5 tasks for today. Intended only for debug builds.
 *
 * History stats after seeding:
 *   Open-ended  — 14 tasks, 10 done (~71% completion)
 *   Time-bound  —  7 tasks,  4 done (~57% completion)
 */
@Singleton
class DebugDataSeeder @Inject constructor(
    private val todoDao: TodoDao,
    private val historyDao: HistoryDao
) {

    suspend fun seed() {
        val today = LocalDate.now()

        todoDao.deleteAll()
        historyDao.deleteAll()

        historyDao.insertAll(buildHistory(today))

        TODAY_TASKS.forEachIndexed { index, (text, done) ->
            todoDao.insert(
                TodoTask(
                    text = text,
                    isCompleted = done,
                    sortOrder = index,
                    createdDate = today.toString()
                )
            )
        }
    }

    private fun buildHistory(today: LocalDate): List<TaskHistory> {
        val tasks = mutableListOf<TaskHistory>()
        var order = 0
        HISTORY.forEach { (daysAgo, entries) ->
            val date = today.minusDays(daysAgo).toString()
            entries.forEach { (text, done) ->
                tasks += TaskHistory(date = date, taskText = text, isCompleted = done, sortOrder = order++)
            }
        }
        return tasks
    }

    companion object {

        // Per-day entries: (task text, isCompleted)
        // Each day has 2 open-ended tasks and 1 time-bound task.
        // Every day has at least one done and one not-done entry.
        private val HISTORY = listOf(
            7L to listOf(
                "Water the plants"           to true,
                "Read a book chapter"        to true,
                "Doctor appointment at 10am" to true
            ),
            6L to listOf(
                "Organize the desk"               to true,
                "Go for a walk"                   to false,
                "Submit report by Friday at 5pm"  to false
            ),
            5L to listOf(
                "Write in the journal"          to true,
                "Clean the kitchen"             to true,
                "Team standup at 9am tomorrow"  to true
            ),
            4L to listOf(
                "Breathe and meditate"                to false,
                "Call a friend"                       to true,
                "Gym session at 7am on Wednesday"     to false
            ),
            3L to listOf(
                "Review personal notes"              to true,
                "Plan current priorities"            to true,
                "Pick up parcel before 6pm today"    to true
            ),
            2L to listOf(
                "Stretch and exercise"              to false,
                "Catch up on reading"               to true,
                "Call the bank at 2pm on Tuesday"   to false
            ),
            1L to listOf(
                "Tidy the living room"                    to true,
                "Practice the guitar"                     to false,
                "Dentist checkup on Thursday at 11am"     to true
            )
        )

        // Today's active tasks in daily_tasks: (text, isCompleted)
        private val TODAY_TASKS = listOf(
            "Clear inbox and archive old messages"  to false,
            "Write weekly summary notes"            to false,
            "Prepare materials for next meeting"    to false,
            "Quick sync call at 2pm today"          to true,
            "Code review by end of day Friday"      to false
        )
    }
}
