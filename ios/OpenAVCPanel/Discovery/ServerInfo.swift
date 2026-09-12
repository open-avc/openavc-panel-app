import Foundation

/// One reachable OpenAVC server, however it was found.
///
/// Deliberately mirrors `ServerInfo.kt` on Android, field for field, so the two
/// apps agree on what a server is and a change to the server contract shows up
/// in one shape on both platforms.
struct ServerInfo: Equatable, Codable, Identifiable {
    let name: String
    let instanceId: String
    let host: String
    let port: Int
    let version: String
    let panelUrl: String
    /// Plain HTTP unless mDNS advertised `scheme=https` or a TLS handoff
    /// upgraded us. Matches the Android default.
    var scheme: String = "http"

    var id: String { instanceId.isEmpty ? "\(host):\(port)" : instanceId }

    var statusUrl: String { "\(scheme)://\(host):\(port)/api/status" }
    var healthUrl: String { "\(scheme)://\(host):\(port)/api/health" }

    /// Parses a panel or pair URL, as scanned from a QR code or typed by hand.
    ///
    /// Port defaults follow OpenAVC's own: 8080 for http, 8443 for https. A URL
    /// with no path is treated as pointing at `/panel`.
    static func fromPanelUrl(_ url: String, name: String? = nil) -> ServerInfo? {
        var trimmed = url.trimmingCharacters(in: .whitespacesAndNewlines)
        while trimmed.hasSuffix("/") { trimmed.removeLast() }

        let pattern = "^(https?)://([^:/]+)(?::(\\d+))?(/.*)?$"
        guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]),
              let match = regex.firstMatch(
                  in: trimmed,
                  options: [.anchored],
                  range: NSRange(trimmed.startIndex..., in: trimmed)
              ),
              match.range.length == trimmed.utf16.count
        else { return nil }

        func group(_ i: Int) -> String {
            guard let r = Range(match.range(at: i), in: trimmed) else { return "" }
            return String(trimmed[r])
        }

        let scheme = group(1).lowercased()
        let host = group(2)
        let portText = group(3)
        let port = Int(portText.isEmpty ? (scheme == "https" ? "8443" : "8080") : portText)
        guard let port, port > 0, port <= 65535 else { return nil }
        let path = group(4).isEmpty ? "/panel" : group(4)

        return ServerInfo(
            name: name ?? host,
            instanceId: "",
            host: host,
            port: port,
            version: "",
            panelUrl: "\(scheme)://\(host):\(port)\(path)",
            scheme: scheme
        )
    }
}
