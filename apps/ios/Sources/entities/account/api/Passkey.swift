import AuthenticationServices
import SwiftUI

/// The device side of passkey sign-in (contract "패스키 로그인"): server options in, WebAuthn response JSON out.
/// Every binary field is base64url without padding.
enum Passkey {
    /// The API's `WEBAUTHN_RP_ID`, also in the `webcredentials:` entitlement. Changing it orphans every passkey.
    static let relyingParty = "wid.io.kr"

    /// What the app reads from `PublicKeyCredentialCreationOptionsJSON`; the rest is the server's to check.
    struct CreationOptions: Decodable, Sendable {
        struct User: Decodable, Sendable { let id: String; let name: String; let displayName: String? }
        let challenge: String
        let user: User
    }
    /// What the app reads from `PublicKeyCredentialRequestOptionsJSON`; `allowCredentials` is empty by contract.
    struct RequestOptions: Decodable, Sendable {
        let challenge: String
    }
    /// `RegistrationResponseJSON` or `AuthenticationResponseJSON`; the response fields of the other kind are left out.
    struct Credential: Encodable, Sendable {
        struct Response: Encodable, Sendable {
            let clientDataJSON: String
            var attestationObject: String?
            var authenticatorData: String?
            var signature: String?
            var userHandle: String?
        }
        let id: String
        let rawId: String
        let type = "public-key"
        let authenticatorAttachment = "platform"
        let response: Response
        let clientExtensionResults = [String: String]()
    }
    enum Failure: Error { case canceled, failed }

    @MainActor static func register(_ options: CreationOptions, with controller: AuthorizationController) async throws -> Credential {
        guard let challenge = Data(base64URL: options.challenge), let userID = Data(base64URL: options.user.id) else { throw Failure.failed }
        let request = provider.createCredentialRegistrationRequest(challenge: challenge, name: options.user.name, userID: userID)
        request.displayName = options.user.displayName
        guard case .passkeyRegistration(let result) = try await perform(request, controller),
              let attestation = result.rawAttestationObject else { throw Failure.failed }
        return Credential(id: result.credentialID.base64URL, rawId: result.credentialID.base64URL,
                          response: .init(clientDataJSON: result.rawClientDataJSON.base64URL, attestationObject: attestation.base64URL))
    }
    @MainActor static func assert(_ options: RequestOptions, with controller: AuthorizationController) async throws -> Credential {
        guard let challenge = Data(base64URL: options.challenge) else { throw Failure.failed }
        guard case .passkeyAssertion(let result) = try await perform(provider.createCredentialAssertionRequest(challenge: challenge), controller)
        else { throw Failure.failed }
        return Credential(id: result.credentialID.base64URL, rawId: result.credentialID.base64URL,
                          response: .init(clientDataJSON: result.rawClientDataJSON.base64URL, authenticatorData: result.rawAuthenticatorData.base64URL,
                                          signature: result.signature.base64URL, userHandle: result.userID?.base64URL))
    }

    private static var provider: ASAuthorizationPlatformPublicKeyCredentialProvider {
        ASAuthorizationPlatformPublicKeyCredentialProvider(relyingPartyIdentifier: relyingParty)
    }
    /// Closing the OS sheet, or having no passkey for this app on the device, comes back as `canceled`.
    @MainActor private static func perform(_ request: ASAuthorizationRequest, _ controller: AuthorizationController) async throws -> ASAuthorizationResult {
        do { return try await controller.performRequest(request) } catch let error as ASAuthorizationError where error.code == .canceled {
            throw Failure.canceled
        } catch { throw Failure.failed }
    }
}

private extension Data {
    init?(base64URL text: String) {
        var base64 = text.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        base64 += String(repeating: "=", count: (4 - base64.count % 4) % 4)
        self.init(base64Encoded: base64)
    }
    var base64URL: String {
        base64EncodedString().replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
    }
}
