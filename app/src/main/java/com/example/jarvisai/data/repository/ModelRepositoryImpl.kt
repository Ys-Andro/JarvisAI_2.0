package com.example.jarvisai.data.repository

import com.example.jarvisai.data.local.database.dao.ModelDao
import com.example.jarvisai.data.mapper.toDomain
import com.example.jarvisai.data.mapper.toEntity
import com.example.jarvisai.domain.model.LocalGgufModel
import com.example.jarvisai.domain.repository.IModelRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class ModelRepositoryImpl(
    private val modelDao: ModelDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : IModelRepository {

    override fun getAllModels(): Flow<List<LocalGgufModel>> {
        return modelDao.getAllModels()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(dispatcher)
    }

    override fun getModelById(modelId: String): Flow<LocalGgufModel?> {
        return modelDao.getModelById(modelId)
            .map { it?.toDomain() }
            .flowOn(dispatcher)
    }

    override suspend fun registerImportedModel(
        name: String,
        fileName: String,
        filePath: String,
        sizeBytes: Long,
        quantization: String
    ): LocalGgufModel = withContext(dispatcher) {
        val model = LocalGgufModel(
            id = UUID.randomUUID().toString(),
            name = name,
            fileName = fileName,
            filePath = filePath,
            sizeBytes = sizeBytes,
            quantization = quantization,
            contextLength = 2048,
            isLoaded = false,
            isDefault = false,
            lastUsedAt = System.currentTimeMillis()
        )
        modelDao.insertModel(model.toEntity())
        model
    }

    override suspend fun deleteModel(modelId: String) = withContext(dispatcher) {
        modelDao.deleteModelById(modelId)
    }

    override suspend fun setDefaultModel(modelId: String) = withContext(dispatcher) {
        modelDao.setDefaultModel(modelId)
    }

    override fun getDefaultModel(): Flow<LocalGgufModel?> {
        return modelDao.getDefaultModel()
            .map { it?.toDomain() }
            .flowOn(dispatcher)
    }
}
