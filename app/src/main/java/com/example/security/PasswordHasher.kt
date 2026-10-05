package com.example.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class PasswordHasher(
    private val iterations: Int = DEFAULT_ITERATIONS,
    private val keyLengthBits: Int = DEFAULT_KEY_LENGTH_BITS,
    private val saltLengthBytes: Int = DEFAULT_SALT_LENGTH_BYTES
) {
    private val secureRandom = SecureRandom()

    companion object {
        const val DEFAULT_ITERATIONS = 12_000
        const val DEFAULT_KEY_LENGTH_BITS = 256
        const val DEFAULT_SALT_LENGTH_BYTES = 16
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"

        val DEFAULT = PasswordHasher()
    }

    data class HashResult(
        val hashHex: String,
        val saltHex: String
    )

    fun generateSalt(): ByteArray {
        val salt = ByteArray(saltLengthBytes)
        secureRandom.nextBytes(salt)
        return salt
    }

    fun hash(pin: String, salt: ByteArray = generateSalt()): HashResult {
        require(pin.isNotBlank()) { "PIN cannot be blank" }
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, keyLengthBits)
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        val hashBytes = factory.generateSecret(spec).encoded
        spec.clearPassword()

        return HashResult(
            hashHex = hashBytes.toHex(),
            saltHex = salt.toHex()
        )
    }

    fun verify(pin: String, expectedHashHex: String, saltHex: String): Boolean {
        if (pin.isBlank() || expectedHashHex.isBlank() || saltHex.isBlank()) {
            return false
        }
        return try {
            val saltBytes = saltHex.fromHex()
            val spec = PBEKeySpec(pin.toCharArray(), saltBytes, iterations, keyLengthBits)
            val factory = SecretKeyFactory.getInstance(ALGORITHM)
            val computedHash = factory.generateSecret(spec).encoded
            spec.clearPassword()

            val expectedBytes = expectedHashHex.fromHex()
            MessageDigest.isEqual(computedHash, expectedBytes)
        } catch (e: Exception) {
            false
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.fromHex(): ByteArray {
        require(length % 2 == 0) { "Hex string must have an even length" }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
