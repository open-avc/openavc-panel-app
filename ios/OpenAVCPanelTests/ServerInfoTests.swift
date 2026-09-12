import XCTest
@testable import OpenAVCPanel

/// Mirrors `ServerInfoTest.kt` case for case.
///
/// The two apps parse the same printed QR code and talk to the same server, so
/// a divergence here is a real bug on one platform. Keeping the cases paired is
/// what makes that visible.
final class ServerInfoTests: XCTestCase {

    func testFromPanelUrlHttpPreservesScheme() {
        let info = ServerInfo.fromPanelUrl("http://192.168.1.10:8080/panel")
        XCTAssertEqual(info?.scheme, "http")
        XCTAssertEqual(info?.host, "192.168.1.10")
        XCTAssertEqual(info?.port, 8080)
        XCTAssertEqual(info?.panelUrl, "http://192.168.1.10:8080/panel")
    }

    func testFromPanelUrlHttpsPreservesScheme() {
        let info = ServerInfo.fromPanelUrl("https://192.168.1.10:8443/panel")
        XCTAssertEqual(info?.scheme, "https")
        XCTAssertEqual(info?.port, 8443)
        XCTAssertEqual(info?.panelUrl, "https://192.168.1.10:8443/panel")
    }

    func testFromPanelUrlHttpsNoPortDefaultsTo8443() {
        // OpenAVC's own default TLS port, not the IANA 443.
        let info = ServerInfo.fromPanelUrl("https://server.local/panel")
        XCTAssertEqual(info?.scheme, "https")
        XCTAssertEqual(info?.port, 8443)
    }

    func testFromPanelUrlHttpNoPortDefaultsTo8080() {
        let info = ServerInfo.fromPanelUrl("http://server.local/panel")
        XCTAssertEqual(info?.scheme, "http")
        XCTAssertEqual(info?.port, 8080)
    }

    func testFromPanelUrlNoPathDefaultsToPanel() {
        let info = ServerInfo.fromPanelUrl("https://192.168.1.10:8443")
        XCTAssertEqual(info?.panelUrl, "https://192.168.1.10:8443/panel")
    }

    func testFromPanelUrlMixedCaseSchemeNormalizes() {
        let info = ServerInfo.fromPanelUrl("HTTPS://192.168.1.10:8443/panel")
        XCTAssertEqual(info?.scheme, "https")
    }

    func testFromPanelUrlPairPathIsKept() {
        // The Programmer's QR encodes /pair, not /panel. Parsing must survive
        // it; the validator is what turns it into a panel URL.
        let info = ServerInfo.fromPanelUrl("http://192.168.1.50:8080/pair")
        XCTAssertEqual(info?.host, "192.168.1.50")
        XCTAssertEqual(info?.port, 8080)
        XCTAssertEqual(info?.panelUrl, "http://192.168.1.50:8080/pair")
    }

    func testFromPanelUrlGarbageReturnsNil() {
        XCTAssertNil(ServerInfo.fromPanelUrl("not a url"))
        XCTAssertNil(ServerInfo.fromPanelUrl("ftp://server/panel"))
        XCTAssertNil(ServerInfo.fromPanelUrl(""))
    }

    func testStatusUrlUsesScheme() {
        let http = ServerInfo(
            name: "x", instanceId: "", host: "h", port: 8080,
            version: "", panelUrl: "http://h:8080/panel", scheme: "http"
        )
        XCTAssertEqual(http.statusUrl, "http://h:8080/api/status")

        var https = http
        https.scheme = "https"
        let httpsOn8443 = ServerInfo(
            name: https.name, instanceId: https.instanceId, host: https.host,
            port: 8443, version: https.version,
            panelUrl: https.panelUrl, scheme: "https"
        )
        XCTAssertEqual(httpsOn8443.statusUrl, "https://h:8443/api/status")
    }

    func testHealthUrlUsesScheme() {
        let info = ServerInfo(
            name: "x", instanceId: "", host: "h", port: 8443,
            version: "", panelUrl: "https://h:8443/panel", scheme: "https"
        )
        XCTAssertEqual(info.healthUrl, "https://h:8443/api/health")
    }

    func testDefaultSchemeIsHttp() {
        // Callers that don't pass a scheme (manual entry, a restore from
        // storage written before the scheme key existed) stay on plain HTTP.
        let info = ServerInfo(
            name: "x", instanceId: "", host: "h", port: 8080,
            version: "", panelUrl: "http://h:8080/panel"
        )
        XCTAssertEqual(info.scheme, "http")
        XCTAssertEqual(info.statusUrl, "http://h:8080/api/status")
    }
}
