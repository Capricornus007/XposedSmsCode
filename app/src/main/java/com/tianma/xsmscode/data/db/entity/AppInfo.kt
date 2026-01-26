package com.tianma.xsmscode.data.db.entity

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Entity(tableName = "app_info")
@Parcelize
@Serializable
data class AppInfo @JvmOverloads constructor(
    @PrimaryKey
    @ColumnInfo(name = "package_name")
    @SerialName("packageName")
    var packageName: String = "",

    @ColumnInfo(name = "label")
    @SerialName("label")
    var label: String? = null,

    @ColumnInfo(name = "blocked")
    @SerialName("blocked")
    @get:JvmName("isBlocked")
    var blocked: Boolean = false
) : Parcelable
