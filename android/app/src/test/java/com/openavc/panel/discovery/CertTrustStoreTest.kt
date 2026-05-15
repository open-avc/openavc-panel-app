package com.openavc.panel.discovery

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.BeforeClass
import org.junit.Test
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class CertTrustStoreTest {

    @Test
    fun lookup_prefers_instanceId_over_hostPort() {
        val (instanceCert, _) = PinnedTrustManagerTest.newSelfSigned("CN=instance")
        val (hostCert, _) = PinnedTrustManagerTest.newSelfSigned("CN=host")
        val prefs = FakeSharedPreferences()
        val store = CertTrustStore(prefs)

        store.pin(instanceId = "abc-123", hostPort = null, pemBytes = pem(instanceCert))
        store.pin(instanceId = null, hostPort = "10.0.0.5:8443", pemBytes = pem(hostCert))

        val resolved = store.lookup("abc-123", "10.0.0.5:8443")
        assertEquals(instanceCert.encoded.toList(), resolved?.encoded?.toList())
    }

    @Test
    fun lookup_falls_back_to_hostPort_when_instance_pin_missing() {
        val (hostCert, _) = PinnedTrustManagerTest.newSelfSigned("CN=fallback")
        val prefs = FakeSharedPreferences()
        val store = CertTrustStore(prefs)

        store.pin(instanceId = null, hostPort = "10.0.0.5:8443", pemBytes = pem(hostCert))

        val resolved = store.lookup("abc-123", "10.0.0.5:8443")
        assertEquals(hostCert.encoded.toList(), resolved?.encoded?.toList())
    }

    @Test
    fun lookup_returns_null_when_nothing_pinned() {
        val store = CertTrustStore(FakeSharedPreferences())
        assertNull(store.lookup("abc-123", "10.0.0.5:8443"))
    }

    @Test
    fun clear_removes_both_keys() {
        val (cert, _) = PinnedTrustManagerTest.newSelfSigned("CN=clearable")
        val prefs = FakeSharedPreferences()
        val store = CertTrustStore(prefs)
        store.pin("abc", "10.0.0.5:8443", pem(cert))

        store.clear("abc", "10.0.0.5:8443")

        assertNull(store.lookup("abc", "10.0.0.5:8443"))
    }

    @Test
    fun hostPortKey_is_lowercase() {
        assertEquals("server.local:8443", CertTrustStore.hostPortKey("SERVER.local", 8443))
    }

    private fun pem(cert: java.security.cert.X509Certificate): ByteArray =
        CertFetcher.pemEncode(cert).toByteArray(Charsets.UTF_8)

    companion object {
        @BeforeClass
        @JvmStatic
        fun installBc() {
            if (Security.getProvider("BC") == null) {
                Security.addProvider(BouncyCastleProvider())
            }
        }
    }
}

/**
 * Minimal in-memory [SharedPreferences] for unit tests. Only implements the
 * methods [CertTrustStore] actually calls (getString, edit / Editor's putString,
 * remove, commit/apply). Everything else throws — if the production code ever
 * starts calling, say, getInt, this fake fails loudly instead of silently
 * returning a default that masks a regression.
 */
private class FakeSharedPreferences : SharedPreferences {
    private val map = mutableMapOf<String, String>()

    override fun getString(key: String, defValue: String?): String? = map[key] ?: defValue

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, String?>()

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            pending[key] = value
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            pending[key] = null
            return this
        }

        override fun commit(): Boolean { apply(); return true }

        override fun apply() {
            pending.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v }
            pending.clear()
        }

        override fun clear(): SharedPreferences.Editor = throw unsupported()
        override fun putStringSet(k: String, v: Set<String>?): SharedPreferences.Editor = throw unsupported()
        override fun putInt(k: String, v: Int): SharedPreferences.Editor = throw unsupported()
        override fun putLong(k: String, v: Long): SharedPreferences.Editor = throw unsupported()
        override fun putFloat(k: String, v: Float): SharedPreferences.Editor = throw unsupported()
        override fun putBoolean(k: String, v: Boolean): SharedPreferences.Editor = throw unsupported()
    }

    override fun getAll(): MutableMap<String, *> = map.toMutableMap()
    override fun contains(key: String): Boolean = map.containsKey(key)

    override fun getStringSet(k: String, d: MutableSet<String>?) = throw unsupported()
    override fun getInt(k: String, d: Int): Int = throw unsupported()
    override fun getLong(k: String, d: Long): Long = throw unsupported()
    override fun getFloat(k: String, d: Float): Float = throw unsupported()
    override fun getBoolean(k: String, d: Boolean): Boolean = throw unsupported()
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = throw unsupported()
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = throw unsupported()

    private fun unsupported() = UnsupportedOperationException("Not implemented in FakeSharedPreferences")
}
