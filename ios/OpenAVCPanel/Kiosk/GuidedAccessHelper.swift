import UIKit

/// What iOS will and will not let a panel do to lock itself down.
///
/// iOS has no equivalent of Android's Lock Task Mode: an app cannot put itself
/// into a locked state unaided. There are three tiers, and being straight with
/// the integrator about which one they are on matters more than the code here.
///
///  1. Guided Access — free, enabled by hand in Settings, started by a
///     triple-click. Per session, and the person starting it is standing there.
///  2. Programmatic request — this app asks iOS to start a Guided Access
///     session. Works only once tier 1 is enabled; fails cleanly otherwise.
///  3. Autonomous Single App Mode — true unattended kiosk, requires MDM to push
///     a lock profile. Only this tier survives a reboot without a human.
@MainActor
enum GuidedAccessHelper {

    enum State {
        /// A Guided Access session is running right now.
        case active
        /// Not in a session. Whether Guided Access is even enabled in Settings
        /// is not a thing iOS will tell us — only whether a session took.
        case inactive
    }

    static var state: State {
        UIAccessibility.isGuidedAccessEnabled ? .active : .inactive
    }

    /// Asks iOS to start or end a Guided Access session.
    ///
    /// Succeeds when Guided Access is enabled in Settings, or when an MDM has
    /// pushed an Autonomous Single App Mode payload for this app — in which
    /// case it locks with nobody present, which is the dedicated-panel case.
    /// Fails with `false` otherwise, and the caller shows the walkthrough.
    static func requestSession(enabled: Bool) async -> Bool {
        await withCheckedContinuation { continuation in
            UIAccessibility.requestGuidedAccessSession(enabled: enabled) { success in
                continuation.resume(returning: success)
            }
        }
    }
}
