package com.astrbot.android.runtime.plugin

class PluginV2AfterSentView(
    val requestId: String,
    val conversationId: String,
    val sendAttemptId: String,
    val platformAdapterType: String,
    val platformInstanceKey: String,
    val sentAtEpochMs: Long,
    val deliveryStatus: DeliveryStatus,
    receiptIds: List<String> = emptyList(),
    deliveredEntries: List<DeliveredEntry> = emptyList(),
    val usage: PluginLlmUsageSnapshot? = null,
    val deliveredEntryCount: Int = deliveredEntries.size,
) {
    enum class DeliveryStatus(
        val wireValue: String,
    ) {
        SUCCESS("success"),
        FAILED("failed"),
        SKIPPED("skipped");

        companion object {
            fun fromWireValue(value: String): DeliveryStatus? {
                return entries.firstOrNull { it.wireValue == value }
            }
        }
    }

    data class DeliveredEntry(
        val entryId: String,
        val entryType: String,
        val textPreview: String,
        val attachmentCount: Int,
    ) {
        init {
            require(entryId.isNotBlank()) { "entryId must not be blank." }
            require(entryType.isNotBlank()) { "entryType must not be blank." }
            require(attachmentCount >= 0) { "attachmentCount must not be negative." }
        }
    }

    val receiptIds: List<String> = receiptIds.toList()
    val deliveredEntries: List<DeliveredEntry> = deliveredEntries.toList()

    init {
        require(requestId.isNotBlank()) { "requestId must not be blank." }
        require(conversationId.isNotBlank()) { "conversationId must not be blank." }
        require(sendAttemptId.isNotBlank()) { "sendAttemptId must not be blank." }
        require(platformAdapterType.isNotBlank()) { "platformAdapterType must not be blank." }
        require(platformInstanceKey.isNotBlank()) { "platformInstanceKey must not be blank." }
        require(sentAtEpochMs >= 0L) { "sentAtEpochMs must not be negative." }
        require(deliveredEntryCount == this.deliveredEntries.size) {
            "deliveredEntryCount must match deliveredEntries size."
        }
        require(this.receiptIds.none(String::isBlank)) {
            "receiptIds must not contain blank values."
        }
    }
}
