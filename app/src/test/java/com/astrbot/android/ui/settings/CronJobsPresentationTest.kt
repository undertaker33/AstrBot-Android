package com.astrbot.android.ui.settings

import com.astrbot.android.model.CronJob
import com.astrbot.android.model.CronJobExecutionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CronJobsPresentationTest {

    @Test
    fun `cron jobs page shows five items per page`() {
        val presentation = buildCronJobsPresentation(
            jobs = sampleJobs(6),
            requestedPage = 1,
        )

        assertEquals(1, presentation.currentPage)
        assertEquals(2, presentation.totalPages)
        assertEquals(listOf("job-1", "job-2", "job-3", "job-4", "job-5"), presentation.visibleJobs.map { it.jobId })
        assertFalse(presentation.canGoPrevious)
        assertTrue(presentation.canGoNext)
    }

    @Test
    fun `cron jobs page exposes session and execution metadata`() {
        val presentation = buildCronJobsPresentation(
            jobs = sampleJobs(1),
            requestedPage = 1,
        )

        val item = presentation.visibleJobs.single()

        assertEquals("conversation-1", item.conversationId)
        assertEquals(1_735_000_001_000L, item.nextRunTime)
        assertEquals(1_734_000_001_000L, item.lastRunAt)
        assertEquals("Task 1 description", item.description)
        assertEquals("0 9 * * *", item.cronExpression)
    }

    @Test
    fun `cron job run presentation prefers delivery summary then error detail`() {
        val presentations = buildCronJobRunPresentations(
            listOf(
                CronJobExecutionRecord(
                    executionId = "run-1",
                    jobId = "job-1",
                    status = "succeeded",
                    startedAt = 10L,
                    completedAt = 20L,
                    attempt = 1,
                    trigger = "scheduled",
                    deliverySummary = "Delivered to app chat",
                    errorMessage = "ignored",
                ),
                CronJobExecutionRecord(
                    executionId = "run-2",
                    jobId = "job-1",
                    status = "failed",
                    attempt = 2,
                    errorCode = "delivery_failed",
                    errorMessage = "Conversation missing",
                ),
            ),
        )

        assertEquals("run-1", presentations[0].executionId)
        assertEquals("Delivered to app chat", presentations[0].summary)
        assertEquals("Conversation missing", presentations[1].summary)
        assertEquals(2, presentations[1].attempt)
    }

    @Test
    fun `cron jobs page coerces requested page into available range`() {
        val presentation = buildCronJobsPresentation(
            jobs = sampleJobs(6),
            requestedPage = 99,
        )

        assertEquals(2, presentation.currentPage)
        assertEquals(listOf("job-6"), presentation.visibleJobs.map { it.jobId })
        assertTrue(presentation.canGoPrevious)
        assertFalse(presentation.canGoNext)
    }

    private fun sampleJobs(count: Int): List<CronJob> {
        return (1..count).map { index ->
            CronJob(
                jobId = "job-$index",
                name = "Task $index",
                description = "Task $index description",
                cronExpression = "0 9 * * *",
                conversationId = "conversation-$index",
                nextRunTime = 1_735_000_000_000L + index * 1_000L,
                lastRunAt = 1_734_000_000_000L + index * 1_000L,
                status = "scheduled",
                enabled = index % 2 == 1,
                createdAt = 1_733_000_000_000L + index * 1_000L,
            )
        }
    }
}
