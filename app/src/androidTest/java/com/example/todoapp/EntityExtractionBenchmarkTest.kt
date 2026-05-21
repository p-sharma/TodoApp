package com.example.todoapp

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validates that ML Kit entity extraction meets the 300ms p95 latency target on API 26+.
 *
 * Run on a Play Store emulator image (API 26) so that ML Kit model download succeeds:
 *   ./gradlew connectedDebugAndroidTest --tests "com.example.todoapp.EntityExtractionBenchmarkTest"
 *
 * Outcome:
 *   PASS — confirms API 26 as minimum supported version.
 *   FAIL — p95 >= 300ms; escalate to Product with options listed in the assertion message.
 */
@RunWith(AndroidJUnit4::class)
class EntityExtractionBenchmarkTest {

    @Test
    fun entityExtraction_p95_isUnder300ms() = runBlocking {
        val extractor = EntityExtraction.getClient(
            EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build()
        )

        try {
            extractor.downloadModelIfNeeded().await()
        } catch (e: Exception) {
            fail(
                "ML Kit model download failed — ensure the emulator uses a Play Store image " +
                    "with internet access. Cause: ${e.message}"
            )
            return@runBlocking
        }

        // 20 representative strings cycling across all entity types the app uses.
        val corpus = listOf(
            "Buy groceries from the supermarket",
            "Call mom this afternoon",
            "Submit the quarterly report by Friday at 5pm",
            "Meeting with design team at 3pm tomorrow",
            "Pick up package at 123 Main Street",
            "Email john.doe@example.com about the project proposal",
            "Pay \$50 gym membership fee",
            "Transfer €100 to savings account",
            "Read two chapters of the new book",
            "Schedule dentist appointment for next Tuesday",
            "Review pull requests before end of day",
            "Book flight to New York for conference on June 15th",
            "Send invoice to client@company.com",
            "Pay electricity bill of \$120",
            "Drop off dry cleaning at 456 Oak Avenue",
            "Prepare slides for Monday morning presentation",
            "Order birthday cake online before Thursday",
            "Check IBAN DE89370400440532013000 balance",
            "Water the plants",
            "Write daily journal entry"
        )

        val totalTasks = DAYS * TASKS_PER_DAY  // 600
        val inputs = List(totalTasks) { i -> corpus[i % corpus.size] }

        // Warm up before measuring: gives JIT a chance to compile the hot path.
        repeat(WARMUP_COUNT) { i ->
            extractor.annotate(corpus[i % corpus.size]).await()
        }

        val timingsNs = LongArray(totalTasks)
        for (i in 0 until totalTasks) {
            val start = System.nanoTime()
            extractor.annotate(inputs[i]).await()
            timingsNs[i] = System.nanoTime() - start
        }

        extractor.close()

        timingsNs.sort()
        val p50Ms = timingsNs[totalTasks / 2] / NS_PER_MS
        val p95Ms = timingsNs[totalTasks * 95 / 100] / NS_PER_MS
        val minMs = timingsNs.first() / NS_PER_MS
        val maxMs = timingsNs.last() / NS_PER_MS

        Log.i(TAG, "=== ML Kit Entity Extraction Benchmark ===")
        Log.i(TAG, "Corpus: $totalTasks tasks (${DAYS}d × ${TASKS_PER_DAY}/day), warmup=$WARMUP_COUNT")
        Log.i(TAG, "min=${minMs}ms  p50=${p50Ms}ms  p95=${p95Ms}ms  max=${maxMs}ms")
        Log.i(TAG, "Target: p95 < ${TARGET_P95_MS}ms — ${if (p95Ms < TARGET_P95_MS) "PASS" else "FAIL"}")

        if (p95Ms >= TARGET_P95_MS) {
            fail(
                "Entity extraction p95 latency is ${p95Ms}ms (target < ${TARGET_P95_MS}ms, " +
                    "p50=${p50Ms}ms). Escalate to Product with one of:\n" +
                    "  a) Raise minimum API level above 26\n" +
                    "  b) Reduce the analysis window on older devices\n" +
                    "  c) Adjust the 300ms performance target"
            )
        }
    }

    companion object {
        private const val TAG = "MLKitBenchmark"
        private const val DAYS = 60
        private const val TASKS_PER_DAY = 10
        private const val WARMUP_COUNT = 5
        private const val TARGET_P95_MS = 300L
        private const val NS_PER_MS = 1_000_000L
    }
}
