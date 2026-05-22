package com.github.magisk317.smscode.data.db

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DBProviderContractTest {
    @Test
    fun isSupportedDateSortOrder_acceptsOnlyDateSortsWithOptionalLimit() {
        assertTrue(DBProvider.Contract.isSupportedDateSortOrder(null))
        assertTrue(DBProvider.Contract.isSupportedDateSortOrder(""))
        assertTrue(DBProvider.Contract.isSupportedDateSortOrder("date desc"))
        assertTrue(DBProvider.Contract.isSupportedDateSortOrder("DATE  ASC LIMIT 20"))

        assertFalse(DBProvider.Contract.isSupportedDateSortOrder("processed_time desc"))
        assertFalse(DBProvider.Contract.isSupportedDateSortOrder("date desc limit 0"))
        assertFalse(DBProvider.Contract.isSupportedDateSortOrder("date desc; drop table sms_msg"))
    }

    @Test
    fun isProjectionSupported_rejectsUnknownColumns() {
        assertTrue(DBProvider.Contract.isProjectionSupported(null, DBProvider.SMS_MSG_COLUMNS))
        assertTrue(DBProvider.Contract.isProjectionSupported(arrayOf("_id", "date", "body"), DBProvider.SMS_MSG_COLUMNS))

        assertFalse(DBProvider.Contract.isProjectionSupported(arrayOf("_id", "unknown"), DBProvider.SMS_MSG_COLUMNS))
    }
}
