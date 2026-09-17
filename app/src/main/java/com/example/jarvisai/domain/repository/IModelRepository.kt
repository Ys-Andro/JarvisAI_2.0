package com.example.jarvisai.domain.repository

import com.example.jarvisai.domain.model.LocalGgufModel
import kotlinx.coroutines.flow.Flow

interface IModelRepository {
    fun getAllModels(): Flow<List<LocalGgufModel>>
    fun getModelById(modelId: String): Flow<LocalGgufModel?>
    suspend fun registerImportedModel(
        name: String,
        fileName: String,
        filePath: String,
        sizeBytes: Long,
        quantization: String
    ): LocalGgufModel
    suspend fun deleteModel(modelId: String)
    suspend fun setDefaultModel(modelId: String)
    fun getDefaultModel(): Flow<LocalGgufModel?>
}
