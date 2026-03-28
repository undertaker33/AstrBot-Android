package com.astrbot.android.ui.screen

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.astrbot.android.R
import com.astrbot.android.ui.monochromeOutlinedTextFieldColors

@Composable
internal fun AdminSettingsSection(
    adminUids: List<String>,
    onAdminUidsChange: (List<String>) -> Unit,
) {
        ConfigSectionCard(
        title = stringResource(R.string.config_section_admin),
        subtitle = stringResource(R.string.config_section_admin_desc),
    ) {
        ConfigFieldGroup {
            StringListManagerField(
                title = stringResource(R.string.config_admin_uids_title),
                values = adminUids,
                itemLabel = stringResource(R.string.config_admin_uid_item_label),
                onValuesChange = onAdminUidsChange,
                helperText = stringResource(R.string.config_admin_uids_desc),
            )
        }
    }
}

@Composable
internal fun SessionSettingsSection(
    sessionIsolationEnabled: Boolean,
    onSessionIsolationEnabledChange: (Boolean) -> Unit,
) {
    ConfigSectionCard(
        title = stringResource(R.string.config_section_session),
        subtitle = stringResource(R.string.config_section_session_desc),
    ) {
        ConfigFieldGroup {
            ConfigToggleField(
                title = stringResource(R.string.config_session_isolation_title),
                subtitle = stringResource(R.string.config_session_isolation_desc),
                checked = sessionIsolationEnabled,
                onCheckedChange = onSessionIsolationEnabledChange,
            )
        }
    }
}

@Composable
internal fun WakeSettingsSection(
    wakeWords: List<String>,
    onWakeWordsChange: (List<String>) -> Unit,
    wakeWordsAdminOnlyEnabled: Boolean,
    onWakeWordsAdminOnlyEnabledChange: (Boolean) -> Unit,
    privateChatRequiresWakeWord: Boolean,
    onPrivateChatRequiresWakeWordChange: (Boolean) -> Unit,
) {
    ConfigSectionCard(
        title = stringResource(R.string.config_section_wake),
        subtitle = stringResource(R.string.config_section_wake_desc),
    ) {
        ConfigFieldGroup {
            StringListManagerField(
                title = stringResource(R.string.config_wake_words_title),
                values = wakeWords,
                itemLabel = stringResource(R.string.config_wake_word_item_label),
                onValuesChange = onWakeWordsChange,
                helperText = stringResource(R.string.config_wake_words_desc),
            )
            ConfigToggleField(
                title = stringResource(R.string.config_wake_words_admin_only_title),
                subtitle = stringResource(R.string.config_wake_words_admin_only_desc),
                checked = wakeWordsAdminOnlyEnabled,
                onCheckedChange = onWakeWordsAdminOnlyEnabledChange,
            )
            ConfigToggleField(
                title = stringResource(R.string.config_private_chat_requires_wake_word_title),
                subtitle = stringResource(R.string.config_private_chat_requires_wake_word_desc),
                checked = privateChatRequiresWakeWord,
                onCheckedChange = onPrivateChatRequiresWakeWordChange,
            )
        }
    }
}

@Composable
internal fun ReplySettingsSection(
    replyTextPrefix: String,
    onReplyTextPrefixChange: (String) -> Unit,
    quoteSenderMessageEnabled: Boolean,
    onQuoteSenderMessageEnabledChange: (Boolean) -> Unit,
    mentionSenderEnabled: Boolean,
    onMentionSenderEnabledChange: (Boolean) -> Unit,
    replyOnAtOnlyEnabled: Boolean,
    onReplyOnAtOnlyEnabledChange: (Boolean) -> Unit,
) {
    ConfigSectionCard(
        title = stringResource(R.string.config_section_reply),
        subtitle = stringResource(R.string.config_section_reply_desc),
    ) {
        ConfigFieldGroup {
            OutlinedTextField(
                value = replyTextPrefix,
                onValueChange = onReplyTextPrefixChange,
                label = { Text(stringResource(R.string.config_reply_prefix_title)) },
                modifier = Modifier,
                colors = monochromeOutlinedTextFieldColors(),
            )
            ConfigToggleField(
                title = stringResource(R.string.config_quote_sender_message_title),
                subtitle = stringResource(R.string.config_quote_sender_message_desc),
                checked = quoteSenderMessageEnabled,
                onCheckedChange = onQuoteSenderMessageEnabledChange,
            )
            ConfigToggleField(
                title = stringResource(R.string.config_mention_sender_title),
                subtitle = stringResource(R.string.config_mention_sender_desc),
                checked = mentionSenderEnabled,
                onCheckedChange = onMentionSenderEnabledChange,
            )
            ConfigToggleField(
                title = stringResource(R.string.config_reply_on_at_only_title),
                subtitle = stringResource(R.string.config_reply_on_at_only_desc),
                checked = replyOnAtOnlyEnabled,
                onCheckedChange = onReplyOnAtOnlyEnabledChange,
            )
        }
    }
}

@Composable
internal fun WhitelistSettingsSection(
    whitelistEnabled: Boolean,
    onWhitelistEnabledChange: (Boolean) -> Unit,
    whitelistEntries: List<String>,
    onWhitelistEntriesChange: (List<String>) -> Unit,
    logOnWhitelistMiss: Boolean,
    onLogOnWhitelistMissChange: (Boolean) -> Unit,
    adminGroupBypassWhitelistEnabled: Boolean,
    onAdminGroupBypassWhitelistEnabledChange: (Boolean) -> Unit,
    adminPrivateBypassWhitelistEnabled: Boolean,
    onAdminPrivateBypassWhitelistEnabledChange: (Boolean) -> Unit,
) {
    ConfigSectionCard(
        title = stringResource(R.string.config_section_whitelist),
        subtitle = stringResource(R.string.config_section_whitelist_desc),
    ) {
        ConfigFieldGroup {
            ConfigToggleField(
                title = stringResource(R.string.config_whitelist_enabled_title),
                subtitle = stringResource(R.string.config_whitelist_enabled_desc),
                checked = whitelistEnabled,
                onCheckedChange = onWhitelistEnabledChange,
            )
            StringListManagerField(
                title = stringResource(R.string.config_whitelist_entries_title),
                values = whitelistEntries,
                itemLabel = stringResource(R.string.config_whitelist_entry_item_label),
                onValuesChange = onWhitelistEntriesChange,
                helperText = stringResource(R.string.config_whitelist_entries_desc),
            )
            ConfigToggleField(
                title = stringResource(R.string.config_log_on_whitelist_miss_title),
                subtitle = stringResource(R.string.config_log_on_whitelist_miss_desc),
                checked = logOnWhitelistMiss,
                onCheckedChange = onLogOnWhitelistMissChange,
            )
            ConfigToggleField(
                title = stringResource(R.string.config_admin_group_bypass_whitelist_title),
                subtitle = stringResource(R.string.config_admin_group_bypass_whitelist_desc),
                checked = adminGroupBypassWhitelistEnabled,
                onCheckedChange = onAdminGroupBypassWhitelistEnabledChange,
            )
            ConfigToggleField(
                title = stringResource(R.string.config_admin_private_bypass_whitelist_title),
                subtitle = stringResource(R.string.config_admin_private_bypass_whitelist_desc),
                checked = adminPrivateBypassWhitelistEnabled,
                onCheckedChange = onAdminPrivateBypassWhitelistEnabledChange,
            )
        }
    }
}

@Composable
internal fun IgnorePermissionSettingsSection(
    ignoreSelfMessageEnabled: Boolean,
    onIgnoreSelfMessageEnabledChange: (Boolean) -> Unit,
    ignoreAtAllEventEnabled: Boolean,
    onIgnoreAtAllEventEnabledChange: (Boolean) -> Unit,
    replyWhenPermissionDenied: Boolean,
    onReplyWhenPermissionDeniedChange: (Boolean) -> Unit,
) {
    ConfigSectionCard(
        title = stringResource(R.string.config_section_ignore_permission),
        subtitle = stringResource(R.string.config_section_ignore_permission_desc),
    ) {
        ConfigFieldGroup {
            ConfigToggleField(
                title = stringResource(R.string.config_ignore_self_message_title),
                subtitle = stringResource(R.string.config_ignore_self_message_desc),
                checked = ignoreSelfMessageEnabled,
                onCheckedChange = onIgnoreSelfMessageEnabledChange,
            )
            ConfigToggleField(
                title = stringResource(R.string.config_ignore_at_all_event_title),
                subtitle = stringResource(R.string.config_ignore_at_all_event_desc),
                checked = ignoreAtAllEventEnabled,
                onCheckedChange = onIgnoreAtAllEventEnabledChange,
            )
            ConfigToggleField(
                title = stringResource(R.string.config_reply_when_permission_denied_title),
                subtitle = stringResource(R.string.config_reply_when_permission_denied_desc),
                checked = replyWhenPermissionDenied,
                onCheckedChange = onReplyWhenPermissionDeniedChange,
            )
        }
    }
}

@Composable
internal fun RateLimitSettingsSection(
    rateLimitWindowSeconds: String,
    onRateLimitWindowSecondsChange: (String) -> Unit,
    rateLimitMaxCount: String,
    onRateLimitMaxCountChange: (String) -> Unit,
    rateLimitStrategy: String,
    onRateLimitStrategyChange: (String) -> Unit,
) {
    ConfigSectionCard(
        title = stringResource(R.string.config_section_rate_limit),
        subtitle = stringResource(R.string.config_section_rate_limit_desc),
    ) {
        ConfigFieldGroup {
            OutlinedTextField(
                value = rateLimitWindowSeconds,
                onValueChange = { onRateLimitWindowSecondsChange(it.filter(Char::isDigit).take(6)) },
                label = { Text(stringResource(R.string.config_rate_limit_window_seconds_title)) },
                modifier = Modifier,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = monochromeOutlinedTextFieldColors(),
            )
            OutlinedTextField(
                value = rateLimitMaxCount,
                onValueChange = { onRateLimitMaxCountChange(it.filter(Char::isDigit).take(6)) },
                label = { Text(stringResource(R.string.config_rate_limit_max_count_title)) },
                modifier = Modifier,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = monochromeOutlinedTextFieldColors(),
            )
            SelectionField(
                title = stringResource(R.string.config_rate_limit_strategy_title),
                options = listOf(
                    "drop" to stringResource(R.string.config_rate_limit_strategy_drop),
                    "stash" to stringResource(R.string.config_rate_limit_strategy_stash),
                ),
                selectedId = rateLimitStrategy,
                onSelect = onRateLimitStrategyChange,
            )
        }
    }
}

@Composable
internal fun KeywordSettingsSection(
    keywordDetectionEnabled: Boolean,
    onKeywordDetectionEnabledChange: (Boolean) -> Unit,
    keywordPatterns: List<String>,
    onKeywordPatternsChange: (List<String>) -> Unit,
) {
    ConfigSectionCard(
        title = stringResource(R.string.config_section_keyword),
        subtitle = stringResource(R.string.config_section_keyword_desc),
    ) {
        ConfigFieldGroup {
            ConfigToggleField(
                title = stringResource(R.string.config_keyword_detection_enabled_title),
                subtitle = stringResource(R.string.config_keyword_detection_enabled_desc),
                checked = keywordDetectionEnabled,
                onCheckedChange = onKeywordDetectionEnabledChange,
            )
            StringListManagerField(
                title = stringResource(R.string.config_keyword_patterns_title),
                values = keywordPatterns,
                itemLabel = stringResource(R.string.config_keyword_pattern_item_label),
                onValuesChange = onKeywordPatternsChange,
                helperText = stringResource(R.string.config_keyword_patterns_desc),
            )
        }
    }
}
