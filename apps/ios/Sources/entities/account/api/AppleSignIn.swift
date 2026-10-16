import AuthenticationServices
import SwiftUI

/// The device side of Sign in with Apple (contract "애플 로그인"): a server nonce in, Apple's identity token out.
/// Needs the `com.apple.developer.applesignin` entitlement; without it the OS fails the request.
enum AppleSignIn {
    /// What `POST /v1/auth/apple` needs from Apple's answer.
    struct Credential: Sendable {
        let identityToken: String
        /// Apple gives the name only on the first authorization for this app; nil afterwards or when left empty.
        let displayName: String?
    }

    /// `nonce` goes into the request exactly as the server sent it (not hashed); the server compares it to the token's claim.
    /// Closing the OS sheet throws `Passkey.Failure.canceled`, like a passkey request.
    @MainActor static func authorize(nonce: String, with controller: AuthorizationController) async throws -> Credential {
        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = nonce
        let result: ASAuthorizationResult
        do { result = try await controller.performRequest(request) } catch let error as ASAuthorizationError where error.code == .canceled {
            throw Passkey.Failure.canceled
        } catch { throw Passkey.Failure.failed }
        guard case .appleID(let credential) = result, let data = credential.identityToken,
              let token = String(data: data, encoding: .utf8) else { throw Passkey.Failure.failed }
        let name = credential.fullName.map { PersonNameComponentsFormatter().string(from: $0) }?
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return Credential(identityToken: token, displayName: name?.isEmpty == false ? name : nil)
    }
}
