package com.example.jarvisai.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_models")
data class LocalGgufModelEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val fileName: String,
    val filePath: String,
    val sizeBytes: Long,
    val quantization: String = "Unknown",
    val contextLength: Int = 2048,
    val isDefault: Boolean = false,
    val lastUsedAt: Long = System.currentTimeMillis()
)
