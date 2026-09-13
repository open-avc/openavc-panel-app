import SwiftUI

/// The admin menu behind the corner triple-tap, the way in to changing the
/// server and to the dedicated-panel settings. Same entries as Android's
/// `sheet_admin` minus "Close app": an iOS app cannot quit itself.
struct AdminSheet: View {
    @Environment(\.dismiss) private var dismiss

    let onChangeServer: () -> Void
    let onPanelSettings: () -> Void
    let onViewFingerprint: () -> Void

    var body: some View {
        NavigationStack {
            List {
                Button {
                    dismiss()
                    onChangeServer()
                } label: {
                    Label("Change server", systemImage: "arrow.triangle.2.circlepath")
                }
                Button {
                    dismiss()
                    onPanelSettings()
                } label: {
                    Label("Panel settings", systemImage: "lock.ipad")
                }
                Button {
                    dismiss()
                    onViewFingerprint()
                } label: {
                    Label("View server fingerprint", systemImage: "checkmark.seal")
                }
            }
            .navigationTitle("Admin")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close menu") { dismiss() }
                }
            }
        }
        .preferredColorScheme(.dark)
    }
}
