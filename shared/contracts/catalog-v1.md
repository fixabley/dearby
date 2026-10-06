# Activity catalog v1 — 2026-09-27

Additive to native-v1.md. Public `GET /v1/catalog` returns the complete small catalog, including closed/unknown records needed by saved lists and historical exchange context. No auth required. No production bundled fake activities. Return `200` with empty arrays when no source has been imported; transport/server failure is not empty success.

```ts
type Organization = { id: string; name: string; description: string; parentId: string | null };
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

## Organization hierarchy — 2026-10-06 승인

사용자 결정: 조직은 하위 조직을 가질 수 있다(예: 회사 → 사업부·팀). 프로그램은 어느 깊이의 조직에도 속할 수 있다. 기존 필드는 그대로 두고 아래만 추가한다.

- `Organization.parentId: string | null`. 최상위 조직은 null. 응답에는 항상 이 필드가 있다.
- 깊이는 최상위를 1로 세어 최대 4단계다. 자기 자신이나 자기 하위 조직을 부모로 지정할 수 없다(순환 금지). DB가 이를 거부하고 어드민도 선택지에서 제외한다.
- 하위 조직이나 프로그램이 있는 조직은 삭제할 수 없다. 부모를 바꾸는 이동은 허용하며 위 깊이·순환 규칙을 다시 검사한다.
- 공개 스냅샷은 게시된 활동이 참조하는 조직과 **그 모든 상위 조직**을 포함한다. 모든 `parentId`는 같은 스냅샷 안의 조직을 가리킨다.
- 소비자 반영 순서: API의 조직 검증(`apps/dearby-api/src/catalog.ts`의 strict schema)이 먼저 `parentId`를 받아야 한다. 그 전에 스냅샷이 필드를 내보내면 API 동기화가 실패하므로, API 반영이 운영에 배포된 뒤 스냅샷 함수를 운영 DB에 적용한다. 웹은 모르는 필드를 무시하므로 같은 시점에 깨지지 않는다.
- 웹·앱의 표시(예: `NAVER · DAN`)와 상위 조직 이름으로 하위 조직 활동까지 검색하는 기능은 별도 작업으로 결정한다. 수집기는 프로그램 단위로 동작하므로 영향이 없다.
- 운영 Supabase 마이그레이션 적용은 사용자 승인 후 root가 수행한다.
