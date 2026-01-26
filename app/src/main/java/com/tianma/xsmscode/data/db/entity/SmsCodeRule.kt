package com.tianma.xsmscode.data.db.entity

import android.os.Parcelable
import androidx.room.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.tianma.xsmscode.feature.backup.BackupConst
import kotlinx.parcelize.Parcelize

@Entity(
    tableName = "sms_code_rule",
    indices = [
        Index(value = ["company", "code_keyword", "code_regex"], unique = true)
    ]
)
@Serializable
@Parcelize
data class SmsCodeRule @JvmOverloads constructor(
    @ColumnInfo(name = "company")
    @SerialName(BackupConst.KEY_COMPANY)
    var company: String? = null,

    @ColumnInfo(name = "code_keyword")
    @SerialName(BackupConst.KEY_CODE_KEYWORD)
    var codeKeyword: String = "",

    @ColumnInfo(name = "code_regex")
    @SerialName(BackupConst.KEY_CODE_REGEX)
    var codeRegex: String = "",

    @PrimaryKey(autoGenerate = true)
    var id: Long? = null
) : Parcelable {

    fun copyFrom(newRule: SmsCodeRule) {
        this.id = newRule.id
        this.company = newRule.company
        this.codeKeyword = newRule.codeKeyword
        this.codeRegex = newRule.codeRegex
    }
}
