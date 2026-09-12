import Foundation
import Security

/// First-contact certificate retrieval for OpenAVC HTTPS servers.
///
/// Auto-generate TLS:
///   `GET https://host:port/api/certificate` returns the server's CA in PEM.
///   We pin that, so the leaf can rotate without a re-pair.
///
/// Provided TLS (the integrator uploaded their own certificate):
///   `/api/certificate` answers 404. We open a trust-all handshake against
///   `/api/health`, capture the leaf the server presents, and pin that instead.
///   A later leaf rotation needs a re-pair, which is acceptable for BYO-cert.
///
/// Both are trust-on-first-use. The mitigation is the same as Android's: the
/// admin sheet shows the pinned fingerprint to compare against the IDE.
struct CertFetcher {
    private let trustStore: CertTrustStore
    private static let timeout: TimeInterval = 3

    init(trustStore: CertTrustStore = CertTrustStore()) {
        self.trustStore = trustStore
    }

    func fetchAndPin(host: String, port: Int, instanceId: String? = nil) async -> SecCertificate? {
        let hostPort = CertTrustStore.hostPortKey(host: host, port: port)

        if let auto = await fetchAutoCa(host: host, port: port) {
            trustStore.pin(instanceId: instanceId, hostPort: hostPort, pem: auto.pem)
            return auto.certificate
        }
        if let leaf = await fetchProvidedLeaf(host: host, port: port) {
            trustStore.pin(
                instanceId: instanceId,
                hostPort: hostPort,
                pem: CertTrustStore.pemEncode(leaf)
            )
            return leaf
        }
        return nil
    }

    private struct FetchedPem {
        let pem: String
        let certificate: SecCertificate
    }

    private func fetchAutoCa(host: String, port: Int) async -> FetchedPem? {
        guard let url = URL(string: "https://\(host):\(port)/api/certificate") else { return nil }
        let delegate = TrustAllSessionDelegate()
        let session = Self.session(delegate: delegate)
        defer { session.finishTasksAndInvalidate() }

        do {
            let (data, response) = try await session.data(for: Self.request(url))
            guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
                return nil
            }
            let pem = String(decoding: data, as: UTF8.self)
            guard let certificate = CertTrustStore.parsePem(pem) else { return nil }
            return FetchedPem(pem: pem, certificate: certificate)
        } catch {
            return nil
        }
    }

    private func fetchProvidedLeaf(host: String, port: Int) async -> SecCertificate? {
        guard let url = URL(string: "https://\(host):\(port)/api/health") else { return nil }
        let delegate = TrustAllSessionDelegate()
        let session = Self.session(delegate: delegate)
        defer { session.finishTasksAndInvalidate() }

        // The response body is irrelevant; the handshake is the point. The
        // delegate captures the leaf on its way past.
        _ = try? await session.data(for: Self.request(url))
        return delegate.capturedLeaf
    }

    private static func request(_ url: URL) -> URLRequest {
        var request = URLRequest(url: url)
        request.timeoutInterval = timeout
        request.httpMethod = "GET"
        return request
    }

    private static func session(delegate: URLSessionDelegate) -> URLSession {
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = timeout
        config.timeoutIntervalForResource = timeout
        return URLSession(configuration: config, delegate: delegate, delegateQueue: nil)
    }
}
