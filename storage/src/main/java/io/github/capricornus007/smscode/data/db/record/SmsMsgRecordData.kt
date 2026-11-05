package io.github.capricornus007.smscode.data.db.record

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * UI-facing projection of a stored SMS record.
 *
 * The record screen consumes this type instead of the Room entity so the UI stays
 * decoupled from the storage schema; DBManager maps between the two.
 */
@Immutable
@Serializable
data class SmsMsgRecordData(
    @SerialName("id")
    val id: Long? = null,

    @SerialName("sender")
    val sender: String? = null,

    @SerialName("body")
    val body: String? = null,

    @SerialName("date")
    val date: Long = 0,

    @SerialName("company")
    val company: String? = null,

    @SerialName("code")
    val smsCode: String? = null,

    @SerialName("packageName")
    val packageName: String? = null,

    @SerialName("notifyChannelId")
    val notifyChannelId: String = "",

    @SerialName("forwardStatus")
    val forwardStatus: Int = FORWARD_STATUS_NONE,

    @SerialName("forwardTarget")
    val forwardTarget: String? = null,

    @SerialName("forwardMessage")
    val forwardMessage: String? = null,

    @SerialName("forwardTime")
    val forwardTime: Long = 0L,

    @SerialName("msgType")
    val msgType: Int = MSG_TYPE_SMS,

    @SerialName("callType")
    val callType: Int = 0,
) {
    companion object {
        const val FORWARD_STATUS_NONE = 0
        const val FORWARD_STATUS_SUCCESS = 1
        const val FORWARD_STATUS_FAILED = 2
        const val FORWARD_STATUS_PARTIAL = 3
        const val FORWARD_STATUS_BLOCKED = 4

        const val MSG_TYPE_SMS = 0
        const val MSG_TYPE_APP_NOTIFY = 1
        const val MSG_TYPE_CALL_NOTIFY = 2
    }
}
