package com.astrbot.android.feature.qq.runtime

import com.astrbot.android.model.chat.MessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QqReplyPolicyEvaluatorTest {
    @Test
    fun allows_group_reply_when_bot_is_mentioned() {
        val result = QqReplyPolicyEvaluator.evaluate(
            QqReplyPolicyInput(
                messageType = MessageType.GroupMessage,
                text = "hello",
                userId = "10001",
                groupId = "20001",
                isCommand = false,
                mentionsSelf = true,
                mentionsAll = false,
                isSelfMessage = false,
                ignoreSelfMessageEnabled = true,
                ignoreAtAllEventEnabled = true,
                isAdmin = false,
                whitelistEnabled = false,
                whitelistEntries = emptyList(),
                logOnWhitelistMiss = false,
                adminGroupBypassWhitelistEnabled = true,
                adminPrivateBypassWhitelistEnabled = true,
                replyWhenPermissionDenied = false,
                replyOnAtOnlyEnabled = true,
                wakeWords = emptyList(),
                wakeWordsAdminOnlyEnabled = false,
                privateChatRequiresWakeWord = false,
                hasExplicitAtTrigger = true,
            ),
        )

        assertTrue(result.shouldReply)
        assertEquals(QqReplyDecisionReason.AT_MENTION, result.reason)
    }

    @Test
    fun blocks_whitelist_miss_and_requests_info_log() {
        val result = QqReplyPolicyEvaluator.evaluate(
            QqReplyPolicyInput(
                messageType = MessageType.GroupMessage,
                text = "astrbot",
                userId = "10001",
                groupId = "20001",
                isCommand = false,
                mentionsSelf = false,
                mentionsAll = false,
                isSelfMessage = false,
                ignoreSelfMessageEnabled = true,
                ignoreAtAllEventEnabled = true,
                isAdmin = false,
                whitelistEnabled = true,
                whitelistEntries = listOf("99999"),
                logOnWhitelistMiss = true,
                adminGroupBypassWhitelistEnabled = true,
                adminPrivateBypassWhitelistEnabled = true,
                replyWhenPermissionDenied = false,
                replyOnAtOnlyEnabled = false,
                wakeWords = listOf("astrbot"),
                wakeWordsAdminOnlyEnabled = false,
                privateChatRequiresWakeWord = false,
                hasExplicitAtTrigger = false,
            ),
        )

        assertFalse(result.shouldReply)
        assertTrue(result.shouldLogInfo)
        assertEquals(QqReplyDecisionReason.WHITELIST_BLOCKED, result.reason)
    }

    @Test
    fun blocks_private_chat_when_wake_word_is_required_but_missing() {
        val result = QqReplyPolicyEvaluator.evaluate(
            QqReplyPolicyInput(
                messageType = MessageType.FriendMessage,
                text = "hello",
                userId = "10001",
                groupId = null,
                isCommand = false,
                mentionsSelf = false,
                mentionsAll = false,
                isSelfMessage = false,
                ignoreSelfMessageEnabled = true,
                ignoreAtAllEventEnabled = true,
                isAdmin = false,
                whitelistEnabled = false,
                whitelistEntries = emptyList(),
                logOnWhitelistMiss = false,
                adminGroupBypassWhitelistEnabled = true,
                adminPrivateBypassWhitelistEnabled = true,
                replyWhenPermissionDenied = false,
                replyOnAtOnlyEnabled = true,
                wakeWords = listOf("astrbot"),
                wakeWordsAdminOnlyEnabled = false,
                privateChatRequiresWakeWord = true,
                hasExplicitAtTrigger = false,
            ),
        )

        assertFalse(result.shouldReply)
        assertEquals(QqReplyDecisionReason.WAKE_WORD_REQUIRED, result.reason)
    }

    @Test
    fun group_plain_text_is_blocked_when_at_only_is_enabled_and_no_other_trigger_matches() {
        val result = QqReplyPolicyEvaluator.evaluate(
            QqReplyPolicyInput(
                messageType = MessageType.GroupMessage,
                text = "hello everyone",
                userId = "10001",
                groupId = "20001",
                isCommand = false,
                mentionsSelf = false,
                mentionsAll = false,
                isSelfMessage = false,
                ignoreSelfMessageEnabled = true,
                ignoreAtAllEventEnabled = true,
                isAdmin = false,
                whitelistEnabled = false,
                whitelistEntries = emptyList(),
                logOnWhitelistMiss = false,
                adminGroupBypassWhitelistEnabled = true,
                adminPrivateBypassWhitelistEnabled = true,
                replyWhenPermissionDenied = false,
                replyOnAtOnlyEnabled = true,
                wakeWords = listOf("astrbot"),
                wakeWordsAdminOnlyEnabled = false,
                privateChatRequiresWakeWord = false,
                hasExplicitAtTrigger = false,
            ),
        )

        assertFalse(result.shouldReply)
        assertEquals(QqReplyDecisionReason.NO_TRIGGER, result.reason)
    }

    @Test
    fun admin_can_bypass_group_whitelist_but_still_needs_valid_trigger() {
        val result = QqReplyPolicyEvaluator.evaluate(
            QqReplyPolicyInput(
                messageType = MessageType.GroupMessage,
                text = "astrbot help",
                userId = "10001",
                groupId = "20001",
                isCommand = false,
                mentionsSelf = false,
                mentionsAll = false,
                isSelfMessage = false,
                ignoreSelfMessageEnabled = true,
                ignoreAtAllEventEnabled = true,
                isAdmin = true,
                whitelistEnabled = true,
                whitelistEntries = emptyList(),
                logOnWhitelistMiss = true,
                adminGroupBypassWhitelistEnabled = true,
                adminPrivateBypassWhitelistEnabled = true,
                replyWhenPermissionDenied = false,
                replyOnAtOnlyEnabled = true,
                wakeWords = listOf("astrbot"),
                wakeWordsAdminOnlyEnabled = false,
                privateChatRequiresWakeWord = false,
                hasExplicitAtTrigger = false,
            ),
        )

        assertTrue(result.shouldReply)
        assertEquals(QqReplyDecisionReason.WAKE_WORD, result.reason)
    }

    @Test
    fun wake_word_admin_only_does_not_block_commands() {
        val result = QqReplyPolicyEvaluator.evaluate(
            QqReplyPolicyInput(
                messageType = MessageType.GroupMessage,
                text = "/reset",
                userId = "10001",
                groupId = "20001",
                isCommand = true,
                mentionsSelf = false,
                mentionsAll = false,
                isSelfMessage = false,
                ignoreSelfMessageEnabled = true,
                ignoreAtAllEventEnabled = true,
                isAdmin = false,
                whitelistEnabled = false,
                whitelistEntries = emptyList(),
                logOnWhitelistMiss = false,
                adminGroupBypassWhitelistEnabled = true,
                adminPrivateBypassWhitelistEnabled = true,
                replyWhenPermissionDenied = false,
                replyOnAtOnlyEnabled = true,
                wakeWords = listOf("astrbot"),
                wakeWordsAdminOnlyEnabled = true,
                privateChatRequiresWakeWord = false,
                hasExplicitAtTrigger = false,
            ),
        )

        assertTrue(result.shouldReply)
        assertEquals(QqReplyDecisionReason.COMMAND, result.reason)
    }

    @Test
    fun non_admin_private_chat_can_be_whitelist_blocked_without_bypass() {
        val result = QqReplyPolicyEvaluator.evaluate(
            QqReplyPolicyInput(
                messageType = MessageType.FriendMessage,
                text = "astrbot",
                userId = "10001",
                groupId = null,
                isCommand = false,
                mentionsSelf = false,
                mentionsAll = false,
                isSelfMessage = false,
                ignoreSelfMessageEnabled = true,
                ignoreAtAllEventEnabled = true,
                isAdmin = true,
                whitelistEnabled = true,
                whitelistEntries = emptyList(),
                logOnWhitelistMiss = true,
                adminGroupBypassWhitelistEnabled = true,
                adminPrivateBypassWhitelistEnabled = false,
                replyWhenPermissionDenied = true,
                replyOnAtOnlyEnabled = true,
                wakeWords = listOf("astrbot"),
                wakeWordsAdminOnlyEnabled = false,
                privateChatRequiresWakeWord = false,
                hasExplicitAtTrigger = false,
            ),
        )

        assertFalse(result.shouldReply)
        assertEquals(QqReplyDecisionReason.WHITELIST_BLOCKED, result.reason)
        assertEquals("Permission denied by whitelist.", result.permissionDeniedNotice)
    }

    @Test
    fun ignores_at_all_events_when_switch_is_enabled() {
        val result = QqReplyPolicyEvaluator.evaluate(
            QqReplyPolicyInput(
                messageType = MessageType.GroupMessage,
                text = "@all astrbot",
                userId = "10001",
                groupId = "20001",
                isCommand = false,
                mentionsSelf = false,
                mentionsAll = true,
                isSelfMessage = false,
                ignoreSelfMessageEnabled = true,
                ignoreAtAllEventEnabled = true,
                isAdmin = false,
                whitelistEnabled = false,
                whitelistEntries = emptyList(),
                logOnWhitelistMiss = false,
                adminGroupBypassWhitelistEnabled = true,
                adminPrivateBypassWhitelistEnabled = true,
                replyWhenPermissionDenied = false,
                replyOnAtOnlyEnabled = true,
                wakeWords = listOf("astrbot"),
                wakeWordsAdminOnlyEnabled = false,
                privateChatRequiresWakeWord = false,
                hasExplicitAtTrigger = true,
            ),
        )

        assertFalse(result.shouldReply)
        assertEquals(QqReplyDecisionReason.AT_ALL_IGNORED, result.reason)
    }

    @Test
    fun at_all_events_can_trigger_reply_when_ignore_switch_is_disabled() {
        val result = QqReplyPolicyEvaluator.evaluate(
            QqReplyPolicyInput(
                messageType = MessageType.GroupMessage,
                text = "@all hello",
                userId = "10001",
                groupId = "20001",
                isCommand = false,
                mentionsSelf = false,
                mentionsAll = true,
                isSelfMessage = false,
                ignoreSelfMessageEnabled = true,
                ignoreAtAllEventEnabled = false,
                isAdmin = false,
                whitelistEnabled = false,
                whitelistEntries = emptyList(),
                logOnWhitelistMiss = false,
                adminGroupBypassWhitelistEnabled = true,
                adminPrivateBypassWhitelistEnabled = true,
                replyWhenPermissionDenied = false,
                replyOnAtOnlyEnabled = true,
                wakeWords = emptyList(),
                wakeWordsAdminOnlyEnabled = false,
                privateChatRequiresWakeWord = false,
                hasExplicitAtTrigger = true,
            ),
        )

        assertTrue(result.shouldReply)
        assertEquals(QqReplyDecisionReason.AT_MENTION, result.reason)
    }

    @Test
    fun bare_bot_mention_replies_only_when_at_only_switch_is_enabled() {
        val enabledResult = QqReplyPolicyEvaluator.evaluate(
            QqReplyPolicyInput(
                messageType = MessageType.GroupMessage,
                text = "",
                userId = "10001",
                groupId = "20001",
                isCommand = false,
                mentionsSelf = true,
                mentionsAll = false,
                isSelfMessage = false,
                ignoreSelfMessageEnabled = true,
                ignoreAtAllEventEnabled = true,
                isAdmin = false,
                whitelistEnabled = false,
                whitelistEntries = emptyList(),
                logOnWhitelistMiss = false,
                adminGroupBypassWhitelistEnabled = true,
                adminPrivateBypassWhitelistEnabled = true,
                replyWhenPermissionDenied = false,
                replyOnAtOnlyEnabled = true,
                wakeWords = emptyList(),
                wakeWordsAdminOnlyEnabled = false,
                privateChatRequiresWakeWord = false,
                hasExplicitAtTrigger = true,
            ),
        )

        val disabledResult = QqReplyPolicyEvaluator.evaluate(
            QqReplyPolicyInput(
                messageType = MessageType.GroupMessage,
                text = "",
                userId = "10001",
                groupId = "20001",
                isCommand = false,
                mentionsSelf = true,
                mentionsAll = false,
                isSelfMessage = false,
                ignoreSelfMessageEnabled = true,
                ignoreAtAllEventEnabled = true,
                isAdmin = false,
                whitelistEnabled = false,
                whitelistEntries = emptyList(),
                logOnWhitelistMiss = false,
                adminGroupBypassWhitelistEnabled = true,
                adminPrivateBypassWhitelistEnabled = true,
                replyWhenPermissionDenied = false,
                replyOnAtOnlyEnabled = false,
                wakeWords = emptyList(),
                wakeWordsAdminOnlyEnabled = false,
                privateChatRequiresWakeWord = false,
                hasExplicitAtTrigger = true,
            ),
        )

        assertTrue(enabledResult.shouldReply)
        assertEquals(QqReplyDecisionReason.AT_MENTION, enabledResult.reason)
        assertFalse(disabledResult.shouldReply)
        assertEquals(QqReplyDecisionReason.NO_TRIGGER, disabledResult.reason)
    }
}
