// Passkeys in the browser (contract "패스키 로그인" › API: standard WebAuthn JSON with base64url). Browsers with
// `parseRequestOptionsFromJSON` / `toJSON` use them; older ones get the same conversion by hand.
type Json = Record<string, unknown>;

/** Thrown when the browser has no WebAuthn at all. */
export class PasskeyUnsupported extends Error {}
/** The person closed the OS passkey prompt, or no passkey matched: no message is shown. */
export function passkeyCancelled(error: unknown) {
  return error instanceof DOMException && error.name === "NotAllowedError";
}
/** The authenticator already holds a passkey for this account (adding a second one on the same device). */
export function passkeyExists(error: unknown) {
  return error instanceof DOMException && error.name === "InvalidStateError";
}

function fromBase64url(value: string): ArrayBuffer {
  const base64 = value.replace(/-/g, "+").replace(/_/g, "/");
  const binary = atob(base64 + "=".repeat((4 - (base64.length % 4)) % 4));
  return Uint8Array.from(binary, (char) => char.charCodeAt(0)).buffer;
}
function toBase64url(buffer: ArrayBuffer | null): string | null {
  if (!buffer) return null;
  return btoa(String.fromCharCode(...new Uint8Array(buffer)))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
}
function descriptors(list: unknown) {
  return Array.isArray(list)
    ? (list as Json[]).map((item) => ({ ...item, id: fromBase64url(item.id as string) }) as PublicKeyCredentialDescriptor)
    : undefined;
}

type Parsers = {
  parseRequestOptionsFromJSON?: (json: unknown) => PublicKeyCredentialRequestOptions;
  parseCreationOptionsFromJSON?: (json: unknown) => PublicKeyCredentialCreationOptions;
};
function parsers(): Parsers {
  if (typeof window === "undefined" || !("PublicKeyCredential" in window) || !navigator.credentials) throw new PasskeyUnsupported();
  return window.PublicKeyCredential as unknown as Parsers;
}

/** `navigator.credentials.get` from the API's `RequestOptionsJSON`; returns the `AuthenticationResponseJSON` to send back. */
export async function getPasskey(options: Json): Promise<Json> {
  const { parseRequestOptionsFromJSON } = parsers();
  const publicKey = parseRequestOptionsFromJSON
    ? parseRequestOptionsFromJSON(options)
    : ({ ...options, challenge: fromBase64url(options.challenge as string), allowCredentials: descriptors(options.allowCredentials) } as PublicKeyCredentialRequestOptions);
  const credential = (await navigator.credentials.get({ publicKey })) as PublicKeyCredential | null;
  if (!credential) throw new DOMException("No credential", "NotAllowedError");
  return toJSON(credential);
}

/** `navigator.credentials.create` from the API's `CreationOptionsJSON`; returns the `RegistrationResponseJSON`. */
export async function createPasskey(options: Json): Promise<Json> {
  const { parseCreationOptionsFromJSON } = parsers();
  const user = options.user as Json;
  const publicKey = parseCreationOptionsFromJSON
    ? parseCreationOptionsFromJSON(options)
    : ({
        ...options,
        challenge: fromBase64url(options.challenge as string),
        user: { ...user, id: fromBase64url(user.id as string) },
        excludeCredentials: descriptors(options.excludeCredentials),
      } as PublicKeyCredentialCreationOptions);
  const credential = (await navigator.credentials.create({ publicKey })) as PublicKeyCredential | null;
  if (!credential) throw new DOMException("No credential", "NotAllowedError");
  return toJSON(credential);
}

function toJSON(credential: PublicKeyCredential): Json {
  if ("toJSON" in credential && typeof credential.toJSON === "function") return credential.toJSON() as Json;
  const response = credential.response as AuthenticatorAssertionResponse & AuthenticatorAttestationResponse;
  const attestation = "attestationObject" in response && response.attestationObject;
  return {
    id: credential.id,
    rawId: toBase64url(credential.rawId),
    type: credential.type,
    authenticatorAttachment: credential.authenticatorAttachment ?? undefined,
    clientExtensionResults: credential.getClientExtensionResults(),
    response: attestation
      ? {
          clientDataJSON: toBase64url(response.clientDataJSON),
          attestationObject: toBase64url(response.attestationObject),
          transports: typeof response.getTransports === "function" ? response.getTransports() : [],
        }
      : {
          clientDataJSON: toBase64url(response.clientDataJSON),
          authenticatorData: toBase64url(response.authenticatorData),
          signature: toBase64url(response.signature),
          userHandle: toBase64url(response.userHandle),
        },
  };
}
