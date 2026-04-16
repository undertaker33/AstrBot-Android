package com.astrbot.android.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "config_mcp_servers",
    primaryKeys = ["configId", "serverId"],
    foreignKeys = [
        ForeignKey(
            entity = ConfigProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["configId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["configId", "sortIndex"])],
)
data class ConfigMcpServerEntity(
    val configId: String,
    val serverId: String,
    val name: String,
    val url: String,
    val transport: String,
    val command: String,
    val argsJson: String,
    val headersJson: String,
    val timeoutSeconds: Int,
    val active: Boolean,
    val sortIndex: Int,
)
