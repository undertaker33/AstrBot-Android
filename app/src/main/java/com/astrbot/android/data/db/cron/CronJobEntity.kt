package com.astrbot.android.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for scheduled cron jobs / future tasks.
 *
 * Mirrors AstrBot-master's `CronJob` model, adapted for Android.
 *
 * `jobType`:
 *   - `"active_agent"` — LLM agent wakes up and executes the `note` instruction
 *   - `"basic"` — reserved for simple programmatic triggers (not used by UI yet)
 *
 * `payloadJson` stores extra structured data (session context, sender info, etc.)
 * as a JSON string.  The canonical payload shape for `active_agent`:
 * ```json
 * {
 *   "session": "<unified_msg_origin>",
 *   "sender_id": "<sender_id>",
 *   "note": "<detailed instruction>",
 *   "origin": "tool" | "api" | "ui",
 *   "persona_id": "",
 *   "provider_id": "",
 *   "run_at": "<ISO datetime, only when run_once=true>"
 * }
 * ```
 */
@Entity(tableName = "cron_jobs")
data class CronJobEntity(
    @PrimaryKey val jobId: String,
    val name: String,
    val description: String,
    val jobType: String,
    val cronExpression: String,
    val timezone: String,
    val payloadJson: String,
    val enabled: Boolean,
    val runOnce: Boolean,
    val status: String,
    val lastRunAt: Long,
    val nextRunTime: Long,
    val lastError: String,
    val createdAt: Long,
    val updatedAt: Long,
)
