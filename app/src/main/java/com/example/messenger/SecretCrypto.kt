package com.example.messenger

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

object SecretCrypto {

    // Standard SHA-256 hashing for zero-plaintext matching of codes
    fun hashString(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(input.trim().lowercase().toByteArray(Charsets.UTF_8))
            Base64.encodeToString(hashBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            ""
        }
    }

    // Encrypt content using AES-CBC with PKCS5Padding
    fun encryptAES(plainText: String, passcode: String): String {
        if (plainText.isEmpty()) return ""
        return try {
            // Derive a 256-bit AES key from the SHA-256 passcode hash
            val shaKey = MessageDigest.getInstance("SHA-256").digest(passcode.toByteArray(Charsets.UTF_8))
            val keySpec = SecretKeySpec(shaKey, "AES")

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val secureRandom = SecureRandom()
            val iv = ByteArray(16)
            secureRandom.nextBytes(iv)
            val ivSpec = IvParameterSpec(iv)

            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            // Combine IV and Ciphertext for zero-storage tracking
            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)

            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            ""
        }
    }

    // Decrypt content using AES-CBC
    fun decryptAES(cipherTextBase64: String, passcode: String): String {
        if (cipherTextBase64.isEmpty()) return ""
        return try {
            val combined = Base64.decode(cipherTextBase64, Base64.NO_WRAP)
            if (combined.size < 16) return ""

            val iv = ByteArray(16)
            System.arraycopy(combined, 0, iv, 0, 16)
            val ivSpec = IvParameterSpec(iv)

            val encryptedBytes = ByteArray(combined.size - 16)
            System.arraycopy(combined, 16, encryptedBytes, 0, encryptedBytes.size)

            val shaKey = MessageDigest.getInstance("SHA-256").digest(passcode.toByteArray(Charsets.UTF_8))
            val keySpec = SecretKeySpec(shaKey, "AES")

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val decryptedBytes = cipher.doFinal(encryptedBytes)

            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }
}
