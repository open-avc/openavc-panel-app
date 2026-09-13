import SwiftUI
import WebKit

/// The room panel itself: the server's own web UI, full screen.
///
/// Everything the user touches is that web app. This wrapper exists only to
/// host it edge to edge, keep the screen awake, and answer the TLS challenge
/// with the certificate we pinned during pairing — which is the part a plain
/// browser cannot do for a self-signed certificate on a private address.
struct PanelView: UIViewRepresentable {
    let server: ServerInfo
    let onNavigationFailure: (String) -> Void
    /// Three taps in the top-left corner within two seconds, the same gesture
    /// as Android's `MainActivity.dispatchTouchEvent`. The web panel still
    /// receives every one of those touches.
    let onAdminHotspot: () -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(
            server: server,
            onNavigationFailure: onNavigationFailure,
            onAdminHotspot: onAdminHotspot
        )
    }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.allowsInlineMediaPlayback = true
        config.mediaTypesRequiringUserActionForPlayback = []
        // The panel is a single-page app that manages its own history; letting
        // the WebView keep a back-forward list only invites a swipe to leave it.
        let webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = context.coordinator
        webView.allowsBackForwardNavigationGestures = false
        webView.scrollView.bounces = false
        webView.scrollView.isScrollEnabled = false
        webView.scrollView.contentInsetAdjustmentBehavior = .never
        webView.scrollView.maximumZoomScale = 1.0
        webView.scrollView.minimumZoomScale = 1.0
        webView.isOpaque = false
        webView.backgroundColor = .black
        webView.scrollView.backgroundColor = .black

        let cornerTap = UITapGestureRecognizer(
            target: context.coordinator, action: #selector(Coordinator.cornerTapped(_:))
        )
        cornerTap.cancelsTouchesInView = false
        cornerTap.delegate = context.coordinator
        webView.addGestureRecognizer(cornerTap)

        UIApplication.shared.isIdleTimerDisabled = true

        if let url = URL(string: server.panelUrl) {
            webView.load(URLRequest(url: url))
        }
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        context.coordinator.server = server
    }

    static func dismantleUIView(_ webView: WKWebView, coordinator: Coordinator) {
        UIApplication.shared.isIdleTimerDisabled = false
        webView.stopLoading()
        webView.navigationDelegate = nil
    }

    final class Coordinator: NSObject, WKNavigationDelegate, UIGestureRecognizerDelegate {
        var server: ServerInfo
        private let onNavigationFailure: (String) -> Void
        private let onAdminHotspot: () -> Void
        private let trustStore = CertTrustStore()

        private static let cornerHotspot: CGFloat = 80
        private static let cornerTapsRequired = 3
        private static let cornerTapWindow: TimeInterval = 2
        private var cornerTapCount = 0
        private var cornerTapWindowEnd: TimeInterval = 0

        init(
            server: ServerInfo,
            onNavigationFailure: @escaping (String) -> Void,
            onAdminHotspot: @escaping () -> Void
        ) {
            self.server = server
            self.onNavigationFailure = onNavigationFailure
            self.onAdminHotspot = onAdminHotspot
        }

        // MARK: Corner triple-tap

        /// Only a touch in the corner is ours to count; everything else goes
        /// straight to the web view without this recognizer in the way.
        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch
        ) -> Bool {
            let point = touch.location(in: gestureRecognizer.view)
            return point.x <= Self.cornerHotspot && point.y <= Self.cornerHotspot
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer
        ) -> Bool {
            true
        }

        @objc func cornerTapped(_ recognizer: UITapGestureRecognizer) {
            let now = Date().timeIntervalSinceReferenceDate
            if now > cornerTapWindowEnd {
                cornerTapCount = 1
                cornerTapWindowEnd = now + Self.cornerTapWindow
                return
            }
            cornerTapCount += 1
            if cornerTapCount >= Self.cornerTapsRequired {
                cornerTapCount = 0
                cornerTapWindowEnd = 0
                onAdminHotspot()
            }
        }

        /// The whole reason this delegate exists.
        ///
        /// An OpenAVC server with TLS on presents a self-signed certificate on a
        /// private IP address. WKWebView rejects that outright, with no prompt
        /// and no way for the user to proceed — so without this the panel is
        /// simply a blank screen on every HTTPS server. We answer with the
        /// certificate pinned during pairing; an unpinned or mismatched server
        /// still gets refused, which is the behaviour we want.
        func webView(
            _ webView: WKWebView,
            didReceive challenge: URLAuthenticationChallenge,
            completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
        ) {
            guard server.scheme == "https" else {
                completionHandler(.performDefaultHandling, nil)
                return
            }
            let pinned = trustStore.lookup(
                instanceId: server.instanceId.isEmpty ? nil : server.instanceId,
                hostPort: CertTrustStore.hostPortKey(host: server.host, port: server.port)
            )
            let (disposition, credential) = PinnedTrust.evaluate(
                challenge: challenge, pinned: pinned
            )
            completionHandler(disposition, credential)
        }

        func webView(
            _ webView: WKWebView,
            didFail navigation: WKNavigation!,
            withError error: Error
        ) {
            onNavigationFailure(error.localizedDescription)
        }

        func webView(
            _ webView: WKWebView,
            didFailProvisionalNavigation navigation: WKNavigation!,
            withError error: Error
        ) {
            onNavigationFailure(error.localizedDescription)
        }
    }
}
