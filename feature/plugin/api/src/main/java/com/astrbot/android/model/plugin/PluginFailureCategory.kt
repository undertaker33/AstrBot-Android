package com.astrbot.android.model.plugin

enum class PluginFailureCategory(
    val wireValue: String,
) {
    Timeout("timeout"),
    PermissionDenied("permission_denied"),
    InvalidPayload("invalid_payload"),
    UnsupportedAction("unsupported_action"),
    RuntimeError("runtime_error"),
    Unknown("unknown");
}
