import SwiftUI
import UIKit

/// Admin-only screen reached from the admin sheet: the dedicated-panel
/// walkthrough for iOS.
///
/// The Android counterpart is `KioskSetupActivity`, and the shape is the same:
/// the current state at the top, the lock switch, the admin PIN, then the
/// one-time setup the integrator still has to do. What differs is the setup
/// itself. Android has one command; iOS has Guided Access by hand, or an MDM
/// profile that lets the app lock itself, and the screen says which one the
/// reader is looking at.
struct KioskSetupView: View {
    @Environment(\.dismiss) private var dismiss

    private let prefs = KioskPreferences()

    @State private var lockOnLaunch = false
    @State private var hasPin = false
    @State private var sessionActive = false
    @State private var showPinSheet = false
    @State private var hint: String?
    @State private var lockNowResult: String?
    @State private var isRequestingLock = false

    private static let docsUrl = URL(string: "https://docs.openavc.com/panel-app-dedicated-ios")!

    var body: some View {
        NavigationStack {
            Form {
                stateSection
                lockSection
                pinSection
                guidedAccessSection
                managedSection
            }
            .navigationTitle("Dedicated panel")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .sheet(isPresented: $showPinSheet, onDismiss: refresh) {
                PinSetupSheet(prefs: prefs)
            }
            .alert("Dedicated panel", isPresented: .constant(hint != nil)) {
                Button("OK") { hint = nil }
            } message: {
                Text(hint ?? "")
            }
            .onAppear(perform: refresh)
            .onReceive(NotificationCenter.default.publisher(
                for: UIAccessibility.guidedAccessStatusDidChangeNotification
            )) { _ in refresh() }
        }
        .preferredColorScheme(.dark)
    }

    // MARK: State

    private var stateSection: some View {
        Section {
            VStack(alignment: .leading, spacing: 6) {
                Text(stateTitle).font(.title3.weight(.semibold))
                Text(stateDetail).font(.callout).foregroundStyle(.secondary)
            }
            .padding(.vertical, 4)
        }
    }

    /// The heading follows the switch as well as the live session, the same
    /// rule as Android's `render()`: with the switch on and no session yet, the
    /// panel is armed rather than basic.
    private var stateTitle: String {
        if sessionActive { return "Locked" }
        if lockOnLaunch { return "Lock on launch is on" }
        return "Basic mode"
    }

    private var stateDetail: String {
        if sessionActive {
            return "The iPad is locked to this panel. Triple-click the top button and enter the Guided Access passcode to leave it."
        }
        if lockOnLaunch {
            return "The panel asks iOS to lock the iPad each time the panel opens. That takes effect on an iPad with an MDM lock profile. On any other iPad, start Guided Access by hand: triple-click the top button."
        }
        return "The panel runs full screen and keeps the screen awake. The Home bar can still swipe out to the Home Screen, and after a restart someone has to open the app again. To lock the iPad to the panel, use Guided Access or an MDM lock profile."
    }

    // MARK: Lock switch

    private var lockSection: some View {
        Section {
            Toggle(isOn: $lockOnLaunch) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Lock on launch")
                    Text("Ask iOS to lock the iPad to this panel whenever the panel opens. Takes effect the next time you return to the panel. Needs an MDM lock profile.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .onChange(of: lockOnLaunch) { enabled in
                onLockToggle(enabled)
            }
        }
    }

    private func onLockToggle(_ enabled: Bool) {
        guard enabled != prefs.kioskEnabled else { return }
        if enabled && !prefs.hasPin() {
            hint = "Set an admin PIN first."
            lockOnLaunch = false
            return
        }
        prefs.kioskEnabled = enabled
        hint = enabled
            ? "Lock engages when you return to the panel."
            : "Lock releases when you return to the panel."
    }

    // MARK: Admin PIN

    private var pinSection: some View {
        Section("Admin PIN") {
            Text(hasPin
                 ? "A 4+ digit PIN protects this screen and the admin menu."
                 : "No PIN set. Required before turning on Lock on launch.")
                .font(.callout)
                .foregroundStyle(.secondary)
            Button(hasPin ? "Change PIN" : "Set PIN") { showPinSheet = true }
        }
    }

    // MARK: Guided Access

    private var guidedAccessSection: some View {
        Section {
            step(1, "In Settings, tap Accessibility, then Guided Access, and turn it on.")
            step(2, "Tap Passcode Settings and set a passcode. Write it down.")
            step(3, "Come back to this panel and triple-click the top button.")
            step(4, "Tap Start.")
            Text("To leave: triple-click the top button, enter the passcode, then tap End. A restart ends the session, so someone has to open the app and start Guided Access again.")
                .font(.footnote)
                .foregroundStyle(.secondary)
            Button {
                Task { await lockNow() }
            } label: {
                HStack {
                    Text("Ask iOS to lock now")
                    if isRequestingLock { Spacer(); ProgressView() }
                }
            }
            .disabled(isRequestingLock || sessionActive)
            if let lockNowResult {
                Text(lockNowResult).font(.footnote).foregroundStyle(.secondary)
            }
        } header: {
            Text("Guided Access (free)")
        } footer: {
            Text("Guided Access is built into iPadOS. Anyone with the passcode can end it, and it does not come back on its own after a restart.")
        }
    }

    private func lockNow() async {
        isRequestingLock = true
        defer { isRequestingLock = false }
        let locked = await GuidedAccessHelper.requestSession(enabled: true)
        refresh()
        lockNowResult = locked
            ? "Locked."
            : "iOS did not lock the panel. Without an MDM lock profile, triple-click the top button and choose Guided Access instead."
    }

    // MARK: MDM

    private var managedSection: some View {
        Section {
            Text("With an MDM lock profile for com.openavc.panel (Apple calls it Autonomous Single App Mode), the lock engages every time the panel opens, restarts included. Turn on Lock on launch above once the profile is on the iPad.")
                .font(.callout)
                .foregroundStyle(.secondary)
            Link("Open full setup guide on docs.openavc.com", destination: Self.docsUrl)
        } header: {
            Text("Managed iPads (MDM)")
        }
    }

    private func step(_ number: Int, _ text: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 12) {
            Text("\(number)")
                .font(.callout.weight(.semibold))
                .frame(width: 22, height: 22)
                .background(Color.accentColor.opacity(0.25), in: Circle())
            Text(text).font(.callout)
        }
    }

    private func refresh() {
        hasPin = prefs.hasPin()
        lockOnLaunch = prefs.kioskEnabled
        sessionActive = GuidedAccessHelper.state == .active
    }
}

/// Set or change the admin PIN. Mirrors Android's `dialog_pin_entry`: the
/// current PIN is asked for only when one exists.
private struct PinSetupSheet: View {
    @Environment(\.dismiss) private var dismiss
    let prefs: KioskPreferences

    @State private var current = ""
    @State private var proposed = ""
    @State private var confirm = ""
    @State private var failure: String?
    @FocusState private var focused: Field?

    private static let minPinLength = 4

    private enum Field { case current, proposed }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    if prefs.hasPin() {
                        SecureField("Current PIN", text: $current)
                            .keyboardType(.numberPad)
                            .focused($focused, equals: .current)
                    }
                    SecureField("New PIN (min 4 digits)", text: $proposed)
                        .keyboardType(.numberPad)
                        .focused($focused, equals: .proposed)
                    SecureField("Confirm new PIN", text: $confirm)
                        .keyboardType(.numberPad)
                }
                if let failure {
                    Section { Text(failure).foregroundStyle(.red) }
                }
            }
            .navigationTitle(prefs.hasPin() ? "Change admin PIN" : "Set admin PIN")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save", action: save)
                }
            }
            .onAppear { focused = prefs.hasPin() ? .current : .proposed }
        }
    }

    private func save() {
        if prefs.hasPin() && !prefs.checkPin(current) {
            failure = "PIN doesn't match."
            return
        }
        if proposed.count < Self.minPinLength {
            failure = "Use at least 4 digits."
            return
        }
        if proposed != confirm {
            failure = "New PIN and confirmation don't match."
            return
        }
        prefs.setPin(proposed)
        dismiss()
    }
}
