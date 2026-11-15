package com.patbaumgartner.android.myapps.domain.verify

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** SHA-256 helpers shared by the verifier and the unit tests. */
object Checksums {

    private const val BUFFER_SIZE = 256 * 1024

    fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toColonHex().replace(":", "").lowercase()

    fun sha256Hex(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().toColonHex().replace(":", "").lowercase()
    }

    fun sha256Hex(file: File): String = file.inputStream().use(::sha256Hex)

    /** Formats bytes as colon-separated uppercase hex, e.g. the certificate fingerprint form. */
    fun ByteArray.toColonHex(): String = joinToString(":") { "%02X".format(it) }

    fun normalizeFingerprint(value: String): String = value.replace(":", "").uppercase()

    fun isValidSha256Hex(value: String): Boolean = value.matches(Regex("[0-9a-fA-F]{64}"))
}
