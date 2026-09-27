# Activity catalog implementation — issue #46

Device-local SwiftData library and API-namespaced catalog snapshot, both published only after explicit successful commit. Catalog failure retains the previous snapshot; unparseable cache is surfaced separately so a network fetch can recover without blocking profiles or local bookmarks. Library corruption still preserves disk and fails initialization rather than replacing personal records with empty state.

`GET /v1/catalog` is unauthenticated, uses exact catalog-v1 wire names and no bundled content. Separate organization/program/activity values retain only IDs for relationships; the feature owner composes a coherent atomic catalog snapshot, avoiding torn references during refresh. This full-snapshot endpoint supersedes the archived per-entity mock-source cache for this feature; no extra repositories or fake source layer were added.

Discovery excludes unverified/future/expired/deadline-reached records and enforces the maximum 24-hour verification lifetime independently from fetch time. Saved groups can display historical records and unknown IDs without dropping saves. Details provide unknown values honestly and no calendar/no-conflict or autofill success claims. User report is activity-keyed, independent of program and organization bookmarks and login.

Application and source actions are separate. Both use native SFSafariViewController after http(s), host and credential validation; only the application sheet dismissal asks for applied/not applied/later. Later/cancel preserves prior report. Exact banner copy: “이 활동은 이미 신청한 활동이에요.” with checkmark, official-site action and user-report/acceptance distinction; state can be corrected. External login may require Safari using the native browser menu; app cannot observe completion, share external authentication automatically or verify acceptance. Source opening never prompts. if(kakao) autofill remains #36.

Apple delegate reference: https://developer.apple.com/documentation/safariservices/sfsafariviewcontrollerdelegate/safariviewcontrollerdidfinish(_:)

Initial validation 2026-09-27: Debug Simulator build, strict SwiftLint and 16 Harmonize/SwiftSyntax structure tests passed; initial 23 catalog/domain/storage/contract tests passed. Corrupt-cache recovery regression and final integration/UI checks are pending at this checkpoint. Default Simulator ad-hoc signing retained for Keychain; no developer account used.
