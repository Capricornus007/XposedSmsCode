package com.tianma.xsmscode.data.db.entity

import androidx.compose.runtime.Immutable
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

@Immutable
@Entity(
    tableName = "sms_msg",
    indices = [
        androidx.room.Index(value = ["sender", "body", "date"], unique = true)
    ]
)
@Parcelize
@Serializable
data class SmsMsg(
    @PrimaryKey(autoGenerate = true)
    @SerialName("id")
    val id: Long? = null,

    @ColumnInfo(name = "sender")
    @SerialName("sender")
    val sender: String? = null,

    @ColumnInfo(name = "body")
    @SerialName("body")
    val body: String? = null,

    @ColumnInfo(name = "date")
    @SerialName("date")
    val date: Long = 0,

    @ColumnInfo(name = "company")
    @SerialName("company")
    val company: String? = null,

    @ColumnInfo(name = "sms_code")
    @SerialName("code")
    val smsCode: String? = null,

    @ColumnInfo(name = "package_name")
    @SerialName("packageName")
    val packageName: String? = null
) : Parcelable {

    companion object {
        @JvmStatic
        fun fromIntent(intent: Intent): SmsMsg {
            val smsMessageParts = SmsMessageUtils.fromIntent(intent)
            if (smsMessageParts.isEmpty()) return SmsMsg()
            
            var sender = smsMessageParts[0].displayOriginatingAddress
            var body = SmsMessageUtils.getMessageBody(smsMessageParts)
            val date = smsMessageParts[0].timestampMillis

            sender = Normalizer.normalize(sender, Normalizer.Form.NFC)
            body = Normalizer.normalize(body, Normalizer.Form.NFC)

            return SmsMsg(
                sender = sender,
                body = body,
                date = date
            )
        }
    }
}
