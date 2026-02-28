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
], version = 12, exportSchema = false)
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

        private val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE sms_msg ADD COLUMN msg_type INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        private val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE app_info ADD COLUMN forwarding INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }


        private val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Ensure forwarding column exists in app_info if it wasn't added in 8_9
                try {
                    db.execSQL("ALTER TABLE app_info ADD COLUMN forwarding INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {
                    // Column might already exist
                    e.printStackTrace()
                }
            }
        }

        private val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE app_info ADD COLUMN notify_template TEXT NOT NULL DEFAULT ''")
                } catch (e: Exception) {
                    // Column might already exist
                    e.printStackTrace()
                }
            }
        }

        private val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE Sender ADD COLUMN receive_app_notify INTEGER NOT NULL DEFAULT 1")
                } catch (e: Exception) {
                    // Column might already exist
                    e.printStackTrace()
                }
            }
        }

        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            val dbContext = context.applicationContext ?: context
            instance ?: Room.databaseBuilder(
                dbContext,
                AppDatabase::class.java,
                DATABASE_NAME,
            )
                .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
                .enableMultiInstanceInvalidation()
                .build().also { instance = it }
        }
    }
}
