/**
 * 개인정보 동의 문서 버전 (contract "개인정보 동의"): the effective dates of `/privacy` and `/terms`, the same values as the API's
 * `dearby.consent.*-version` and the apps' `ConsentVersions`. A version the server no longer accepts gets 400 `CONSENT_VERSION`.
 */
export const CONSENT_VERSIONS = { privacy: "2026-10-26", terms: "2026-10-22" } as const;
