package com.example.mywarpvpn.data.config

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.example.mywarpvpn.domain.model.ConfigSummary
import com.wireguard.config.BadConfigException
import com.wireguard.config.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores a WireGuard config as AES-GCM ciphertext in no-backup app storage. The encryption key
 * stays in Android Keystore. Plaintext is only read into memory while importing or connecting.
 * No key, endpoint secret, or config content is written to logs.
 */
class EncryptedConfigStore(context: Context) {
    private val appContext = context.applicationContext
    private val configFile = File(appContext.noBackupFilesDir, CONFIG_FILE_NAME)

    @Synchronized
    fun hasConfig(): Boolean = configFile.isFile && configFile.length() > 0

    @Synchronized
    fun importFrom(uri: Uri): ConfigSummary {
        val bytes = appContext.contentResolver.openInputStream(uri)?.use { input ->
            input.readBounded(MAX_CONFIG_BYTES)
        } ?: throw IllegalArgumentException("The selected file could not be read.")

        try {
            val summary = validate(bytes)
            encryptAndStore(bytes)
            return summary
        } finally {
            bytes.fill(0)
        }
    }

    @Synchronized
    fun importConfigText(configText: String): ConfigSummary {
        val bytes = configText.toByteArray(Charsets.UTF_8)
        try {
            val summary = validate(bytes)
            encryptAndStore(bytes)
            return summary
        } finally {
            bytes.fill(0)
        }
    }

    @Synchronized
    fun loadConfig(): Config {
        if (!hasConfig()) throw MissingConfigException()
        val encrypted = FileInputStream(configFile).use { it.readBytes() }
        val plaintext = try {
            decrypt(encrypted)
        } finally {
            encrypted.fill(0)
        }
        return try {
            val parsed = Config.parse(ByteArrayInputStream(plaintext))
            validateParsed(parsed)
            parsed
        } catch (_: BadConfigException) {
            throw InvalidConfigException()
        } finally {
            plaintext.fill(0)
        }
    }

    @Synchronized
    fun describeConfig(): ConfigSummary? {
        if (!hasConfig()) return null
        val encrypted = FileInputStream(configFile).use { it.readBytes() }
        val plaintext = try {
            decrypt(encrypted)
        } finally {
            encrypted.fill(0)
        }
        return try {
            validate(plaintext)
        } finally {
            plaintext.fill(0)
        }
    }

    @Synchronized
    fun deleteConfig() {
        configFile.delete()
        val keyStore = loadKeyStore()
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
    }

    private fun validate(plaintext: ByteArray): ConfigSummary {
        val parsed = try {
            Config.parse(ByteArrayInputStream(plaintext))
        } catch (_: BadConfigException) {
            throw InvalidConfigException()
        } catch (_: Exception) {
            throw InvalidConfigException()
        }

        return validateParsed(parsed)
    }

    private fun validateParsed(parsed: Config): ConfigSummary {
        val iface = parsed.getInterface()
        val peers = parsed.getPeers()
        if (iface.getAddresses().isEmpty() || peers.isEmpty() ||
            peers.none { it.getEndpoint().isPresent && it.getAllowedIps().isNotEmpty() }
        ) {
            throw InvalidConfigException()
        }

        val endpoint = peers.firstNotNullOfOrNull { peer ->
            peer.getEndpoint().orElse(null)?.toString()
        } ?: "Not specified"
        val dns = iface.getDnsServers().joinToString { it.hostAddress }.ifBlank { "From network" }
        val mtu = iface.getMtu().orElse(null)?.toString() ?: "Automatic"
        return ConfigSummary(endpoint = endpoint, dnsServers = dns, mtu = mtu, peerCount = peers.size)
    }

    private fun encryptAndStore(plaintext: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        val tempFile = File(appContext.noBackupFilesDir, "$CONFIG_FILE_NAME.tmp")
        try {
            FileOutputStream(tempFile).use { fileOutput ->
                val output = DataOutputStream(fileOutput)
                output.writeInt(iv.size)
                output.write(iv)
                output.write(ciphertext)
                output.flush()
                fileOutput.fd.sync()
            }
            try {
                Files.move(
                    tempFile.toPath(),
                    configFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tempFile.toPath(), configFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            ciphertext.fill(0)
            tempFile.delete()
        }
    }

    private fun decrypt(encrypted: ByteArray): ByteArray {
        try {
            DataInputStream(ByteArrayInputStream(encrypted)).use { input ->
                val ivSize = input.readInt()
                if (ivSize !in 12..16 || encrypted.size <= 4 + ivSize) throw InvalidConfigException()
                val iv = ByteArray(ivSize)
                input.readFully(iv)
                val ciphertext = ByteArray(input.available())
                input.readFully(ciphertext)
                try {
                    val cipher = Cipher.getInstance(TRANSFORMATION)
                    cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
                    return cipher.doFinal(ciphertext)
                } finally {
                    iv.fill(0)
                    ciphertext.fill(0)
                }
            }
        } catch (e: InvalidConfigException) {
            throw e
        } catch (_: Exception) {
            throw InvalidConfigException()
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = loadKeyStore()
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun loadKeyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val CONFIG_FILE_NAME = "wireguard-config.aesgcm"
        private const val KEY_ALIAS = "my-warp-vpn-config-key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val MAX_CONFIG_BYTES = 1024 * 1024
    }
}

private fun InputStream.readBounded(maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(4096)
    var total = 0
    try {
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            if (total > maxBytes) throw InvalidConfigException()
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    } finally {
        buffer.fill(0)
    }
}

class MissingConfigException : IllegalStateException("Import a WireGuard configuration first.")
class InvalidConfigException : IllegalArgumentException(
    "The configuration is invalid or is not a complete WireGuard client configuration.",
)
