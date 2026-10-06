# Activity catalog v1 — 2026-09-27

Additive to native-v1.md. Public `GET /v1/catalog` returns the complete small catalog, including closed/unknown records needed by saved lists and historical exchange context. No auth required. No production bundled fake activities. Return `200` with empty arrays when no source has been imported; transport/server failure is not empty success.

```ts
type Organization = { id: string; name: string; description: string };
type Program = { id: string; organizationId: string; title: string; description: string };
type Schedule = { id: string; title: string; startAt: string | null; endAt: string | null; dateLabel: string; timeZone: string };
type Activity = {
  id: string; programId: string; organizationId: string;
  title: string; summary: string; participationType: 'registration' | 'selection';
  recruitmentStatus: 'open' | 'scheduled' | 'closed' | 'unknown';
  isRecruiting: boolean;
  recruitmentStartAt: string | null; recruitmentEndAt: string | null;
  dateLabel: string; location: string | null; cost: string | null;
  audience: string | null; qualification: string | null;
  roles: string[]; schedules: Schedule[];
  officialUrl: string; applicationUrl: string | null;
  sourceCheckedAt: string | null; validUntil: string | null;
  freshness: 'verified' | 'stale' | 'unavailable'; sourceNote: string;
};
type Catalog = { generatedAt: string; organizations: Organization[]; programs: Program[]; activities: Activity[] };
```

All fields present; optional values JSON null. IDs are stable UUIDs derived once from source identity, not random on refresh; all references valid. Times ISO8601 with timezone. Date-only official schedules keep exact dateLabel and null start/end instead of inventing midnight/duration. timeZone `Asia/Seoul` only when source establishes Korean local time. Do not combine notice conditions from different rounds.

`recruitmentStatus` is derived from the officially verified recruitment window or the administrator's status (2026-10-06 user decision): a selected `closed` always stays closed (early close); `now >= recruitmentEndAt` is closed; otherwise unverified freshness is `unknown`; `now < recruitmentStartAt` is scheduled; any other activity with a start or end date is open (start inclusive, end exclusive, either date alone suffices); with neither date the administrator's status applies. `isRecruiting` is true only when that status is open and freshness is verified (sourceCheckedAt <= now < validUntil). Maximum verification lifetime 24h is a reversible conservative policy, not organizer guarantee. Exact deadline is exclusive; date-only deadlines can be normalized to next midnight in evidenced timezone, with sourceNote explaining end-of-day normalization. Fetch success alone does not renew semantic verification. Parser uncertainty/fetch failure never promotes stale data to current. Last good content survives failure; freshness/status are updated conservatively. Server recalculates freshness on reads. Client cache must also hide records from discovery after validUntil. Never label old cached data as current/live.

Discovery: only isRecruiting=true and still within validUntil; no search, no own-card module. Explicit loading/error/retry/empty states. Saved tab stores program and organization UUID sets on device; can show closed/unknown activities with status and source. Save success only after SwiftData/Room commit; failed writes retain previous state. Do not silently upload or claim cloud sync. A failed refresh preserves cache with visible last-checked/offline state, distinct from a successful empty result.

Detail: identity, participation type, summary, schedule(s)/dateLabel, roles, audience/qualification, cost/location, source link and checked time; unknown values say not confirmed. Thin fixed checked banner for user-reported applied status: `이 활동은 이미 신청한 활동이에요.` Keep the report editable and use an official-site action after reporting applied. External application page is an in-app browser/WebView (safe http(s) URL validation; prefer HTTPS). Close after an application attempt prompts applied / not applied / later. Persist activity-keyed user report; cancel/later never auto-marks applied. Can correct state later; report is not organizer acceptance/payment. Opening source alone must not trigger application prompt. No autofill/support claim for if(kakao) yet (#36). External-login failures can offer system browser and remain honest about inability to observe completion. Device-local state, not account sync.

Registered activity picker for QR/direct-send context may use the same catalog (including past activities); label direct-entry and none distinctly. Persist registered = activityId with label null under native-v1 context exclusivity. Resolve the activity title through the catalog for receipt summaries, detail and search. Missing catalog references say activity information is unavailable rather than displaying a UUID as a title. A future durable title snapshot would require an explicit additive native-v1 contract change; do not silently add a label to registered context requests.

Tests use fixtures only in test targets. HTTP integration uses the actual API and isolated DB. Shared catalog contract changes belong to coordinator; workers request adjustments by Orca message. OS calendar/push/form autofill are separate follow-ups, not completed by this slice.
