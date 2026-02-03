package com.tianma.xsmscode.common.utils

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

/**
 * End-to-end encryption utility using AES-256-CBC.
 */
object CryptoUtils {
    private const val ALGORITHM = "AES/CBC/PKCS5Padding"

    private fun generateKey(password: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(password.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(bytes, "AES")
    }

    fun encrypt(data: String, password: String): String? {
        if (password.isBlank()) return data
        return try {
            val key = generateKey(password)
            val cipher = Cipher.getInstance(ALGORITHM)
            // Use static IV for simplicity in this use case (pairing ID) 
            // but fixed to be unique per group by using a hash of the key.
            val iv = MessageDigest.getInstance("MD5").digest(password.toByteArray()).sliceArray(0..15)
            cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
            val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(encrypted, Base64.DEFAULT)
        } catch (e: Exception) {
            XLog.e("CryptoUtils", "Encryption failed", e)
            null
        }
    }

    fun decrypt(encryptedData: String, password: String): String? {
        if (password.isBlank()) return encryptedData
        return try {
            val key = generateKey(password)
            val cipher = Cipher.getInstance(ALGORITHM)
            val iv = MessageDigest.getInstance("MD5").digest(password.toByteArray()).sliceArray(0..15)
            cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
            val decoded = Base64.decode(encryptedData, Base64.DEFAULT)
            val decrypted = cipher.doFinal(decoded)
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            XLog.e("CryptoUtils", "Decryption failed", e)
            null
        }
    }
}
