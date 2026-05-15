package com.openavc.panel.discovery

import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.assertThrows
import org.junit.BeforeClass
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Security
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Date
import javax.security.auth.x500.X500Principal

class PinnedTrustManagerTest {

    @Test
    fun accepts_chain_when_pinned_cert_present_at_index_zero() {
        val (cert, _) = newSelfSigned("CN=pin-leaf")
        val tm = PinnedTrustManager(listOf(cert))
        tm.checkServerTrusted(arrayOf(cert), "RSA")
    }

    @Test
    fun accepts_chain_when_pinned_cert_present_deeper_in_chain() {
        val (pinned, _) = newSelfSigned("CN=pinned-ca")
        val (leaf, _) = newSelfSigned("CN=leaf")
        val tm = PinnedTrustManager(listOf(pinned))
        // We don't care about issuer relationships in the trust manager —
        // it walks the chain looking for byte-equal entries.
        tm.checkServerTrusted(arrayOf(leaf, pinned), "RSA")
    }

    @Test
    fun rejects_chain_when_pinned_cert_absent() {
        val (pinned, _) = newSelfSigned("CN=pinned")
        val (other, _) = newSelfSigned("CN=other")
        val tm = PinnedTrustManager(listOf(pinned))
        assertThrows(CertificateException::class.java) {
            tm.checkServerTrusted(arrayOf(other), "RSA")
        }
    }

    @Test
    fun rejects_empty_chain() {
        val (pinned, _) = newSelfSigned("CN=pinned")
        val tm = PinnedTrustManager(listOf(pinned))
        assertThrows(CertificateException::class.java) {
            tm.checkServerTrusted(emptyArray(), "RSA")
        }
    }

    @Test
    fun client_trust_always_throws() {
        val (pinned, _) = newSelfSigned("CN=pinned")
        val tm = PinnedTrustManager(listOf(pinned))
        assertThrows(CertificateException::class.java) {
            tm.checkClientTrusted(arrayOf(pinned), "RSA")
        }
    }

    companion object {

        @BeforeClass
        @JvmStatic
        fun installBouncyCastle() {
            if (Security.getProvider("BC") == null) {
                Security.addProvider(BouncyCastleProvider())
            }
        }

        internal fun newSelfSigned(subjectDn: String): Pair<X509Certificate, KeyPair> {
            val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val now = System.currentTimeMillis()
            val builder: X509v3CertificateBuilder = JcaX509v3CertificateBuilder(
                X500Principal(subjectDn),
                BigInteger.valueOf(now),
                Date(now - 60_000),
                Date(now + 60 * 60_000),
                X500Principal(subjectDn),
                kp.public,
            )
            val signer = JcaContentSignerBuilder("SHA256withRSA").build(kp.private)
            val holder = builder.build(signer)
            val cert = JcaX509CertificateConverter().getCertificate(holder)
            return cert to kp
        }
    }
}
