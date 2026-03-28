package com.astrbot.android.runtime.qq

data class QqReplyPolicyInput(
    val messageType: String,
    val text: String,
    val userId: String,
    val groupId: String?,
    val isCommand: Boolean,
    val mentionsSelf: Boolean,
    val mentionsAll: Boolean,
    val isSelfMessage: Boolean,
    val ignoreSelfMessageEnabled: Boolean,
    val ignoreAtAllEventEnabled: Boolean,
    val isAdmin: Boolean,
    val whitelistEnabled: Boolean,
    val whitelistEntries: List<String>,
    val logOnWhitelistMiss: Boolean,
    val adminGroupBypassWhitelistEnabled: Boolean,
    val adminPrivateBypassWhitelistEnabled: Boolean,
    val replyWhenPermissionDenied: Boolean,
    val replyOnAtOnlyEnabled: Boolean,
    val wakeWords: List<String>,
    val wakeWordsAdminOnlyEnabled: Boolean,
    val privateChatRequiresWakeWord: Boolean,
    val hasExplicitAtTrigger: Boolean,
)

data class QqReplyPolicyResult(
    val shouldReply: Boolean,
    val reason: QqReplyDecisionReason,
    val shouldLogInfo: Boolean = false,
    val permissionDeniedNotice: String? = null,
)

enum class QqReplyDecisionReason {
    COMMAND,
    AT_MENTION,
    WAKE_WORD,
    PRIVATE_DEFAULT,
    SELF_MESSAGE_IGNORED,
    AT_ALL_IGNORED,
    WHITELIST_BLOCKED,
    WAKE_WORD_REQUIRED,
    NO_TRIGGER,
}

object QqReplyPolicyEvaluator {
    fun evaluate(input: QqReplyPolicyInput): QqReplyPolicyResult {
        if (input.ignoreSelfMessageEnabled && input.isSelfMessage) {
            return QqReplyPolicyResult(
                shouldReply = false,
                reason = QqReplyDecisionReason.SELF_MESSAGE_IGNORED,
            )
        }

        if (input.ignoreAtAllEventEnabled && input.mentionsAll) {
            return QqReplyPolicyResult(
                shouldReply = false,
                reason = QqReplyDecisionReason.AT_ALL_IGNORED,
            )
        }

        if (input.whitelistEnabled && !isWhitelistBypassed(input)) {
            val allowed = QqWhitelistMatcher.isAllowed(
                entries = input.whitelistEntries,
                userId = input.userId,
                groupId = input.groupId,
            )
            if (!allowed) {
                return QqReplyPolicyResult(
                    shouldReply = false,
                    reason = QqReplyDecisionReason.WHITELIST_BLOCKED,
                    shouldLogInfo = input.logOnWhitelistMiss,
                    permissionDeniedNotice = input.replyWhenPermissionDenied
                        .takeIf { it }
                        ?.let { "Permission denied by whitelist." },
                )
            }
        }

        if (input.isCommand) {
            return QqReplyPolicyResult(
                shouldReply = true,
                reason = QqReplyDecisionReason.COMMAND,
            )
        }

        val wakeMatched = QqWakeWordPolicy.matches(
            text = input.text,
            wakeWords = input.wakeWords,
            adminOnlyEnabled = input.wakeWordsAdminOnlyEnabled,
            isAdmin = input.isAdmin,
        )
        val hasMentionContentTrigger = input.mentionsSelf && input.text.isNotBlank()
        val hasBareMentionTrigger = input.replyOnAtOnlyEnabled &&
            input.hasExplicitAtTrigger &&
            input.mentionsSelf
        val hasAtAllTrigger = input.hasExplicitAtTrigger && input.mentionsAll

        return when (input.messageType) {
            "private" -> {
                if (input.privateChatRequiresWakeWord && !wakeMatched) {
                    QqReplyPolicyResult(
                        shouldReply = false,
                        reason = QqReplyDecisionReason.WAKE_WORD_REQUIRED,
                        permissionDeniedNotice = input.replyWhenPermissionDenied
                            .takeIf { it }
                            ?.let { "Wake word required." },
                    )
                } else if (wakeMatched) {
                    QqReplyPolicyResult(
                        shouldReply = true,
                        reason = QqReplyDecisionReason.WAKE_WORD,
                    )
                } else {
                    QqReplyPolicyResult(
                        shouldReply = true,
                        reason = QqReplyDecisionReason.PRIVATE_DEFAULT,
                    )
                }
            }

            "group" -> when {
                hasMentionContentTrigger || hasBareMentionTrigger || hasAtAllTrigger -> QqReplyPolicyResult(
                    shouldReply = true,
                    reason = QqReplyDecisionReason.AT_MENTION,
                )

                wakeMatched -> QqReplyPolicyResult(
                    shouldReply = true,
                    reason = QqReplyDecisionReason.WAKE_WORD,
                )

                else -> QqReplyPolicyResult(
                    shouldReply = false,
                    reason = QqReplyDecisionReason.NO_TRIGGER,
                )
            }

            else -> QqReplyPolicyResult(
                shouldReply = false,
                reason = QqReplyDecisionReason.NO_TRIGGER,
            )
        }
    }

    private fun isWhitelistBypassed(input: QqReplyPolicyInput): Boolean {
        if (!input.isAdmin) return false
        return when (input.messageType) {
            "group" -> input.adminGroupBypassWhitelistEnabled
            "private" -> input.adminPrivateBypassWhitelistEnabled
            else -> false
        }
    }
}
