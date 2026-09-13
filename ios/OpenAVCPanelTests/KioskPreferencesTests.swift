import XCTest
@testable import OpenAVCPanel

/// The admin PIN and the lock switch, on an isolated defaults suite.
///
/// Android has no unit test for `KioskPreferences.kt`; these pin the contract
/// the two share: a salted hash that never stores the PIN, a wrong PIN and a
/// missing PIN both refused, and a change that invalidates the old one.
final class KioskPreferencesTests: XCTestCase {

    private var suite: String!
    private var defaults: UserDefaults!
    private var prefs: KioskPreferences!

    override func setUp() {
        super.setUp()
        suite = "test.kiosk.\(UUID().uuidString)"
        defaults = UserDefaults(suiteName: suite)
        prefs = KioskPreferences(defaults: defaults)
    }

    override func tearDown() {
        defaults.removePersistentDomain(forName: suite)
        super.tearDown()
    }

    func testNoPinByDefault() {
        XCTAssertFalse(prefs.hasPin())
        XCTAssertFalse(prefs.checkPin("1234"))
        XCTAssertFalse(prefs.checkPin(""))
    }

    func testSetPinThenCheck() {
        prefs.setPin("2468")
        XCTAssertTrue(prefs.hasPin())
        XCTAssertTrue(prefs.checkPin("2468"))
        XCTAssertFalse(prefs.checkPin("2469"))
        XCTAssertFalse(prefs.checkPin("246"))
        XCTAssertFalse(prefs.checkPin(""))
    }

    func testPinIsNeverStoredInPlaintext() {
        prefs.setPin("2468")
        for (key, value) in defaults.dictionaryRepresentation() where key.hasPrefix("pin_") {
            XCTAssertNotEqual(value as? String, "2468", "\(key) holds the PIN in the clear")
        }
        XCTAssertEqual(defaults.string(forKey: "pin_salt")?.count, 32)
        XCTAssertEqual(defaults.string(forKey: "pin_hash")?.count, 64)
    }

    func testChangingPinInvalidatesOldOne() {
        prefs.setPin("1111")
        prefs.setPin("2222")
        XCTAssertFalse(prefs.checkPin("1111"))
        XCTAssertTrue(prefs.checkPin("2222"))
    }

    func testClearPin() {
        prefs.setPin("1234")
        prefs.clearPin()
        XCTAssertFalse(prefs.hasPin())
        XCTAssertFalse(prefs.checkPin("1234"))
    }

    func testKioskEnabledRoundTrip() {
        XCTAssertFalse(prefs.kioskEnabled)
        prefs.kioskEnabled = true
        XCTAssertTrue(prefs.kioskEnabled)
        XCTAssertTrue(KioskPreferences(defaults: defaults).kioskEnabled)
        prefs.kioskEnabled = false
        XCTAssertFalse(prefs.kioskEnabled)
    }

    func testHexRoundTrip() {
        let data = Data([0x00, 0x7f, 0x80, 0xff])
        XCTAssertEqual(KioskPreferences.hex(data), "007f80ff")
        XCTAssertEqual(KioskPreferences.bytes(fromHex: "007f80ff"), data)
        XCTAssertNil(KioskPreferences.bytes(fromHex: "abc"))
        XCTAssertNil(KioskPreferences.bytes(fromHex: "zz"))
    }
}
