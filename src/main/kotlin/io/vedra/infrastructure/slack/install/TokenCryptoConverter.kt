package io.vedra.infrastructure.slack.install

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Converter
@Component
class TokenCryptoConverter(
    @Value($$"${slack.token-encryption-key:}") private val keyB64: String,
) : AttributeConverter<String, String> {
    private val key: SecretKeySpec? =
        runCatching {
            if (keyB64.isBlank()) null else SecretKeySpec(normalizeKey(keyB64), "AES")
        }.getOrNull()

    override fun convertToDatabaseColumn(attribute: String?): String? {
        if (attribute == null) return null
        val k = key ?: return PLAIN_PREFIX + attribute
        val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
        val cipher =
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, k, GCMParameterSpec(TAG_BITS, iv))
            }
        val cipherText = cipher.doFinal(attribute.toByteArray(Charsets.UTF_8))
        return ENC_PREFIX + Base64.getEncoder().encodeToString(iv + cipherText)
    }

    override fun convertToEntityAttribute(dbData: String?): String? {
        if (dbData == null) return null
        if (dbData.startsWith(PLAIN_PREFIX)) return dbData.removePrefix(PLAIN_PREFIX)
        if (!dbData.startsWith(ENC_PREFIX)) return dbData
        val k = key ?: return dbData
        return runCatching {
            val bytes = Base64.getDecoder().decode(dbData.removePrefix(ENC_PREFIX))
            val iv = bytes.copyOfRange(0, IV_LEN)
            val cipherText = bytes.copyOfRange(IV_LEN, bytes.size)
            val cipher =
                Cipher.getInstance(TRANSFORMATION).apply {
                    init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(TAG_BITS, iv))
                }
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun normalizeKey(raw: String): ByteArray {
        val decoded =
            runCatching { Base64.getDecoder().decode(raw.trim()) }.getOrNull()
                ?: raw.trim().toByteArray(Charsets.UTF_8)
        return when {
            decoded.size >= 32 -> decoded.copyOf(32)
            decoded.size >= 16 -> decoded.copyOf(16)
            else -> decoded.copyOf(16)
        }
    }

    companion object {
        const val ENC_PREFIX = "{enc}"
        const val PLAIN_PREFIX = "{plain}"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
        private const val IV_LEN = 12
    }
}
