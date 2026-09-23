package com.example.jarvisai.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.jarvisai.data.local.database.entity.LocalGgufModelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {

    @Query("SELECT * FROM local_models ORDER BY lastUsedAt DESC")
    fun getAllModels(): Flow<List<LocalGgufModelEntity>>

    @Query("SELECT * FROM local_models WHERE id = :id LIMIT 1")
    fun getModelById(id: String): Flow<LocalGgufModelEntity?>

    @Query("SELECT * FROM local_models WHERE id = :id LIMIT 1")
    suspend fun getModelByIdOnce(id: String): LocalGgufModelEntity?

    @Query("SELECT * FROM local_models WHERE isDefault = 1 LIMIT 1")
    fun getDefaultModel(): Flow<LocalGgufModelEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: LocalGgufModelEntity)

    @Query("UPDATE local_models SET isDefault = 0")
    suspend fun clearDefaultFlag()

    @Query("UPDATE local_models SET isDefault = 1, lastUsedAt = :timestamp WHERE id = :id")
    suspend fun markAsDefault(id: String, timestamp: Long = System.currentTimeMillis())

    @Transaction
    suspend fun setDefaultModel(id: String) {
        clearDefaultFlag()
        markAsDefault(id)
    }

    @Query("UPDATE local_models SET lastUsedAt = :timestamp WHERE id = :id")
    suspend fun updateLastUsed(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE local_models SET state = :state WHERE id = :id")
    suspend fun updateModelState(id: String, state: String)

    @Query("DELETE FROM local_models WHERE id = :id")
    suspend fun deleteModelById(id: String)
}
