package com.openavc.panel.kiosk

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Persists kiosk configuration: whether the user has asked the app to lock
 * itself into kiosk mode, and a salted SHA-256 hash of the admin PIN.
 *
 * The PIN is never stored in plaintext. It only needs to resist shoulder
 * surfing and casual tampering by end users at the panel — not a cryptographic
 * attacker with filesystem access — but using a salted hash is still cheap and
 * avoids leaking the PIN to anyone who dumps SharedPreferences.
 */
class KioskPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var kioskEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_ENABLED, value) }

    fun hasPin(): Boolean = prefs.contains(KEY_PIN_HASH)

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hash(pin, salt)
        prefs.edit {
            putString(KEY_PIN_SALT, salt.toHex())
            putString(KEY_PIN_HASH, hash.toHex())
        }
    }

    fun clearPin() {
        prefs.edit {
            remove(KEY_PIN_SALT)
            remove(KEY_PIN_HASH)
        }
    }

    fun checkPin(pin: String): Boolean {
        val saltHex = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val expected = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val salt = saltHex.hexToBytes() ?: return false
        val actual = hash(pin, salt).toHex()
        return constantTimeEquals(expected, actual)
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        md.update(pin.toByteArray(Charsets.UTF_8))
        return md.digest()
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }

    private fun String.hexToBytes(): ByteArray? {
        if (length % 2 != 0) return null
        return ByteArray(length / 2) { i ->
            val high = Character.digit(this[i * 2], 16)
            val low = Character.digit(this[i * 2 + 1], 16)
            if (high < 0 || low < 0) return null
            ((high shl 4) or low).toByte()
        }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].code xor b[i].code)
        return result == 0
    }

    companion object {
        private const val FILE_NAME = "openavc_kiosk_prefs"
        private const val KEY_ENABLED = "kiosk_enabled"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
    }
}
