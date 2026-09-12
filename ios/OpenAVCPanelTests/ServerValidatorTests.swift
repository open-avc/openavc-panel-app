import XCTest
@testable import OpenAVCPanel

/// Mirrors `ServerValidatorTest.kt`.
///
/// Only the pure pieces are asserted here — the URL a probe would target, and
/// how a response classifies as a TLS handoff. The IO path needs a real server
/// and lives in the manual test plan, same as on Android.
final class ServerValidatorTests: XCTestCase {

    func testUrlHttpStatus() {
        let url = ServerValidator.url(host: "192.168.1.10", port: 8080, scheme: "http", path: "/api/status")
        XCTAssertEqual(url?.absoluteString, "http://192.168.1.10:8080/api/status")
    }

    func testUrlHttpsStatus() {
        let url = ServerValidator.url(host: "192.168.1.10", port: 8443, scheme: "https", path: "/api/status")
        XCTAssertEqual(url?.absoluteString, "https://192.168.1.10:8443/api/status")
    }

    func testUrlHttpsHealth() {
        let url = ServerValidator.url(host: "server.local", port: 8443, scheme: "https", path: "/api/health")
        XCTAssertEqual(url?.absoluteString, "https://server.local:8443/api/health")
    }

    func testHandoffHttpsLocationUsesExplicitPort() {
        XCTAssertEqual(
            ServerValidator.tlsHandoffPort(code: 302, location: "https://192.168.1.10:8443/api/status"),
            8443
        )
    }

    func testHandoffHttpsLocationDefaultsTo443() {
        XCTAssertEqual(
            ServerValidator.tlsHandoffPort(code: 302, location: "https://192.168.1.10/api/status"),
            443
        )
    }

    func testHandoffAnyRedirectCodeQualifies() {
        for code in [301, 302, 307, 308] {
            XCTAssertEqual(
                ServerValidator.tlsHandoffPort(code: code, location: "https://192.168.1.10:8443/"),
                8443,
                "code \(code) should classify as a handoff"
            )
        }
    }

    func testHandoffCertifiedNameLocationPortStillHonoured() {
        // A server with a cloud-issued certificate redirects to its public DNS
        // name. The port carries over; the caller keeps probing the scanned IP
        // rather than following that name, which may not even resolve.
        XCTAssertEqual(
            ServerValidator.tlsHandoffPort(
                code: 302,
                location: "https://192-168-1-10.abc123.example.net:8443/api/status?probe=1"
            ),
            8443
        )
    }

    func testHandoffHttpLocationIsNotAHandoff() {
        XCTAssertNil(ServerValidator.tlsHandoffPort(code: 302, location: "http://192.168.1.10:8080/panel"))
    }

    func testHandoffNon3xxIsNotAHandoff() {
        for code in [200, 404, 500] {
            XCTAssertNil(ServerValidator.tlsHandoffPort(code: code, location: "https://192.168.1.10:8443/"))
        }
    }

    func testHandoffMissingOrBlankLocationIsNotAHandoff() {
        XCTAssertNil(ServerValidator.tlsHandoffPort(code: 302, location: nil))
        XCTAssertNil(ServerValidator.tlsHandoffPort(code: 302, location: ""))
        XCTAssertNil(ServerValidator.tlsHandoffPort(code: 302, location: "   "))
    }

    func testHandoffRelativeLocationIsNotAHandoff() {
        XCTAssertNil(ServerValidator.tlsHandoffPort(code: 302, location: "/panel"))
    }

    func testHandoffMalformedLocationIsNotAHandoff() {
        XCTAssertNil(ServerValidator.tlsHandoffPort(code: 302, location: "not a url"))
        XCTAssertNil(ServerValidator.tlsHandoffPort(code: 302, location: "https://"))
    }
}
