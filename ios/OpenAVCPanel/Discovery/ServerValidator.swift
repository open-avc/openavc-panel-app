import Foundation
import Security

/// One-shot `/api/status` probe against a candidate server.
///
/// Returns a populated `ServerInfo`, or nil if the server is unreachable or the
/// response does not parse. Mirrors Android's `ServerValidator.kt`.
///
/// HTTPS mode: fetches and pins the server's CA (auto-generate) or leaf
/// (provided) on first contact through `CertFetcher`, then reads `/api/status`
/// under that pin. Once the server returns its `instance_id` we re-pin under
/// that key, so trust survives a DHCP address change.
enum ServerValidator {

    private static let timeout: TimeInterval = 3

    static func url(host: String, port: Int, scheme: String, path: String) -> URL? {
        URL(string: "\(scheme)://\(host):\(port)\(path)")
    }

    /// Decides whether a failed plain-http probe was answered by the server's
    /// HTTP-to-HTTPS redirect listener.
    ///
    /// Returns the https port to re-validate against, or nil when this is not a
    /// TLS handoff. **Only the Location's scheme and port are honoured — the
    /// caller keeps the host it just probed.** That is deliberate: the probed
    /// address is what answered, and its bare-IP handshake serves the
    /// self-signed chain that `/api/certificate` pinning can match. A server
    /// with a cloud-issued certificate puts its public DNS name in the Location
    /// instead, which may not resolve without internet and presents a CA-signed
    /// chain no pinned certificate will ever match.
    static func tlsHandoffPort(code: Int, location: String?) -> Int? {
        guard (300..<400).contains(code) else { return nil }
        guard let location, !location.trimmingCharacters(in: .whitespaces).isEmpty else { return nil }
        guard let target = URL(string: location.trimmingCharacters(in: .whitespaces)) else { return nil }
        guard target.scheme?.lowercased() == "https" else { return nil }
        guard let host = target.host, !host.isEmpty else { return nil }
        return target.port ?? 443
    }

    static func validate(
        host: String,
        port: Int,
        scheme: String = "http",
        trustStore: CertTrustStore = CertTrustStore()
    ) async -> ServerInfo? {
        guard let statusUrl = url(host: host, port: port, scheme: scheme, path: "/api/status") else {
            return nil
        }

        var pinned: SecCertificate?
        if scheme == "https" {
            pinned = await CertFetcher(trustStore: trustStore)
                .fetchAndPin(host: host, port: port, instanceId: nil)
            guard pinned != nil else { return nil }
        }

        let delegate = PinnedSessionDelegate(pinned: pinned)
        let session = makeSession(delegate: delegate)
        defer { session.finishTasksAndInvalidate() }

        var request = URLRequest(url: statusUrl)
        request.httpMethod = "GET"
        request.timeoutInterval = timeout
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        let data: Data
        let http: HTTPURLResponse
        do {
            let (body, response) = try await session.data(for: request)
            guard let asHttp = response as? HTTPURLResponse else { return nil }
            data = body
            http = asHttp
        } catch {
            return nil
        }

        guard (200..<300).contains(http.statusCode) else {
            // The delegate refuses redirects, so a 3xx surfaces here intact.
            if scheme == "http",
               let handoff = tlsHandoffPort(
                   code: http.statusCode,
                   location: http.value(forHTTPHeaderField: "Location")
               ) {
                return await validate(
                    host: host, port: handoff, scheme: "https", trustStore: trustStore
                )
            }
            return nil
        }

        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return nil
        }

        let name = (json["project_name"] as? String).flatMap { $0.isEmpty ? nil : $0 }
            ?? (json["name"] as? String).flatMap { $0.isEmpty ? nil : $0 }
            ?? host
        let version = json["version"] as? String ?? ""
        let instanceId = json["instance_id"] as? String ?? ""

        // Re-pin under instance_id so the pin survives DHCP-driven IP changes.
        if let pinned, !instanceId.isEmpty {
            trustStore.pin(
                instanceId: instanceId,
                hostPort: CertTrustStore.hostPortKey(host: host, port: port),
                pem: CertTrustStore.pemEncode(pinned)
            )
        }

        return ServerInfo(
            name: name,
            instanceId: instanceId,
            host: host,
            port: port,
            version: version,
            panelUrl: "\(scheme)://\(host):\(port)/panel",
            scheme: scheme
        )
    }

    /// Cheap liveness check for a server we already know, used on launch to
    /// decide whether discovery can be skipped.
    static func ping(
        host: String,
        port: Int,
        scheme: String = "http",
        instanceId: String? = nil,
        trustStore: CertTrustStore = CertTrustStore()
    ) async -> Bool {
        guard let healthUrl = url(host: host, port: port, scheme: scheme, path: "/api/health") else {
            return false
        }

        var pinned: SecCertificate?
        if scheme == "https" {
            let hostPort = CertTrustStore.hostPortKey(host: host, port: port)
            if let stored = trustStore.lookup(instanceId: instanceId, hostPort: hostPort) {
                pinned = stored
            } else {
                pinned = await CertFetcher(trustStore: trustStore)
                    .fetchAndPin(host: host, port: port, instanceId: instanceId)
            }
            guard pinned != nil else { return false }
        }

        let session = makeSession(delegate: PinnedSessionDelegate(pinned: pinned))
        defer { session.finishTasksAndInvalidate() }

        var request = URLRequest(url: healthUrl)
        request.httpMethod = "GET"
        request.timeoutInterval = timeout

        guard let (_, response) = try? await session.data(for: request),
              let http = response as? HTTPURLResponse
        else { return false }
        return (200..<300).contains(http.statusCode)
    }

    private static func makeSession(delegate: URLSessionDelegate) -> URLSession {
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = timeout
        config.timeoutIntervalForResource = timeout
        return URLSession(configuration: config, delegate: delegate, delegateQueue: nil)
    }
}
