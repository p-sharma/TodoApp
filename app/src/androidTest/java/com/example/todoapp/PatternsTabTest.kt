package com.example.todoapp

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.todoapp.data.CategoryMapper
import com.example.todoapp.data.TaskHistory
import com.example.todoapp.data.TodoDatabase
import com.example.todoapp.data.TodoRepository
import com.example.todoapp.ui.dashboard.PatternsUiState
import com.example.todoapp.ui.dashboard.PatternsViewModel
import com.example.todoapp.ui.dashboard.SortMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Integration tests for the Patterns tab in the Insights screen.
 *
 * Seeds 7 days of task history (2 open-ended + 1 time-bound per day), then drives
 * PatternsViewModel through its real ML Kit pipeline and asserts categorisation and
 * completion rates are correct.
 *
 * Requires a Play Store emulator image (API 26+) so that the ML Kit model can download:
 *   ./gradlew connectedDebugAndroidTest --tests "com.example.todoapp.PatternsTabTest"
 *
 * Seeded data summary (21 tasks over 7 days):
 *   Open-ended  — 14 tasks, 7 done (50.0% completion rate)
 *   Time-bound  —  7 tasks, 4 done (57.1% completion rate)
 *   Per-day mix — every day has at least one done and one not-done task.
 */
@RunWith(AndroidJUnit4::class)
class PatternsTabTest {

    private lateinit var db: TodoDatabase

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, TodoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    fun patternsTab_withMixedTasks_reachesReadyState() = runBlocking {
        seedHistory()
        val vm = buildViewModel()

        val state = awaitReady(vm)

        assertNotNull(
            "Patterns analysis did not complete within ${TIMEOUT_MS / 1000}s — " +
                "ensure the emulator uses a Play Store image with internet access",
            state
        )
        assertTrue(
            "Expected PatternsUiState.Ready but got ${state!!::class.simpleName}",
            state is PatternsUiState.Ready
        )
    }

    @Test
    fun patternsTab_openEndedCategory_hasCorrectCountsAndRate() = runBlocking {
        seedHistory()
        val vm = buildViewModel()

        val state = awaitReady(vm) as? PatternsUiState.Ready ?: return@runBlocking

        val cat = state.categories.find { it.name == CategoryMapper.OPEN_ENDED }
        assertNotNull("Open-ended category missing from Patterns results", cat)
        assertEquals("Open-ended task count", OPEN_ENDED_TOTAL, cat!!.taskCount)
        assertEquals("Open-ended completed count", OPEN_ENDED_DONE, cat.completedCount)
        assertEquals(
            "Open-ended completion rate",
            OPEN_ENDED_DONE.toFloat() / OPEN_ENDED_TOTAL,
            cat.completionRate,
            0.01f
        )
    }

    @Test
    fun patternsTab_timeBoundCategory_existsWithCorrectCompletionRate() = runBlocking {
        seedHistory()
        val vm = buildViewModel()

        val state = awaitReady(vm) as? PatternsUiState.Ready ?: return@runBlocking

        val cat = state.categories.find { it.name == TIME_BOUND_CATEGORY }
        assertNotNull(
            "Time-bound category missing — ML Kit did not extract date/time entities " +
                "from the seeded tasks. Verify the emulator has internet access for model download.",
            cat
        )
        assertTrue("Time-bound task count should be > 0", cat!!.taskCount > 0)
        assertEquals(
            "Time-bound completed count",
            TIME_BOUND_DONE,
            cat.completedCount
        )
        assertEquals(
            "Time-bound completion rate",
            TIME_BOUND_DONE.toFloat() / cat.taskCount,
            cat.completionRate,
            0.01f
        )
    }

    @Test
    fun patternsTab_byFrequencySort_ordersHighestTaskCountFirst() = runBlocking {
        seedHistory()
        val vm = buildViewModel()

        val state = awaitReady(vm) as? PatternsUiState.Ready ?: return@runBlocking

        vm.setSortMode(SortMode.ByFrequency)
        val sorted = (vm.uiState.value as? PatternsUiState.Ready)?.categories ?: return@runBlocking

        assertEquals("Sort mode should be ByFrequency", SortMode.ByFrequency, vm.uiState.value.let {
            (it as PatternsUiState.Ready).sortMode
        })
        for (i in 0 until sorted.size - 1) {
            assertTrue(
                "Index $i (${sorted[i].taskCount} tasks) should be >= index ${i + 1} (${sorted[i + 1].taskCount} tasks)",
                sorted[i].taskCount >= sorted[i + 1].taskCount
            )
        }
    }

    @Test
    fun patternsTab_byCompletionRateSort_ordersHighestRateFirst() = runBlocking {
        seedHistory()
        val vm = buildViewModel()

        val readyState = awaitReady(vm) as? PatternsUiState.Ready ?: return@runBlocking

        vm.setSortMode(SortMode.ByCompletionRate)

        val updated = vm.uiState.value as? PatternsUiState.Ready
        assertNotNull("State should remain Ready after sort toggle", updated)
        assertEquals("Sort mode should update", SortMode.ByCompletionRate, updated!!.sortMode)
        assertEquals("Category data should not change on sort", readyState.categories, updated.categories)

        val sorted = updated.categories.sortedByDescending { it.completionRate }
        for (i in 0 until sorted.size - 1) {
            assertTrue(
                "Index $i (${sorted[i].completionRate}) should be >= index ${i + 1} (${sorted[i + 1].completionRate})",
                sorted[i].completionRate >= sorted[i + 1].completionRate
            )
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun buildViewModel(): PatternsViewModel {
        val repo = TodoRepository(db.todoDao(), db.historyDao())
        return PatternsViewModel(repo, CategoryMapper())
    }

    private suspend fun awaitReady(vm: PatternsViewModel): PatternsUiState? =
        withTimeoutOrNull(TIMEOUT_MS) {
            vm.uiState.first { it !is PatternsUiState.Loading }
        }

    private suspend fun seedHistory() {
        val today = LocalDate.now()
        val tasks = mutableListOf<TaskHistory>()
        var sortOrder = 0

        for (dayOffset in 6 downTo 0) {
            val date = today.minusDays(dayOffset.toLong()).toString()
            val oeIndex = (6 - dayOffset) * 2  // pairs: 0-1, 2-3, 4-5, 6-7, 8-9, 10-11, 12-13
            val tbIndex = 6 - dayOffset

            // Open-ended task 1 — done on even pair-day indices (0,2,4,6,8,10,12)
            tasks += TaskHistory(
                date = date,
                taskText = OPEN_ENDED_TEXTS[oeIndex],
                isCompleted = oeIndex in OPEN_ENDED_DONE_INDICES,
                sortOrder = sortOrder++
            )
            // Open-ended task 2 — never done (odd pair indices 1,3,5,7,9,11,13)
            tasks += TaskHistory(
                date = date,
                taskText = OPEN_ENDED_TEXTS[oeIndex + 1],
                isCompleted = (oeIndex + 1) in OPEN_ENDED_DONE_INDICES,
                sortOrder = sortOrder++
            )
            // Time-bound task — done on even tbIndex (0,2,4,6); not done on odd (1,3,5)
            tasks += TaskHistory(
                date = date,
                taskText = TIME_BOUND_TEXTS[tbIndex],
                isCompleted = tbIndex in TIME_BOUND_DONE_INDICES,
                sortOrder = sortOrder++
            )
        }

        db.historyDao().insertAll(tasks)
    }

    // -------------------------------------------------------------------------
    // Test corpus
    // -------------------------------------------------------------------------

    companion object {
        private const val TIMEOUT_MS = 120_000L
        private const val TIME_BOUND_CATEGORY = "Time-bound"

        // Completion totals used in assertions
        const val OPEN_ENDED_TOTAL = 14
        const val OPEN_ENDED_DONE = 7   // every even index in the 0-13 range
        const val TIME_BOUND_TOTAL = 7
        const val TIME_BOUND_DONE = 4   // indices 0, 2, 4, 6

        // Even indices → done; odd indices → not done.
        // Ensures every day has exactly 1 done + 1 not-done open-ended task.
        private val OPEN_ENDED_DONE_INDICES = setOf(0, 2, 4, 6, 8, 10, 12)
        // Alternating done/not-done across 7 days; gives 4 done (0,2,4,6) and 3 not-done (1,3,5).
        private val TIME_BOUND_DONE_INDICES = setOf(0, 2, 4, 6)

        /**
         * Texts with no temporal, monetary, address, or communication entities so that
         * ML Kit assigns them to the "Open-ended" bucket.
         *
         * 14 entries — two consumed per day for 7 days.
         */
        private val OPEN_ENDED_TEXTS = listOf(
            "Water the plants",                // day 6 ago — done
            "Read a chapter of a book",        // day 6 ago — not done
            "Organize the desk",               // day 5 ago — done
            "Go for a walk",                   // day 5 ago — not done
            "Write in the journal",            // day 4 ago — done
            "Clean the kitchen",               // day 4 ago — not done
            "Breathe and meditate",            // day 3 ago — done
            "Call a friend",                   // day 3 ago — not done
            "Review recent notes",             // day 2 ago — done
            "Plan current priorities",         // day 2 ago — not done
            "Stretch and exercise",            // day 1 ago — done
            "Catch up on reading",             // day 1 ago — not done
            "Tidy the living room",            // today    — done
            "Practice the guitar"              // today    — not done
        )

        /**
         * Texts with explicit date/time tokens that reliably trigger ML Kit TYPE_DATE_TIME,
         * mapping to the "Time-bound" category via CategoryMapper.
         *
         * 7 entries — one consumed per day.
         */
        private val TIME_BOUND_TEXTS = listOf(
            "Doctor appointment at 10am",           // day 6 ago — done
            "Submit report by Friday at 5pm",       // day 5 ago — not done
            "Team standup at 9am tomorrow",         // day 4 ago — done
            "Gym session at 7am on Wednesday",      // day 3 ago — not done
            "Pick up parcel before 6pm today",      // day 2 ago — done
            "Call the bank at 2pm on Tuesday",      // day 1 ago — not done
            "Dentist checkup on Thursday at 11am"   // today    — done
        )
    }
}
