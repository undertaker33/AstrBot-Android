package com.astrbot.android.model.chat

enum class MessageType(val wireValue: String) {
    FriendMessage("friend"),
    GroupMessage("group"),
    OtherMessage("other");

    companion object {
        fun fromWireValue(value: String): MessageType? = entries.firstOrNull { it.wireValue == value }
    }
}
