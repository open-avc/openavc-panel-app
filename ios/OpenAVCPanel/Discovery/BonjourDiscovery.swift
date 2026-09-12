import Foundation
import Network

/// Finds OpenAVC servers advertising `_openavc._tcp` on the local network.
///
/// The server side is `openavc/discovery/mdns_advertiser.py`, a stdlib-only
/// responder. Its TXT record carries `name`, `id`, `version`, `path`, and
/// `scheme` — and `scheme` **only when TLS is enabled**, so its absence means
/// plain HTTP. Android's `MDNSDiscovery.toServerInfo` reads exactly these and
/// defaults the same way; keep the two in step.
@MainActor
final class BonjourDiscovery: ObservableObject {

    @Published private(set) var servers: [ServerInfo] = []
    @Published private(set) var isSearching = false
    /// Set when iOS denies local network access, which is silent otherwise:
    /// the browser simply never reports anything.
    @Published private(set) var localNetworkLikelyDenied = false

    private var browser: NWBrowser?
    private var resolvers: [String: NWConnection] = [:]
    /// Bonjour service key -> the server it resolved to. Held here rather than
    /// on ServerInfo: which service a server arrived from is this class's
    /// bookkeeping, not part of the server contract the two platforms share.
    private var byServiceKey: [String: ServerInfo] = [:]
    private var sawAnyResult = false
    private var denialCheck: Task<Void, Never>?

    static let serviceType = "_openavc._tcp"

    func start() {
        guard browser == nil else { return }
        servers = []
        byServiceKey = [:]
        sawAnyResult = false
        localNetworkLikelyDenied = false
        isSearching = true

        let parameters = NWParameters()
        parameters.includePeerToPeer = true
        let descriptor = NWBrowser.Descriptor.bonjourWithTXTRecord(
            type: Self.serviceType, domain: nil
        )
        let browser = NWBrowser(for: descriptor, using: parameters)

        browser.browseResultsChangedHandler = { [weak self] results, _ in
            Task { @MainActor in self?.handle(results) }
        }
        browser.stateUpdateHandler = { [weak self] state in
            Task { @MainActor in
                switch state {
                case .failed, .cancelled:
                    self?.isSearching = false
                default:
                    break
                }
            }
        }

        self.browser = browser
        browser.start(queue: .main)

        // iOS asks for local network permission the first time a browse runs and
        // reports a denial by simply never returning anything. Surfacing it as a
        // hint after a quiet spell is the only signal available to us.
        denialCheck = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 6_000_000_000)
            guard let self, !Task.isCancelled else { return }
            if !self.sawAnyResult {
                self.localNetworkLikelyDenied = true
            }
        }
    }

    func stop() {
        denialCheck?.cancel()
        denialCheck = nil
        browser?.cancel()
        browser = nil
        for connection in resolvers.values { connection.cancel() }
        resolvers.removeAll()
        byServiceKey.removeAll()
        isSearching = false
    }

    private func handle(_ results: Set<NWBrowser.Result>) {
        if !results.isEmpty { sawAnyResult = true }

        var seen = Set<String>()
        for result in results {
            guard case let .service(name, type, domain, _) = result.endpoint else { continue }
            let key = "\(name).\(type)\(domain)"
            seen.insert(key)

            var txt: [String: String] = [:]
            if case let .bonjour(record) = result.metadata {
                txt = record.dictionary
            }
            resolve(endpoint: result.endpoint, key: key, serviceName: name, txt: txt)
        }

        // Drop servers whose service has gone away.
        for key in byServiceKey.keys where !seen.contains(key) {
            byServiceKey.removeValue(forKey: key)
        }
        publish()
    }

    /// Bonjour gives us a service name; the panel URL needs a routable address.
    /// One short connection resolves the endpoint, then we drop it.
    private func resolve(
        endpoint: NWEndpoint,
        key: String,
        serviceName: String,
        txt: [String: String]
    ) {
        guard resolvers[key] == nil else { return }

        let connection = NWConnection(to: endpoint, using: .tcp)
        resolvers[key] = connection

        connection.stateUpdateHandler = { [weak self] state in
            guard case .ready = state else {
                if case .failed = state {
                    Task { @MainActor in self?.finishResolve(key: key) }
                }
                return
            }
            guard let inner = connection.currentPath?.remoteEndpoint else {
                Task { @MainActor in self?.finishResolve(key: key) }
                return
            }
            Task { @MainActor in
                self?.record(endpoint: inner, key: key, serviceName: serviceName, txt: txt)
                self?.finishResolve(key: key)
            }
        }
        connection.start(queue: .main)
    }

    private func finishResolve(key: String) {
        resolvers[key]?.cancel()
        resolvers[key] = nil
    }

    private func record(
        endpoint: NWEndpoint,
        key: String,
        serviceName: String,
        txt: [String: String]
    ) {
        guard case let .hostPort(host, port) = endpoint else { return }

        // Prefer IPv4. The address ends up in a WKWebView URL, and link-local
        // IPv6 carries a zone suffix that does not belong in one.
        let address: String
        switch host {
        case let .ipv4(v4):
            address = "\(v4)".components(separatedBy: "%").first ?? "\(v4)"
        case let .ipv6(v6):
            if let mapped = v6.asIPv4 {
                address = "\(mapped)".components(separatedBy: "%").first ?? "\(mapped)"
            } else {
                address = "[\("\(v6)".components(separatedBy: "%").first ?? "\(v6)")]"
            }
        case let .name(name, _):
            address = name
        @unknown default:
            return
        }

        let scheme = (txt["scheme"]?.lowercased()).flatMap { $0.isEmpty ? nil : $0 } ?? "http"
        let path = (txt["path"]).flatMap { $0.isEmpty ? nil : $0 } ?? "/panel"
        let name = (txt["name"]).flatMap { $0.isEmpty ? nil : $0 } ?? serviceName
        let portNumber = Int(port.rawValue)

        byServiceKey[key] = ServerInfo(
            name: name,
            instanceId: txt["id"] ?? "",
            host: address,
            port: portNumber,
            version: txt["version"] ?? "",
            panelUrl: "\(scheme)://\(address):\(portNumber)\(path)",
            scheme: scheme
        )
        publish()
    }

    private func publish() {
        servers = byServiceKey.values
            .sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
    }
}
