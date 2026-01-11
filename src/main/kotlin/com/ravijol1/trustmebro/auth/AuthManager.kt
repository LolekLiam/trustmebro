package com.ravijol1.trustmebro.auth

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Handles user authentication data storage and password verification.
 *
 * Data schema (users.yml):
 * users:
 *   username_lower:
 *     hash: base64
 *     salt: base64
 *     lastIp: 127.0.0.1
 */
class AuthManager(private val plugin: JavaPlugin) {
    private val usersFile: File = File(plugin.dataFolder, "users.yml")
    private var config: FileConfiguration = YamlConfiguration()

    private val random = SecureRandom()

    init {
        load()
    }

    fun load() {
        if (!plugin.dataFolder.exists()) plugin.dataFolder.mkdirs()
        if (!usersFile.exists()) {
            usersFile.createNewFile()
            config = YamlConfiguration()
            config.set("users", HashMap<String, Any>())
            save()
        } else {
            config = YamlConfiguration.loadConfiguration(usersFile)
            if (!config.isConfigurationSection("users")) {
                config.set("users", HashMap<String, Any>())
                save()
            }
        }
    }

    fun save() {
        config.save(usersFile)
    }

    fun isRegistered(username: String): Boolean {
        val key = key(username)
        return config.isConfigurationSection("users.$key")
    }

    fun getLastIp(username: String): String? {
        return config.getString("users.${key(username)}.lastIp")
    }

    fun setLastIp(username: String, ip: String) {
        config.set("users.${key(username)}.lastIp", ip)
        save()
    }

    fun register(username: String, password: String) {
        val key = key(username)
        val salt = ByteArray(16)
        random.nextBytes(salt)
        val hash = hashPassword(password, salt)
        val b64 = Base64.getEncoder()
        config.set("users.$key.hash", b64.encodeToString(hash))
        config.set("users.$key.salt", b64.encodeToString(salt))
        save()
    }

    fun changePassword(username: String, newPassword: String) {
        // same as register but keep lastIp
        val key = key(username)
        val salt = ByteArray(16)
        random.nextBytes(salt)
        val hash = hashPassword(newPassword, salt)
        val b64 = Base64.getEncoder()
        config.set("users.$key.hash", b64.encodeToString(hash))
        config.set("users.$key.salt", b64.encodeToString(salt))
        save()
    }

    fun verify(username: String, password: String): Boolean {
        val key = key(username)
        val b64 = Base64.getDecoder()
        val saltB64 = config.getString("users.$key.salt") ?: return false
        val hashB64 = config.getString("users.$key.hash") ?: return false
        val salt = b64.decode(saltB64)
        val expected = b64.decode(hashB64)
        val actual = hashPassword(password, salt)
        return constantTimeEquals(expected, actual)
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var res = 0
        for (i in a.indices) {
            res = res or (a[i].toInt() xor b[i].toInt())
        }
        return res == 0
    }

    private fun hashPassword(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 65536, 256)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return skf.generateSecret(spec).encoded
    }

    private fun key(username: String): String = username.lowercase()
}
