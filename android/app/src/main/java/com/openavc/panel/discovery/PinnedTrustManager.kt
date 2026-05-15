package com.openavc.panel.discovery

import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/**
 * X509TrustManager that trusts a TLS handshake only if at least one cert in
 * the presented chain is byte-for-byte identical to one of [pinnedCerts].
 *
 * In Auto-generate mode the pin is the server's CA, which appears in the chain
 * because OpenAVC's leaf is signed directly by it. In Provided mode the pin is
 * the leaf, which is index 0 of the chain. Either way: byte-equal-anywhere-in-chain.
 *
 * Used as the trust manager for HttpsURLConnection in [ServerValidator] and
 * any other code paths that hit the OpenAVC server. The WebView has its own
 * trust hook ([android.webkit.WebViewClient.onReceivedSslError]) and uses a
 * different match rule there because it only sees the leaf.
 */
class PinnedTrustManager(pinnedCerts: Collection<X509Certificate>) : X509TrustManager {

    private val pinnedEncodings: List<ByteArray> = pinnedCerts.map { it.encoded }
    private val accepted: Array<X509Certificate> = pinnedCerts.toTypedArray()

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("Client auth not supported")
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        if (chain == null || chain.isEmpty()) {
            throw CertificateException("Empty server certificate chain")
        }
        for (cert in chain) {
            val encoded = cert.encoded
            if (pinnedEncodings.any { it.contentEquals(encoded) }) return
        }
        throw CertificateException("No pinned certificate matched the server chain")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = accepted
}
