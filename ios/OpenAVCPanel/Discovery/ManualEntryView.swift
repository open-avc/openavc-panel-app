import SwiftUI

/// Type in an address when discovery cannot see the server.
///
/// This is the path that has to work on a segmented network, where mDNS does
/// not cross the VLAN boundary — which in commercial AV is common, not exotic.
struct ManualEntryView: View {
    @Environment(\.dismiss) private var dismiss
    let onConnect: (ServerInfo) -> Void

    @State private var host = ""
    @State private var port = "8080"
    @State private var useHttps = false
    @State private var isChecking = false
    @State private var failure: String?
    @FocusState private var hostFocused: Bool

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("192.168.1.50", text: $host)
                        .keyboardType(.URL)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .focused($hostFocused)
                    TextField("Port", text: $port)
                        .keyboardType(.numberPad)
                    Toggle("Use HTTPS", isOn: $useHttps)
                        // Single-parameter onChange: the two-parameter form is
                        // iOS 17+, and this app deliberately still runs on 16 so
                        // an older iPad can be repurposed as a panel.
                        .onChange(of: useHttps) { secure in
                            // Follow the server's own defaults as the toggle
                            // moves, but never overwrite a port typed by hand.
                            if port == "8080" && secure { port = "8443" }
                            else if port == "8443" && !secure { port = "8080" }
                        }
                } header: {
                    Text("Server address")
                } footer: {
                    Text("The address of the computer running OpenAVC. You'll find it on the server's setup screen, or in the Programmer under Panel Access.")
                }

                if let failure {
                    Section {
                        Text(failure).foregroundStyle(.red)
                    }
                }
            }
            .navigationTitle("Enter address")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Connect") { Task { await connect() } }
                        .disabled(host.isEmpty || isChecking)
                }
            }
            .overlay {
                if isChecking { ProgressView("Checking…").padding().background(.regularMaterial) }
            }
            .onAppear { hostFocused = true }
        }
    }

    private func connect() async {
        guard let portNumber = Int(port), portNumber > 0, portNumber <= 65535 else {
            failure = "That port number isn't valid."
            return
        }
        isChecking = true
        failure = nil
        defer { isChecking = false }

        let trimmed = host.trimmingCharacters(in: .whitespaces)
        let server = await ServerValidator.validate(
            host: trimmed, port: portNumber, scheme: useHttps ? "https" : "http"
        )
        if let server {
            onConnect(server)
            dismiss()
        } else {
            failure = "No OpenAVC server answered at \(trimmed):\(portNumber). Check the address and that the server is running."
        }
    }
}
