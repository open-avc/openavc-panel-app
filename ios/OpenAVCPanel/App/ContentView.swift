import SwiftUI

/// Router: the panel if we have a server, otherwise the discovery screen.
///
/// On launch we health-check the remembered server and go straight to the panel
/// if it answers. A wall-mounted panel should come back to its room after a
/// power cut without anyone touching it.
struct ContentView: View {
    @State private var server: ServerInfo?
    @State private var phase: Phase = .checking
    @State private var panelError: String?

    private let store = ServerStore()

    enum Phase {
        case checking
        case discovery
        case panel
    }

    var body: some View {
        Group {
            switch phase {
            case .checking:
                launchCheck
            case .discovery:
                ServerDiscoveryView(onConnect: connect)
            case .panel:
                if let server {
                    panel(server)
                } else {
                    // Unreachable in practice; never trap the user on a blank
                    // screen if it happens.
                    ServerDiscoveryView(onConnect: connect)
                }
            }
        }
        .task { await restoreLastServer() }
    }

    private var launchCheck: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            VStack(spacing: 16) {
                ProgressView().tint(.white)
                Text("Looking for your server…")
                    .foregroundStyle(.white.opacity(0.7))
                    .font(.callout)
            }
        }
    }

    private func panel(_ server: ServerInfo) -> some View {
        ZStack {
            Color.black.ignoresSafeArea()
            PanelView(server: server) { message in
                panelError = message
            }
            .ignoresSafeArea(.all)

            if let panelError {
                ReconnectOverlay(
                    server: server,
                    message: panelError,
                    onRetry: { self.panelError = nil },
                    onChangeServer: {
                        self.panelError = nil
                        self.phase = .discovery
                    }
                )
            }
        }
    }

    private func restoreLastServer() async {
        guard phase == .checking else { return }
        guard let remembered = store.lastServer() else {
            phase = .discovery
            return
        }
        // Straight to the panel, with no launch-time reachability check. This
        // matches MainActivity on Android, and it is the right behaviour for a
        // wall panel: the server is far more often rebooting than gone, and
        // dumping a room's panel to a setup screen over a three-second timeout
        // is the wrong answer with people standing in front of it. If it really
        // is unreachable the WebView says so and the reconnect overlay takes
        // over, which is the same place an outage mid-session lands.
        server = remembered
        phase = .panel
    }

    private func connect(_ found: ServerInfo) {
        store.save(found)
        server = found
        panelError = nil
        phase = .panel
    }
}

/// Shown over the panel when the WebView cannot reach the server.
private struct ReconnectOverlay: View {
    let server: ServerInfo
    let message: String
    let onRetry: () -> Void
    let onChangeServer: () -> Void

    var body: some View {
        ZStack {
            Color.black.opacity(0.85).ignoresSafeArea()
            VStack(spacing: 20) {
                Image(systemName: "wifi.exclamationmark")
                    .font(.system(size: 44))
                    .foregroundStyle(.orange)
                Text("Can't reach \(server.name)")
                    .font(.title2.weight(.semibold))
                    .foregroundStyle(.white)
                Text(message)
                    .font(.footnote)
                    .foregroundStyle(.white.opacity(0.6))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 40)
                HStack(spacing: 12) {
                    Button("Try again", action: onRetry)
                        .buttonStyle(.borderedProminent)
                    Button("Change server", action: onChangeServer)
                        .buttonStyle(.bordered)
                }
                .tint(.white)
            }
            .padding(32)
        }
    }
}
