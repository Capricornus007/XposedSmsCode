package com.tianma.xsmscode.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.github.magisk317.smscode.forwarder.database.dao.RuleDao
import com.github.magisk317.smscode.forwarder.database.dao.SenderDao
import com.github.magisk317.smscode.forwarder.database.ext.ConvertersDate
import com.github.magisk317.smscode.forwarder.database.ext.ConvertersSenderList
import com.github.magisk317.smscode.forwarder.entity.Rule
import com.github.magisk317.smscode.forwarder.entity.Sender
import com.tianma.xsmscode.data.db.dao.AppInfoDao
import com.tianma.xsmscode.data.db.dao.SmsCodeRuleDao
import com.tianma.xsmscode.data.db.dao.SmsMsgDao
import com.tianma.xsmscode.data.db.entity.AppInfo
import com.tianma.xsmscode.data.db.entity.SmsCodeRule
import com.tianma.xsmscode.data.db.entity.SmsMsg

@Database(entities = [
    SmsCodeRule::class, 
    SmsMsg::class, 
    AppInfo::class,
    Sender::class,
    Rule::class
], version = 6, exportSchema = false)
@TypeConverters(ConvertersDate::class, ConvertersSenderList::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun smsCodeRuleDao(): SmsCodeRuleDao
    abstract fun smsMsgDao(): SmsMsgDao
    abstract fun appInfoDao(): AppInfoDao
    
    abstract fun ruleDao(): RuleDao
    abstract fun senderDao(): SenderDao

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

        private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `Sender` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` INTEGER NOT NULL DEFAULT 1, `name` TEXT NOT NULL DEFAULT '', `json_setting` TEXT NOT NULL DEFAULT '', `status` INTEGER NOT NULL DEFAULT 1, `time` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `Rule` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL DEFAULT 'sms', `filed` TEXT NOT NULL DEFAULT 'transpond_all', `check` TEXT NOT NULL DEFAULT 'is', `value` TEXT NOT NULL DEFAULT '', `sender_id` INTEGER NOT NULL DEFAULT 0, `sms_template` TEXT NOT NULL DEFAULT '', `regex_replace` TEXT NOT NULL DEFAULT '', `sim_slot` TEXT NOT NULL DEFAULT 'ALL', `status` INTEGER NOT NULL DEFAULT 1, `time` INTEGER NOT NULL, `sender_list` TEXT NOT NULL DEFAULT '', `sender_logic` TEXT NOT NULL DEFAULT 'ALL', `silent_period_start` INTEGER NOT NULL DEFAULT 0, `silent_period_end` INTEGER NOT NULL DEFAULT 0, `silent_day_of_week` TEXT NOT NULL DEFAULT '', `title` TEXT NOT NULL DEFAULT '', FOREIGN KEY(`sender_id`) REFERENCES `Sender`(`id`) ON UPDATE CASCADE ON DELETE CASCADE )")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Rule_id` ON `Rule` (`id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_Rule_sender_id` ON `Rule` (`sender_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_Rule_sender_list` ON `Rule` (`sender_list`)")
            }
        }

        private val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Sender ADD COLUMN receive_non_code INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            val dbContext = context.applicationContext ?: context
            instance ?: Room.databaseBuilder(
                dbContext,
                AppDatabase::class.java,
                DATABASE_NAME,
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .allowMainThreadQueries() // For legacy compatibility
                .build().also { instance = it }
        }
    }
}
