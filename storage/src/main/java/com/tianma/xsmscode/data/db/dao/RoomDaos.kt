package com.tianma.xsmscode.data.db.dao

import androidx.room.*
import com.tianma.xsmscode.data.db.entity.AppInfo
import com.tianma.xsmscode.data.db.entity.NotifyRouteRule
import com.tianma.xsmscode.data.db.entity.SmsCodeRule
import com.tianma.xsmscode.data.db.entity.SmsMsg
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsCodeRuleDao {
    @Query("SELECT * FROM sms_code_rule")
    fun getAll(): List<SmsCodeRule>

    @Query("SELECT * FROM sms_code_rule WHERE id = :id")
    fun getById(id: Long): SmsCodeRule?

    @Query("SELECT * FROM sms_code_rule")
    fun getAllFlow(): Flow<List<SmsCodeRule>>

    @Query(
        "SELECT * FROM sms_code_rule WHERE company = :company AND code_keyword = :codeKeyword AND code_regex = :codeRegex",
    )
    fun queryRules(company: String?, codeKeyword: String, codeRegex: String): List<SmsCodeRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(rule: SmsCodeRule): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(rules: List<SmsCodeRule>)

    @Update
    fun update(rule: SmsCodeRule)

    @Delete
    fun delete(rule: SmsCodeRule)

    @Delete
    fun deleteAll(rules: List<SmsCodeRule>)

    @Query("DELETE FROM sms_code_rule")
    fun clearAll()

    @Query("SELECT count(*) FROM sms_code_rule")
    fun count(): Long
}

@Dao
interface SmsMsgDao {
    @Query("SELECT * FROM sms_msg ORDER BY date DESC")
    fun getAll(): List<SmsMsg>

    @Query("SELECT * FROM sms_msg WHERE id = :id LIMIT 1")
    fun getById(id: Long): SmsMsg?

    @Query("SELECT * FROM sms_msg WHERE sender IS :sender AND body IS :body AND date = :date AND msg_type = :msgType LIMIT 1")
    fun getByFingerprint(sender: String?, body: String?, date: Long, msgType: Int): SmsMsg?

    @Query("SELECT * FROM sms_msg ORDER BY date DESC")
    fun getAllFlow(): Flow<List<SmsMsg>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(msg: SmsMsg): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(msgs: List<SmsMsg>)

    @Update
    fun update(msg: SmsMsg)

    @Query("DELETE FROM sms_msg")
    fun clearAll()

    @Query("SELECT count(*) FROM sms_msg")
    fun count(): Long

    @Query("SELECT count(*) FROM sms_msg")
    fun countFlow(): Flow<Long>

    @Delete
    fun delete(msg: SmsMsg)

    @Delete
    fun deleteInTx(msgs: List<SmsMsg>)
}

@Dao
interface AppInfoDao {
    @Query("SELECT * FROM app_info")
    fun getAll(): List<AppInfo>

    @Query("SELECT * FROM app_info")
    fun getAllFlow(): Flow<List<AppInfo>>

    @Query("SELECT * FROM app_info WHERE blocked = 1")
    fun getBlockedApps(): List<AppInfo>

    @Query("SELECT * FROM app_info WHERE forwarding = 1")
    fun getForwardingApps(): List<AppInfo>

    @Query("SELECT * FROM app_info WHERE package_name = :packageName")
    fun getByPackageName(packageName: String): AppInfo?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(appInfo: AppInfo)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(appInfos: List<AppInfo>)

    @Update
    fun update(appInfo: AppInfo)

    @Delete
    fun delete(appInfo: AppInfo)

    @Delete
    fun deleteInTx(appInfos: List<AppInfo>)

    @Query("DELETE FROM app_info WHERE package_name IN (:packageNames)")
    fun deleteByPackageNames(packageNames: List<String>): Int

    @Query("DELETE FROM app_info")
    fun clearAll()
}

@Dao
interface NotifyRouteRuleDao {
    @Query("SELECT * FROM notify_route_rule")
    fun getAll(): List<NotifyRouteRule>

    @Query("SELECT * FROM notify_route_rule")
    fun getAllFlow(): Flow<List<NotifyRouteRule>>

    @Query("SELECT sender_id FROM notify_route_rule WHERE scope = :scope AND package_name = :packageName")
    fun getSenderIdsByScopeAndPackage(scope: Int, packageName: String): List<Long>

    @Query("SELECT sender_id FROM notify_route_rule WHERE scope = :scope AND package_name = :packageName")
    fun observeSenderIdsByScopeAndPackage(scope: Int, packageName: String): Flow<List<Long>>

    @Query("SELECT package_name FROM notify_route_rule WHERE scope = :scope AND sender_id = :senderId")
    fun getPackageNamesByScopeAndSender(scope: Int, senderId: Long): List<String>

    @Query("SELECT package_name FROM notify_route_rule WHERE scope = :scope AND sender_id = :senderId")
    fun observePackageNamesByScopeAndSender(scope: Int, senderId: Long): Flow<List<String>>

    @Query("SELECT DISTINCT sender_id FROM notify_route_rule WHERE scope = :scope AND sender_id IN (:senderIds)")
    fun getDistinctSenderIdsByScopeIn(scope: Int, senderIds: List<Long>): List<Long>

    @Query("DELETE FROM notify_route_rule WHERE scope = :scope AND package_name = :packageName")
    fun deleteByScopeAndPackage(scope: Int, packageName: String): Int

    @Query("DELETE FROM notify_route_rule WHERE scope = :scope AND sender_id = :senderId")
    fun deleteByScopeAndSender(scope: Int, senderId: Long): Int

    @Query("DELETE FROM notify_route_rule WHERE scope IN (:scopes) AND sender_id = :senderId")
    fun deleteByScopesAndSender(scopes: List<Int>, senderId: Long): Int

    @Query("DELETE FROM notify_route_rule")
    fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(rule: NotifyRouteRule): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(rules: List<NotifyRouteRule>)
}
