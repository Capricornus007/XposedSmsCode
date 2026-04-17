package com.github.magisk317.smscode.forwarder.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.github.magisk317.smscode.forwarder.entity.Sender
import kotlinx.coroutines.flow.Flow

@Dao
interface SenderDao {

    @Insert
    suspend fun insert(sender: Sender): Long

    @Delete
    suspend fun delete(sender: Sender)

    @Query("DELETE FROM Sender where id=:id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM Sender")
    suspend fun deleteAll()

    @Update
    suspend fun update(sender: Sender)

    @Query("UPDATE Sender SET status=:status WHERE id IN (:ids)")
    suspend fun updateStatusByIds(ids: List<Long>, status: Int)

    @Query("SELECT * FROM Sender where id=:id")
    suspend fun getOne(id: Long): Sender?

    @Query("SELECT * FROM Sender WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<Sender>

    @Query("SELECT * FROM Sender ORDER BY id DESC")
    suspend fun getAll(): List<Sender>

    @Query("SELECT * FROM Sender ORDER BY id DESC")
    fun getAllFlow(): Flow<List<Sender>>
}
