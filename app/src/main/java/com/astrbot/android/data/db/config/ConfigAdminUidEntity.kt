package com.astrbot.android.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "config_admin_uids",
    primaryKeys = ["configId", "uid"],
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
data class ConfigAdminUidEntity(
    val configId: String,
    val uid: String,
    val sortIndex: Int,
)
