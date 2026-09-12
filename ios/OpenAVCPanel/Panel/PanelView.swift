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

    func makeCoordinator() -> Coordinator {
        Coordinator(server: server, onNavigationFailure: onNavigationFailure)
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

    final class Coordinator: NSObject, WKNavigationDelegate {
        var server: ServerInfo
        private let onNavigationFailure: (String) -> Void
        private let trustStore = CertTrustStore()

        init(server: ServerInfo, onNavigationFailure: @escaping (String) -> Void) {
            self.server = server
            self.onNavigationFailure = onNavigationFailure
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
