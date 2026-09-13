import SwiftUI

/// First-run screen: pick a server by mDNS, QR, or typed address.
///
/// Same three routes as Android, in the same order of preference. Automatic
/// discovery is the one that feels like magic when it works; the other two are
/// what make the app usable on the segmented networks it will actually meet.
struct ServerDiscoveryView: View {
    let onConnect: (ServerInfo) -> Void

    @StateObject private var discovery = BonjourDiscovery()
    @State private var showManualEntry = false
    @State private var showScanner = false
    @State private var scanFailure: String?
    @State private var isConnecting = false

    var body: some View {
        NavigationStack {
            ZStack {
                Color(.systemGroupedBackground).ignoresSafeArea()
                content
            }
            .navigationTitle("Choose a server")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Button {
                            showScanner = true
                        } label: {
                            Label("Scan pairing code", systemImage: "qrcode.viewfinder")
                        }
                        Button {
                            showManualEntry = true
                        } label: {
                            Label("Enter address", systemImage: "keyboard")
                        }
                    } label: {
                        Image(systemName: "plus")
                    }
                }
            }
            .sheet(isPresented: $showManualEntry) {
                ManualEntryView(onConnect: onConnect)
            }
            .fullScreenCover(isPresented: $showScanner) {
                scannerSheet
            }
            .alert("Couldn't use that code", isPresented: .constant(scanFailure != nil)) {
                Button("OK") { scanFailure = nil }
            } message: {
                Text(scanFailure ?? "")
            }
        }
        .onAppear { discovery.start() }
        .onDisappear { discovery.stop() }
    }

    @ViewBuilder
    private var content: some View {
        if discovery.servers.isEmpty {
            emptyState
        } else {
            List {
                Section {
                    ForEach(discovery.servers) { server in
                        Button {
                            onConnect(server)
                        } label: {
                            ServerRow(server: server)
                        }
                        .buttonStyle(.plain)
                    }
                } footer: {
                    VStack(spacing: 0) {
                        serverNote
                        adminHint
                    }
                    .padding(.top, 12)
                }
            }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 18) {
            if discovery.localNetworkLikelyDenied {
                Image(systemName: "exclamationmark.triangle")
                    .font(.system(size: 40))
                    .foregroundStyle(.orange)
                Text("No servers found")
                    .font(.title3.weight(.semibold))
                // iOS reports a local-network denial by silently returning
                // nothing, so this is a hint rather than a diagnosis.
                Text("If you didn't allow local network access, OpenAVC can't see servers on your network. Turn it on in Settings > Privacy & Security > Local Network, or enter the server's address directly.")
                    .font(.callout)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            } else {
                ProgressView()
                Text("Looking for servers…")
                    .font(.title3.weight(.semibold))
                Text("Make sure this tablet is on the same network as your OpenAVC server.")
                    .font(.callout)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            serverNote
            adminHint
            HStack(spacing: 12) {
                Button {
                    showScanner = true
                } label: {
                    Label("Scan code", systemImage: "qrcode.viewfinder")
                }
                .buttonStyle(.borderedProminent)
                Button {
                    showManualEntry = true
                } label: {
                    Label("Enter address", systemImage: "keyboard")
                }
                .buttonStyle(.bordered)
            }
            .padding(.top, 8)
        }
        .padding(40)
    }

    /// The first thing a person who installed the app without a server needs
    /// to read: this is the panel, not the system.
    private var serverNote: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "server.rack")
                .font(.title3)
                .foregroundStyle(Color.accentColor)
            Text("OpenAVC Panel is the touch panel for an OpenAVC system. It needs the OpenAVC server running on your network. Install the server on Windows, macOS, Linux, a Raspberry Pi or Docker from openavc.com, then come back here to connect.")
                .font(.callout)
                .foregroundStyle(.primary)
                .multilineTextAlignment(.leading)
        }
        .padding(14)
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12))
        .padding(.top, 12)
    }

    /// Told here, on the first screen, because once the panel is up there is
    /// nothing on it that says how to get back.
    private var adminHint: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "hand.tap")
                .font(.title3)
                .foregroundStyle(Color.accentColor)
            Text("Once the panel is showing, tap the top-left corner of the screen three times to open the admin menu: change server, Panel settings, and the dedicated-panel lock.")
                .font(.callout)
                .foregroundStyle(.primary)
                .multilineTextAlignment(.leading)
        }
        .padding(14)
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12))
        .padding(.top, 12)
    }

    private var scannerSheet: some View {
        ZStack(alignment: .topLeading) {
            QRScannerView(
                onScan: { value in
                    showScanner = false
                    Task { await handleScan(value) }
                },
                onCancel: { showScanner = false }
            )
            .ignoresSafeArea()

            Button {
                showScanner = false
            } label: {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 32))
                    .symbolRenderingMode(.palette)
                    .foregroundStyle(.white, .black.opacity(0.4))
            }
            .padding(20)

            VStack {
                Spacer()
                Text("Point the camera at the pairing code in the Programmer")
                    .font(.callout)
                    .foregroundStyle(.white)
                    .padding()
                    .background(.black.opacity(0.6), in: Capsule())
                    .padding(.bottom, 50)
                    .frame(maxWidth: .infinity)
            }

            if isConnecting {
                Color.black.opacity(0.6).ignoresSafeArea()
                ProgressView("Connecting…").tint(.white).foregroundStyle(.white)
            }
        }
    }

    private func handleScan(_ value: String) async {
        guard let parsed = ServerInfo.fromPanelUrl(value) else {
            scanFailure = "That doesn't look like an OpenAVC pairing code."
            return
        }
        isConnecting = true
        defer { isConnecting = false }

        // Re-validate rather than trusting the code: the QR is a printed label
        // that may be older than the server's current configuration, and
        // validation is what upgrades a printed http:// URL to a TLS server.
        if let server = await ServerValidator.validate(
            host: parsed.host, port: parsed.port, scheme: parsed.scheme
        ) {
            onConnect(server)
        } else {
            scanFailure = "Found \(parsed.host):\(parsed.port) in the code, but no OpenAVC server answered there."
        }
    }
}

private struct ServerRow: View {
    let server: ServerInfo

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: server.scheme == "https" ? "lock.display" : "display")
                .font(.system(size: 26))
                .foregroundStyle(Color.accentColor)
                .frame(width: 34)
            VStack(alignment: .leading, spacing: 3) {
                Text(server.name)
                    .font(.headline)
                    .foregroundStyle(.primary)
                Text("\(server.host):\(String(server.port))")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                if !server.version.isEmpty {
                    Text("OpenAVC \(server.version)")
                        .font(.caption)
                        .foregroundStyle(.tertiary)
                }
            }
            Spacer()
            Image(systemName: "chevron.right")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(.tertiary)
        }
        .padding(.vertical, 6)
    }
}
