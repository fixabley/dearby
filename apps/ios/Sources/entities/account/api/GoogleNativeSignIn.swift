import AuthenticationServices
import CryptoKit
import Foundation
import UIKit

/// The device side of Google sign-in over the server's web flow (contract "구글 로그인"): a verifier whose hash goes to
/// `POST /v1/auth/google/web/start`, the system browser sheet for the authorize URL, and the one-time `code` the server
/// sends back on `dearby://auth/google`. No Google SDK; the user's existing Google session in Safari is reused.
enum GoogleSignIn {
    /// `value` stays on the device and is sent only to `web/exchange`; `hash` (SHA-256, base64url, no padding) goes to `web/start`.
    struct Verifier: Sendable {
        let value: String
        let hash: String
    }

    /// 32 random bytes → 43 base64url characters, like a PKCE verifier.
    static func makeVerifier() -> Verifier {
        var bytes = [UInt8](repeating: 0, count: 32)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        let value = base64URL(Data(bytes))
        return Verifier(value: value, hash: base64URL(Data(SHA256.hash(data: Data(value.utf8)))))
    }

    /// Opens Google's authorize page and returns the server's one-time code from `dearby://auth/google?code=…`.
    /// Closing the sheet, or Google reporting `error=cancelled`, throws `Passkey.Failure.canceled` like a passkey request;
    /// anything else is `.failed`.
    @MainActor static func authorize(_ url: URL) async throws -> String {
        let callback: URL = try await withCheckedThrowingContinuation { continuation in
            let session = ASWebAuthenticationSession(url: url, callback: .customScheme("dearby")) { url, error in
                if let error {
                    let canceled = (error as? ASWebAuthenticationSessionError)?.code == .canceledLogin
                    continuation.resume(throwing: canceled ? Passkey.Failure.canceled : Passkey.Failure.failed)
                } else if let url {
                    continuation.resume(returning: url)
                } else {
                    continuation.resume(throwing: Passkey.Failure.failed)
                }
            }
            session.presentationContextProvider = presenter
            // Not ephemeral on purpose: the person's Google session in Safari makes "Google로 계속하기" one tap.
            session.prefersEphemeralWebBrowserSession = false
            running = session
            if !session.start() { continuation.resume(throwing: Passkey.Failure.failed) }
        }
        running = nil
        return try code(from: callback)
    }

    /// Exactly `dearby://auth/google?code=<one-time code>`; `?error=cancelled` is a cancel, anything else a failure.
    static func code(from url: URL) throws -> String {
        guard let parts = URLComponents(url: url, resolvingAgainstBaseURL: false), parts.scheme == "dearby", parts.host == "auth",
              parts.path == "/google" else { throw Passkey.Failure.failed }
        let items = parts.queryItems ?? []
        if let code = items.first(where: { $0.name == "code" })?.value, !code.isEmpty { return code }
        if items.first(where: { $0.name == "error" })?.value == "cancelled" { throw Passkey.Failure.canceled }
        throw Passkey.Failure.failed
    }

    private static func base64URL(_ data: Data) -> String {
        data.base64EncodedString().replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    /// The running session is kept alive until its completion handler fires.
    @MainActor private static var running: ASWebAuthenticationSession?
    @MainActor private static let presenter = Presenter()

    /// The browser sheet is presented over the key window.
    private final class Presenter: NSObject, ASWebAuthenticationPresentationContextProviding {
        func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
            MainActor.assumeIsolated {
                UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows).first { $0.isKeyWindow }
                    ?? ASPresentationAnchor()
            }
        }
    }
}
