package com.astrbot.android.model.plugin

enum class PluginValidationSeverity {
    INFO,
    WARNING,
    ERROR,
}

data class PluginValidationRule(
    val ruleId: String,
    val title: String,
    val defaultSeverity: PluginValidationSeverity,
)

data class PluginValidationIssue(
    val rule: PluginValidationRule,
    val message: String,
    val severity: PluginValidationSeverity = rule.defaultSeverity,
    val location: String = "",
)

data class PluginValidationReport(
    val pluginId: String = "",
    val pluginVersion: String = "",
    val issues: List<PluginValidationIssue> = emptyList(),
) {
    val errorCount: Int
        get() = issues.count { it.severity == PluginValidationSeverity.ERROR }

    val warningCount: Int
        get() = issues.count { it.severity == PluginValidationSeverity.WARNING }

    val infoCount: Int
        get() = issues.count { it.severity == PluginValidationSeverity.INFO }

    val publishAllowed: Boolean
        get() = errorCount == 0
}
