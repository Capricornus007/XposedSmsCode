package com.tianma.xsmscode.data.db.entity

import android.content.Intent
import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tianma.xsmscode.common.utils.SmsMessageUtils
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.Normalizer

@Entity(tableName = "sms_msg")
@Parcelize
@Serializable
data class SmsMsg(
    @PrimaryKey(autoGenerate = true)
    @SerialName("id")
    var id: Long? = null,

    @ColumnInfo(name = "sender")
    @SerialName("sender")
    var sender: String? = null,

    @ColumnInfo(name = "body")
    @SerialName("body")
    var body: String? = null,

    @ColumnInfo(name = "date")
    @SerialName("date")
    var date: Long = 0,

    @ColumnInfo(name = "company")
    @SerialName("company")
    var company: String? = null,

    @ColumnInfo(name = "sms_code")
    @SerialName("code")
    var smsCode: String? = null
) : Parcelable {

    companion object {
        @JvmStatic
        fun fromIntent(intent: Intent): SmsMsg {
            val smsMessageParts = SmsMessageUtils.fromIntent(intent)
            var sender = smsMessageParts[0].displayOriginatingAddress
            var body = SmsMessageUtils.getMessageBody(smsMessageParts)

            sender = Normalizer.normalize(sender, Normalizer.Form.NFC)
            body = Normalizer.normalize(body, Normalizer.Form.NFC)

            val message = SmsMsg()
            message.sender = sender
            message.body = body
            return message
        }
    }
}
