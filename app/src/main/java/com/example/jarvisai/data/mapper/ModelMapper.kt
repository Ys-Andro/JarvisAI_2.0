package com.example.jarvisai.data.mapper

import com.example.jarvisai.data.local.database.entity.LocalGgufModelEntity
import com.example.jarvisai.domain.model.LocalGgufModel

fun LocalGgufModelEntity.toDomain(isLoaded: Boolean = false): LocalGgufModel {
    return LocalGgufModel(
        id = id,
        name = name,
        fileName = fileName,
        filePath = filePath,
        sizeBytes = sizeBytes,
        quantization = quantization,
        contextLength = contextLength,
        isLoaded = isLoaded,
        isDefault = isDefault,
        lastUsedAt = lastUsedAt
    )
}

fun LocalGgufModel.toEntity(): LocalGgufModelEntity {
    return LocalGgufModelEntity(
        id = id,
        name = name,
        fileName = fileName,
        filePath = filePath,
        sizeBytes = sizeBytes,
        quantization = quantization,
        contextLength = contextLength,
        isDefault = isDefault,
        lastUsedAt = lastUsedAt
    )
}
