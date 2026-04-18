package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.model.plugin.PluginTriggerSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginRuntimeSchedulerTest {
    @Test
    fun scheduler_blocks_trigger_during_success_cooldown_and_reopens_after_window() {
        val clock = TestClock(now = 10_000L)
        val scheduler = PluginRuntimeScheduler(clock = { clock.now })
        val policy = PluginSchedulePolicy(successCooldownMillis = 500L)

        scheduler.recordDispatched(
            pluginId = "alpha",
            trigger = PluginTriggerSource.OnCommand,
        )
        scheduler.recordSuccess(
            pluginId = "alpha",
            trigger = PluginTriggerSource.OnCommand,
            policy = policy,
        )

        val blocked = scheduler.evaluate(
            pluginId = "alpha",
            trigger = PluginTriggerSource.OnCommand,
            policy = policy,
        )

        assertEquals(false, blocked.allowed)
        assertEquals(PluginDispatchSkipReason.SchedulerCoolingDown, blocked.skipReason)

        clock.advanceBy(501L)

        val reopened = scheduler.evaluate(
            pluginId = "alpha",
            trigger = PluginTriggerSource.OnCommand,
            policy = policy,
        )

        assertTrue(reopened.allowed)
    }

    @Test
    fun scheduler_enters_retry_backoff_then_silence_window_after_failure() {
        val clock = TestClock(now = 20_000L)
        val scheduler = PluginRuntimeScheduler(clock = { clock.now })
        val policy = PluginSchedulePolicy(
            failureRetryBackoffMillis = 300L,
            failureSilenceMillis = 600L,
        )

        scheduler.recordDispatched(
            pluginId = "beta",
            trigger = PluginTriggerSource.BeforeSendMessage,
        )
        scheduler.recordFailure(
            pluginId = "beta",
            trigger = PluginTriggerSource.BeforeSendMessage,
            policy = policy,
        )

        val retryBlocked = scheduler.evaluate(
            pluginId = "beta",
            trigger = PluginTriggerSource.BeforeSendMessage,
            policy = policy,
        )
        assertEquals(false, retryBlocked.allowed)
        assertEquals(PluginDispatchSkipReason.SchedulerRetryBackoff, retryBlocked.skipReason)

        clock.advanceBy(350L)

        val silenced = scheduler.evaluate(
            pluginId = "beta",
            trigger = PluginTriggerSource.BeforeSendMessage,
            policy = policy,
        )
        assertEquals(false, silenced.allowed)
        assertEquals(PluginDispatchSkipReason.SchedulerSilenced, silenced.skipReason)

        clock.advanceBy(300L)

        val reopened = scheduler.evaluate(
            pluginId = "beta",
            trigger = PluginTriggerSource.BeforeSendMessage,
            policy = policy,
        )
        assertTrue(reopened.allowed)
    }
}
