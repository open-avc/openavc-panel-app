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

    // The admin path behind the corner triple-tap: PIN gate, menu, and the
    // two things the menu opens. Same flow as Android's MainActivity.
    @State private var showPinPrompt = false
    @State private var pinEntry = ""
    @State private var pinRefused = false
    @State private var showAdminSheet = false
    @State private var showPanelSettings = false
    @State private var fingerprintMessage: String?
    @State private var lockWasOnAtSettingsOpen = false

    private let store = ServerStore()
    private let kioskPrefs = KioskPreferences()

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
            PanelView(
                server: server,
                onNavigationFailure: { message in panelError = message },
                onAdminHotspot: onAdminHotspot
            )
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
        .onAppear(perform: applyLockState)
        .alert("Enter admin PIN", isPresented: $showPinPrompt) {
            SecureField("PIN", text: $pinEntry)
                .keyboardType(.numberPad)
            Button("Unlock") {
                let accepted = kioskPrefs.checkPin(pinEntry)
                pinEntry = ""
                if accepted {
                    showAdminSheet = true
                } else {
                    pinRefused = true
                }
            }
            Button("Cancel", role: .cancel) { pinEntry = "" }
        }
        .alert("PIN doesn't match.", isPresented: $pinRefused) {
            Button("Try again") { showPinPrompt = true }
            Button("Cancel", role: .cancel) {}
        }
        .sheet(isPresented: $showAdminSheet) {
            AdminSheet(
                onChangeServer: {
                    panelError = nil
                    phase = .discovery
                },
                onPanelSettings: {
                    lockWasOnAtSettingsOpen = kioskPrefs.kioskEnabled
                    showPanelSettings = true
                },
                onViewFingerprint: { showFingerprint(server) }
            )
            .presentationDetents([.medium, .large])
            .opaqueSheetBackground()
        }
        .sheet(isPresented: $showPanelSettings, onDismiss: applyLockState) {
            KioskSetupView()
        }
        .alert("Server certificate fingerprint", isPresented: .constant(fingerprintMessage != nil)) {
            Button("Close") { fingerprintMessage = nil }
        } message: {
            Text(fingerprintMessage ?? "")
        }
    }

    /// The PIN only stands between the room and the admin menu once the panel
    /// is set to lock, as on Android: before that, the menu is how an
    /// integrator gets to the PIN screen in the first place.
    private func onAdminHotspot() {
        if kioskPrefs.hasPin() && kioskPrefs.kioskEnabled {
            pinEntry = ""
            showPinPrompt = true
        } else {
            showAdminSheet = true
        }
    }

    /// Runs when the panel appears and whenever Panel settings closes, the way
    /// Android applies its lock state on resume. The request only takes on an
    /// iPad an MDM has allowed to lock itself; anywhere else it fails quietly
    /// and Guided Access is started by hand. A session someone started by hand
    /// is never ended from here: only a switch that was just turned off is.
    private func applyLockState() {
        let wantLocked = kioskPrefs.kioskEnabled
        let active = GuidedAccessHelper.state == .active
        if wantLocked && !active {
            Task { _ = await GuidedAccessHelper.requestSession(enabled: true) }
        } else if !wantLocked && active && lockWasOnAtSettingsOpen {
            Task { _ = await GuidedAccessHelper.requestSession(enabled: false) }
        }
        lockWasOnAtSettingsOpen = false
    }

    private func showFingerprint(_ server: ServerInfo) {
        let pinned = CertTrustStore().lookup(
            instanceId: server.instanceId.isEmpty ? nil : server.instanceId,
            hostPort: CertTrustStore.hostPortKey(host: server.host, port: server.port)
        )
        if let pinned {
            fingerprintMessage = "This SHA-256 fingerprint should match the value shown in the Programmer (Settings > Security).\n\nSHA-256:\n"
                + CertTrustStore.fingerprint(pinned)
        } else {
            fingerprintMessage = "No certificate pinned yet. The panel hasn't connected over HTTPS."
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

private extension View {
    /// A medium-height sheet on iPadOS 26 is translucent glass, and the menu
    /// is unreadable over a dark panel. The modifier is iOS 16.4; below that
    /// the sheet is opaque anyway.
    @ViewBuilder
    func opaqueSheetBackground() -> some View {
        if #available(iOS 16.4, *) {
            self.presentationBackground(Color(.systemGroupedBackground))
        } else {
            self
        }
    }
}
