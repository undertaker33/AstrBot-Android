package com.elymbot.android.app.integration.geofence

import com.elymbot.android.feature.cron.runtime.ScheduledMessageDeliveryPort
import com.elymbot.android.feature.cron.runtime.ScheduledMessageDeliveryRequest
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceMessageAttachment
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceMessageDeliveryPort
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceMessageDeliveryRequest
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceMessageDeliveryResult
import com.elymbot.android.model.chat.ConversationAttachment
import javax.inject.Inject

class GeofenceMessageDeliveryPortAdapter @Inject constructor(
    private val scheduledMessageDeliveryPort: ScheduledMessageDeliveryPort,
) : GeofenceMessageDeliveryPort {
    override suspend fun deliver(request: GeofenceMessageDeliveryRequest): GeofenceMessageDeliveryResult {
        val result = scheduledMessageDeliveryPort.deliver(
            ScheduledMessageDeliveryRequest(
                platform = request.platform,
                conversationId = request.conversationId,
                text = request.text,
                attachments = request.attachments.map { it.toConversationAttachment() },
                botId = request.botId,
            ),
        )
        return GeofenceMessageDeliveryResult(
            success = result.success,
            deliveredMessageCount = result.deliveredMessageCount,
            receiptIds = result.receiptIds,
            errorCode = result.errorCode,
            errorSummary = result.errorSummary,
        )
    }

    private fun GeofenceMessageAttachment.toConversationAttachment(): ConversationAttachment =
        ConversationAttachment(
            id = id,
            type = type,
            mimeType = mimeType,
            fileName = fileName,
            base64Data = base64Data,
            remoteUrl = remoteUrl,
        )
}
