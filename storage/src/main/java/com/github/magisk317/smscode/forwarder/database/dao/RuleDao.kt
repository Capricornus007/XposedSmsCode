package com.github.magisk317.smscode.forwarder.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.github.magisk317.smscode.forwarder.entity.Rule

@Dao
interface RuleDao {

    @Insert
    fun insert(rule: Rule)

    @Delete
    fun delete(rule: Rule)

    @Query("DELETE FROM Rule where id=:id")
    fun delete(id: Long)

    @Query("DELETE FROM Rule")
    fun deleteAll()

    @Update
    fun update(rule: Rule)

    @Query("UPDATE Rule SET status=:status WHERE id IN (:ids)")
    fun updateStatusByIds(ids: List<Long>, status: Int)

    @Query("SELECT * FROM Rule where id=:id")
    fun getOne(id: Long): Rule

    @Transaction
    @Query("SELECT * FROM Rule where type=:type and status=:status and (sim_slot='ALL' or sim_slot=:simSlot)")
    fun getRuleList(type: String, status: Int, simSlot: String): List<Rule>

    @Query("SELECT * FROM Rule ORDER BY id DESC")
    fun getAll(): List<Rule>

}