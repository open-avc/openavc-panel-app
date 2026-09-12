import Foundation

/// Remembers the last server so a panel comes back to its room on launch.
///
/// The Android counterpart is `AppPreferences.kt`; the keys are deliberately
/// the same words so the two are legible side by side.
struct ServerStore {
    private let defaults: UserDefaults

    private enum Key {
        static let name = "server_name"
        static let instanceId = "server_instance_id"
        static let host = "server_host"
        static let port = "server_port"
        static let version = "server_version"
        static let panelUrl = "server_panel_url"
        static let scheme = "server_scheme"
    }

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func save(_ server: ServerInfo) {
        defaults.set(server.name, forKey: Key.name)
        defaults.set(server.instanceId, forKey: Key.instanceId)
        defaults.set(server.host, forKey: Key.host)
        defaults.set(server.port, forKey: Key.port)
        defaults.set(server.version, forKey: Key.version)
        defaults.set(server.panelUrl, forKey: Key.panelUrl)
        defaults.set(server.scheme, forKey: Key.scheme)
    }

    func lastServer() -> ServerInfo? {
        guard let host = defaults.string(forKey: Key.host) else { return nil }
        let port = defaults.integer(forKey: Key.port)
        guard port != 0 else { return nil }
        let scheme = defaults.string(forKey: Key.scheme) ?? "http"
        return ServerInfo(
            name: defaults.string(forKey: Key.name) ?? host,
            instanceId: defaults.string(forKey: Key.instanceId) ?? "",
            host: host,
            port: port,
            version: defaults.string(forKey: Key.version) ?? "",
            panelUrl: defaults.string(forKey: Key.panelUrl) ?? "\(scheme)://\(host):\(port)/panel",
            scheme: scheme
        )
    }

    func clear() {
        for key in [Key.name, Key.instanceId, Key.host, Key.port,
                    Key.version, Key.panelUrl, Key.scheme] {
            defaults.removeObject(forKey: key)
        }
    }
}
