import Foundation
import GoogleSignIn
import UIKit

/// The device side of Google sign-in with the GoogleSignIn SDK (contract "구글 로그인 — 2026-10-19 네이티브 SDK로 전환"):
/// the server's nonce goes into the SDK request, Google's ID token comes out and is sent to `POST /v1/auth/google`.
/// The SDK reads the iOS client ID from Info.plist `GIDClientID` (build setting `GOOGLE_IOS_CLIENT_ID`).
enum GoogleNativeSignIn {
    /// False when the build has no iOS client ID: the button stays hidden (there is nothing the SDK could sign in with).
    static func isConfigured(_ info: [String: Any] = Bundle.main.infoDictionary ?? [:]) -> Bool {
        !(info["GIDClientID"] as? String ?? "").isEmpty
    }

    /// Opens Google's account sheet and returns the ID token whose `nonce` claim is the server's nonce.
    /// Closing the sheet throws `Passkey.Failure.canceled`, like a passkey request; anything else is `.failed`.
    @MainActor static func authorize(nonce: String) async throws -> String {
        guard let presenter = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).flatMap(\.windows)
            .first(where: \.isKeyWindow)?.rootViewController else { throw Passkey.Failure.failed }
        let result: GIDSignInResult
        do {
            result = try await GIDSignIn.sharedInstance.signIn(withPresenting: presenter, hint: nil, additionalScopes: nil, nonce: nonce)
        } catch let error as GIDSignInError where error.code == .canceled {
            throw Passkey.Failure.canceled
        } catch { throw Passkey.Failure.failed }
        guard let token = result.user.idToken?.tokenString, !token.isEmpty else { throw Passkey.Failure.failed }
        return token
    }

    /// The SDK's own redirect (`com.googleusercontent.apps.<id>://…`); true when the URL was Google's and is consumed.
    @MainActor static func handle(_ url: URL) -> Bool { GIDSignIn.sharedInstance.handle(url) }
}
