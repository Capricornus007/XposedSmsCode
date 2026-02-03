package com.tianma.xsmscode.common.utils

import com.tianma.xsmscode.common.serialization.JsonConfig
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.serializer
import java.io.Reader

/**
 * Json utils using Kotlin Serialization
 */
object JsonUtils {

    val json = JsonConfig.json

    @JvmStatic
    inline fun <reified T> toJson(obj: T): String {
        return json.encodeToString(obj)
    }

    @JvmStatic
    inline fun <reified T> toJson(obj: T, writer: Appendable, excludeExposeAnnotation: Boolean = true) {
        writer.append(toJson(obj))
    }

    @JvmStatic
    inline fun <reified T> entityFromJson(
        jsonString: String?,
        typeClass: Class<T>? = null,
        excludeExposeAnnotation: Boolean = true
    ): T? {
        if (jsonString.isNullOrEmpty()) return null
        // typeClass is ignored in KOS reified, kept for compatibility if needed, but nullable
        return json.decodeFromString<T>(jsonString)
    }

    @JvmStatic
    inline fun <reified T> entityFromJson(
        reader: Reader?,
        typeClass: Class<T>? = null,
        excludeExposeAnnotation: Boolean = true
    ): T? {
        if (reader == null) return null
        val jsonString = reader.readText()
        return json.decodeFromString<T>(jsonString)
    }

    @JvmStatic
    inline fun <reified T> listFromJson(
        jsonString: String?,
        typeClass: Class<T>? = null,
        excludeExposeAnnotation: Boolean = true
    ): List<T> {
        if (jsonString.isNullOrEmpty()) return emptyList()
        return json.decodeFromString(jsonString)
    }

    @JvmStatic
    inline fun <reified T> listFromJson(
        reader: Reader?,
        typeClass: Class<T>? = null,
        excludeExposeAnnotation: Boolean = true
    ): List<T> {
        if (reader == null) return emptyList()
        val jsonString = reader.readText()
        return json.decodeFromString(jsonString)
    }

    @JvmStatic
    fun <T : Any> listFromJson(jsonString: String, entityClass: Class<T>): List<T> {
        @Suppress("UNCHECKED_CAST")
        val entitySerializer = serializer(entityClass) as KSerializer<T>
        return json.decodeFromString(ListSerializer(entitySerializer), jsonString)
    }

    @JvmStatic
    fun <T : Any> listFromJson(reader: Reader?, entityClass: Class<T>): List<T> {
        if (reader == null) return emptyList()
        val jsonString = reader.readText()
        return listFromJson(jsonString, entityClass)
    }

    @JvmStatic
    fun <T : Any> listToJson(list: List<T>, entityClass: Class<T>): String {
        @Suppress("UNCHECKED_CAST")
        val entitySerializer = serializer(entityClass) as KSerializer<T>
        return json.encodeToString(ListSerializer(entitySerializer), list)
    }
}
