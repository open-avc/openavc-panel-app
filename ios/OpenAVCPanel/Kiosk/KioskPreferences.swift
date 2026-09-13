import CryptoKit
import Foundation
import Security

/// Persists the dedicated-panel settings: whether the panel should ask iOS to
/// lock it every time it opens, and a salted SHA-256 hash of the admin PIN.
///
/// Mirrors `KioskPreferences.kt` on Android, keys included, so the two are
/// legible side by side. `kioskEnabled` keeps the Android name; on iOS it means
/// "request a Guided Access session on launch", which only takes on an iPad an
/// MDM has allowed to lock itself (Autonomous Single App Mode).
///
/// The PIN is never stored in plaintext. It has to resist shoulder surfing and
/// casual tampering by people at the panel, not an attacker with the file
/// system, and a salted hash is cheap.
struct KioskPreferences {
    private let defaults: UserDefaults

    private static let suiteName = "openavc_kiosk_prefs"

    private enum Key {
        static let enabled = "kiosk_enabled"
        static let pinHash = "pin_hash"
        static let pinSalt = "pin_salt"
    }

    init(defaults: UserDefaults? = nil) {
        self.defaults = defaults ?? UserDefaults(suiteName: Self.suiteName) ?? .standard
    }

    var kioskEnabled: Bool {
        get { defaults.bool(forKey: Key.enabled) }
        nonmutating set { defaults.set(newValue, forKey: Key.enabled) }
    }

    func hasPin() -> Bool {
        defaults.string(forKey: Key.pinHash) != nil
    }

    func setPin(_ pin: String) {
        var salt = [UInt8](repeating: 0, count: 16)
        _ = SecRandomCopyBytes(kSecRandomDefault, salt.count, &salt)
        let saltData = Data(salt)
        defaults.set(Self.hex(saltData), forKey: Key.pinSalt)
        defaults.set(Self.hex(Self.hash(pin, salt: saltData)), forKey: Key.pinHash)
    }

    func clearPin() {
        defaults.removeObject(forKey: Key.pinSalt)
        defaults.removeObject(forKey: Key.pinHash)
    }

    func checkPin(_ pin: String) -> Bool {
        guard let saltHex = defaults.string(forKey: Key.pinSalt),
              let expected = defaults.string(forKey: Key.pinHash),
              let salt = Self.bytes(fromHex: saltHex)
        else { return false }
        let actual = Self.hex(Self.hash(pin, salt: salt))
        return Self.constantTimeEquals(expected, actual)
    }

    private static func hash(_ pin: String, salt: Data) -> Data {
        var hasher = SHA256()
        hasher.update(data: salt)
        hasher.update(data: Data(pin.utf8))
        return Data(hasher.finalize())
    }

    static func hex(_ data: Data) -> String {
        data.map { String(format: "%02x", $0) }.joined()
    }

    static func bytes(fromHex hex: String) -> Data? {
        guard hex.count % 2 == 0 else { return nil }
        var data = Data(capacity: hex.count / 2)
        var index = hex.startIndex
        while index < hex.endIndex {
            let next = hex.index(index, offsetBy: 2)
            guard let byte = UInt8(hex[index..<next], radix: 16) else { return nil }
            data.append(byte)
            index = next
        }
        return data
    }

    private static func constantTimeEquals(_ a: String, _ b: String) -> Bool {
        guard a.utf8.count == b.utf8.count else { return false }
        var result: UInt8 = 0
        for (x, y) in zip(a.utf8, b.utf8) { result |= x ^ y }
        return result == 0
    }
}
