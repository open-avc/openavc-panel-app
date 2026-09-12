import CryptoKit
import Foundation
import Security

extension Data {
    /// Colon-separated uppercase SHA-256, the fingerprint form the Programmer
    /// IDE prints beside a server's certificate.
    func sha256Hex() -> String {
        SHA256.hash(data: self)
            .map { String(format: "%02X", $0) }
            .joined(separator: ":")
    }
}

/// Decides whether a TLS handshake is the server we pinned.
///
/// This is the iOS half of Android's `certificateMatchesPin`, and it applies
/// the same two-stage rule, because the two TLS modes pin different things:
///
///   - **Provided certificate** (the integrator uploaded their own): we pinned
///     the leaf, and the leaf comes back byte for byte. Stage one catches it.
///   - **Auto-generate**: we pinned the server's *CA*, which signs a leaf that
///     may rotate freely. The CA is often not in the presented chain at all, so
///     a byte comparison finds nothing. Stage two catches it by making the
///     pinned certificate the sole trust anchor and asking whether the chain
///     evaluates under it — the iOS equivalent of Android's
///     `leaf.verify(pinned.publicKey)`, and stricter, since it also checks
///     validity dates and chain structure rather than the signature alone.
///
/// Hostname is deliberately not checked, for the same reason as Android: the
/// server's SANs cover localhost and the addresses it knows about, but a panel
/// can legitimately reach it on one nobody enumerated. The pin *is* the trust
/// check — a matching certificate under an unexpected name is still
/// unambiguously the server we paired with. Nothing here trusts the system
/// roots: these are self-signed certificates on private addresses that no
/// public CA will ever vouch for.
enum PinnedTrust {

    static func matchesPin(_ trust: SecTrust, pinned: SecCertificate) -> Bool {
        chainContainsPin(trust, pinned: pinned) || chainAnchorsTo(trust, pinned: pinned)
    }

    /// Stage one: the pinned certificate appears in the presented chain.
    static func chainContainsPin(_ trust: SecTrust, pinned: SecCertificate) -> Bool {
        let pinnedData = SecCertificateCopyData(pinned) as Data
        for presented in chain(of: trust) {
            if (SecCertificateCopyData(presented) as Data) == pinnedData { return true }
        }
        return false
    }

    /// Stage two: the chain evaluates with the pinned certificate as its only
    /// trust anchor. This is the auto-generate path, where we hold the CA and
    /// the server presents just a leaf it signed.
    static func chainAnchorsTo(_ trust: SecTrust, pinned: SecCertificate) -> Bool {
        // A basic X509 policy rather than an SSL one: the SSL policy would
        // re-introduce the hostname check this pin deliberately replaces.
        guard SecTrustSetPolicies(trust, SecPolicyCreateBasicX509()) == errSecSuccess,
              SecTrustSetAnchorCertificates(trust, [pinned] as CFArray) == errSecSuccess,
              SecTrustSetAnchorCertificatesOnly(trust, true) == errSecSuccess
        else { return false }
        return SecTrustEvaluateWithError(trust, nil)
    }

    static func chain(of trust: SecTrust) -> [SecCertificate] {
        (SecTrustCopyCertificateChain(trust) as? [SecCertificate]) ?? []
    }

    static func leaf(of trust: SecTrust) -> SecCertificate? {
        chain(of: trust).first
    }

    /// Answers a server-trust challenge against a pinned certificate.
    ///
    /// Hostname is not checked, on purpose and for the same reason as Android:
    /// the server's SANs cover localhost and the LAN addresses it knows about,
    /// but a panel can legitimately reach it on an address nobody enumerated.
    /// The pin *is* the trust check — a matching certificate on an unexpected
    /// name is still unambiguously the server we paired with.
    static func evaluate(
        challenge: URLAuthenticationChallenge,
        pinned: SecCertificate?
    ) -> (URLSession.AuthChallengeDisposition, URLCredential?) {
        guard challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
              let trust = challenge.protectionSpace.serverTrust
        else {
            return (.performDefaultHandling, nil)
        }
        guard let pinned else {
            return (.cancelAuthenticationChallenge, nil)
        }
        guard matchesPin(trust, pinned: pinned) else {
            return (.cancelAuthenticationChallenge, nil)
        }
        return (.useCredential, URLCredential(trust: trust))
    }
}

/// A `URLSession` delegate that trusts exactly one pinned certificate, and
/// refuses to follow a redirect rather than chasing it.
///
/// The redirect refusal is the load-bearing part. `URLSession` will happily
/// follow http -> https across schemes, which would hide the very handoff the
/// validator needs to reason about: with a cloud certificate active, the
/// server's redirect Location carries its public DNS name, which may not
/// resolve without internet and presents a CA-signed chain that a pinned
/// self-signed certificate can never match. Stopping here lets the validator
/// keep the host it actually probed and honour only the scheme and port.
final class PinnedSessionDelegate: NSObject, URLSessionDataDelegate {
    private let pinned: SecCertificate?

    init(pinned: SecCertificate?) {
        self.pinned = pinned
    }

    func urlSession(
        _ session: URLSession,
        didReceive challenge: URLAuthenticationChallenge,
        completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        let (disposition, credential) = PinnedTrust.evaluate(challenge: challenge, pinned: pinned)
        completionHandler(disposition, credential)
    }

    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        willPerformHTTPRedirection response: HTTPURLResponse,
        newRequest request: URLRequest,
        completionHandler: @escaping (URLRequest?) -> Void
    ) {
        completionHandler(nil)
    }
}

/// A delegate that trusts any server certificate, used only for first contact.
///
/// Trust-on-first-use, exactly as on Android: we have to complete one handshake
/// to learn what the server's certificate even is before we can pin it. Every
/// later connection goes through `PinnedSessionDelegate`. The admin sheet shows
/// the pinned fingerprint so it can be compared against the Programmer IDE.
final class TrustAllSessionDelegate: NSObject, URLSessionDataDelegate {
    /// The leaf the server presented during the handshake, captured for pinning.
    private(set) var capturedLeaf: SecCertificate?

    func urlSession(
        _ session: URLSession,
        didReceive challenge: URLAuthenticationChallenge,
        completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        guard challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
              let trust = challenge.protectionSpace.serverTrust
        else {
            completionHandler(.performDefaultHandling, nil)
            return
        }
        capturedLeaf = PinnedTrust.leaf(of: trust)
        completionHandler(.useCredential, URLCredential(trust: trust))
    }

    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        willPerformHTTPRedirection response: HTTPURLResponse,
        newRequest request: URLRequest,
        completionHandler: @escaping (URLRequest?) -> Void
    ) {
        completionHandler(nil)
    }
}
