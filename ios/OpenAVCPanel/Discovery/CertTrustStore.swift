import Foundation
import Security

/// Persists pinned certificates for OpenAVC servers.
///
/// Two lookup keys per server, same as Android's `CertTrustStore.kt`:
///   - `instanceId` (preferred, stable across DHCP rotation)
///   - `host:port`  (fallback for first pair, before `/api/status` has answered)
///
/// The stored value is the PEM-encoded certificate. In auto-generate TLS mode
/// that is the server's CA; in provided mode it is the leaf. A certificate is
/// public by definition, so `UserDefaults` is the right home — the Keychain
/// would buy nothing and cost a migration.
struct CertTrustStore {
    private let defaults: UserDefaults

    private static let suiteName = "openavc_cert_trust"
    private static let instancePrefix = "i:"
    private static let hostPortPrefix = "h:"

    init(defaults: UserDefaults? = nil) {
        self.defaults = defaults ?? UserDefaults(suiteName: Self.suiteName) ?? .standard
    }

    static func hostPortKey(host: String, port: Int) -> String {
        "\(host.lowercased()):\(port)"
    }

    private static func instanceKey(_ id: String) -> String { instancePrefix + id }
    private static func hostPortStoreKey(_ hp: String) -> String { hostPortPrefix + hp.lowercased() }

    func pin(instanceId: String?, hostPort: String?, pem: String) {
        if let instanceId, !instanceId.isEmpty {
            defaults.set(pem, forKey: Self.instanceKey(instanceId))
        }
        if let hostPort, !hostPort.isEmpty {
            defaults.set(pem, forKey: Self.hostPortStoreKey(hostPort))
        }
    }

    func lookupPem(instanceId: String?, hostPort: String?) -> String? {
        if let instanceId, !instanceId.isEmpty,
           let pem = defaults.string(forKey: Self.instanceKey(instanceId)) {
            return pem
        }
        if let hostPort, !hostPort.isEmpty,
           let pem = defaults.string(forKey: Self.hostPortStoreKey(hostPort)) {
            return pem
        }
        return nil
    }

    /// Returns the pinned certificate, or nil if nothing is pinned.
    ///
    /// A corrupt entry reads as "no pin" rather than throwing, so a mangled
    /// value sends the user through the re-pair flow instead of crashing.
    func lookup(instanceId: String?, hostPort: String?) -> SecCertificate? {
        guard let pem = lookupPem(instanceId: instanceId, hostPort: hostPort) else { return nil }
        return Self.parsePem(pem)
    }

    func clear(instanceId: String?, hostPort: String? = nil) {
        if let instanceId, !instanceId.isEmpty {
            defaults.removeObject(forKey: Self.instanceKey(instanceId))
        }
        if let hostPort, !hostPort.isEmpty {
            defaults.removeObject(forKey: Self.hostPortStoreKey(hostPort))
        }
    }

    /// Accepts either a PEM block or raw base64 — headers stripped, then decoded.
    static func parsePem(_ pem: String) -> SecCertificate? {
        let body = pem
            .replacingOccurrences(of: "-----BEGIN CERTIFICATE-----", with: "")
            .replacingOccurrences(of: "-----END CERTIFICATE-----", with: "")
            .components(separatedBy: .whitespacesAndNewlines)
            .joined()
        guard let der = Data(base64Encoded: body) else { return nil }
        return SecCertificateCreateWithData(nil, der as CFData)
    }

    static func pemEncode(_ certificate: SecCertificate) -> String {
        let der = SecCertificateCopyData(certificate) as Data
        let base64 = der.base64EncodedString(options: [.lineLength64Characters, .endLineWithLineFeed])
        return "-----BEGIN CERTIFICATE-----\n\(base64)\n-----END CERTIFICATE-----\n"
    }

    /// SHA-256 of the DER bytes, colon-separated — the form the Programmer IDE
    /// shows, so an integrator can compare the two by eye.
    static func fingerprint(_ certificate: SecCertificate) -> String {
        let der = SecCertificateCopyData(certificate) as Data
        return der.sha256Hex()
    }
}
