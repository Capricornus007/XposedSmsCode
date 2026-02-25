package com.tianma.xsmscode.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.tianma.xsmscode.data.db.dao.AppInfoDao
import com.tianma.xsmscode.data.db.dao.SmsCodeRuleDao
import com.tianma.xsmscode.data.db.dao.SmsMsgDao
import com.tianma.xsmscode.data.db.entity.AppInfo
import com.tianma.xsmscode.data.db.entity.SmsCodeRule
import com.tianma.xsmscode.data.db.entity.SmsMsg

@Database(entities = [SmsCodeRule::class, SmsMsg::class, AppInfo::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun smsCodeRuleDao(): SmsCodeRuleDao
    abstract fun smsMsgDao(): SmsMsgDao
    abstract fun appInfoDao(): AppInfoDao

    companion object {
        private const val DATABASE_NAME = "xsmscode_room.db"

        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sms_msg ADD COLUMN package_name TEXT")
            }
        }

        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Deduplicate before creating the unique index
                db.execSQL(
                    "DELETE FROM sms_msg WHERE id NOT IN (SELECT MIN(id) FROM sms_msg GROUP BY sender, body, date)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_sms_msg_sender_body_date` ON `sms_msg` (`sender`, `body`, `date`)",
                )
            }
        }

        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sms_msg ADD COLUMN forward_status INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sms_msg ADD COLUMN forward_target TEXT")
                db.execSQL("ALTER TABLE sms_msg ADD COLUMN forward_message TEXT")
                db.execSQL("ALTER TABLE sms_msg ADD COLUMN forward_time INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            val dbContext = context.applicationContext ?: context
            instance ?: Room.databaseBuilder(
                dbContext,
                AppDatabase::class.java,
                DATABASE_NAME,
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .allowMainThreadQueries() // For legacy compatibility
                .build().also { instance = it }
        }
    }
}
