package com.example.jarvisai.data.mapper

import com.example.jarvisai.data.local.database.entity.LocalGgufModelEntity
import com.example.jarvisai.domain.model.GgufModelState
import com.example.jarvisai.domain.model.LocalGgufModel

fun LocalGgufModelEntity.toDomain(isLoaded: Boolean = false): LocalGgufModel {
    val modelState = try {
        GgufModelState.valueOf(state)
    } catch (_: Exception) {
        if (isLoaded) GgufModelState.LOADED else GgufModelState.REGISTERED
    }

    return LocalGgufModel(
        id = id,
        name = name,
        fileName = fileName,
        filePath = filePath,
        sizeBytes = sizeBytes,
        architecture = architecture,
        quantization = quantization,
        contextLength = contextLength,
        state = if (isLoaded) GgufModelState.LOADED else modelState,
        isLoaded = isLoaded || modelState == GgufModelState.LOADED || modelState == GgufModelState.RUNNING,
        isDefault = isDefault,
        lastUsedAt = lastUsedAt,
        sourceUri = sourceUri,
        isCachedFromDrive = isCachedFromDrive
    )
}

fun LocalGgufModel.toEntity(): LocalGgufModelEntity {
    return LocalGgufModelEntity(
        id = id,
        name = name,
        fileName = fileName,
        filePath = filePath,
        sizeBytes = sizeBytes,
        architecture = architecture,
        quantization = quantization,
        contextLength = contextLength,
        state = state.name,
        isDefault = isDefault,
        lastUsedAt = lastUsedAt,
        sourceUri = sourceUri,
        isCachedFromDrive = isCachedFromDrive
    )
}
