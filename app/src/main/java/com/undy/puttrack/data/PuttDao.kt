package com.undy.puttrack.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PuttDao {
    @Insert
    suspend fun insert(putt: Putt)

    @Query("SELECT * FROM putts ORDER BY timestampMillis ASC")
    fun observeAll(): Flow<List<Putt>>

    @Query("DELETE FROM putts")
    suspend fun deleteAll()

    @Query("DELETE FROM putts WHERE id = (SELECT id FROM putts ORDER BY timestampMillis DESC, id DESC LIMIT 1)")
    suspend fun deleteLast()
}
