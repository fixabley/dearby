// 내 프로필 (contract native-v1.md "프로필과 버전 이력", "프로필 사진", "인증자료", "연락처 인증", "추천 설정 퍼널·지원 자격 정보")
// through the account branch of `/api/*`. Every call refreshes once on 401 (`account`).
import { account, accountSend, accountUpload, request } from "@/lib/client";
import type {
  ActivityInfoPage,
  EligibilityProfile,
  Evidence,
  Profile,
  ProfileRequest,
  RecommendationPreferences,
  RolePage,
} from "@/lib/account-models";
import type { ActivityPage, DiscoverySearch, RoleOwnerKind } from "@/lib/models";

export const getProfile = () => account<Profile>("/profile");
/** `If-Match` carries the version the form was loaded from; the API answers 409 when someone else saved since. */
export const putProfile = (body: ProfileRequest, version: number) => accountSend<Profile>("/profile", "PUT", body, { "If-Match": String(version) });

export const putPhoto = (file: File) => accountUpload<{ photoUrl: string }>("/profile/photo", "PUT", file);
export const deletePhoto = () => accountSend<void>("/profile/photo", "DELETE");

export const uploadEvidence = (historyId: string, file: File) => accountUpload<Evidence>(`/profile/histories/${historyId}/evidence`, "POST", file);
export const deleteEvidence = (historyId: string, evidenceId: string) => accountSend<void>(`/profile/histories/${historyId}/evidence/${evidenceId}`, "DELETE");

export type InfoKind = "organizations" | "programs" | "jobs";
const term = (q: string) => encodeURIComponent(q.trim().slice(0, 100));
/** 직무 입력의 선택 창 검색 (Bearer); `q` is trimmed and capped like the proxy expects. */
export const searchInfo = (kind: InfoKind, q: string) => account<ActivityInfoPage>(`/${kind}?q=${term(q)}&size=10`);
/** A unit a role belongs to, as the "어디에서" picker lists it; 활동 rows carry `조직 · 프로그램` as the hint. */
export type UnitPick = { kind: RoleOwnerKind; id: number; name: string; hint?: string };
/**
 * 어디에서 통합 검색 (contract "이력 (History) — 역할 매칭" › 입력 퍼널): 조직·프로그램 from the public `discovery/search` (it has no
 * 활동 type yet) and 활동 from the public list, together. One failing side still shows the other.
 */
export async function searchUnits(q: string): Promise<UnitPick[]> {
  const [units, activities] = await Promise.all([
    request<DiscoverySearch>(`/discovery/search?q=${term(q)}`).then((found) => found.items, () => []),
    request<ActivityPage>(`/activities?q=${term(q)}&size=10`).then((found) => found.items, () => []),
  ]);
  return [
    ...units.map((item) => ({ kind: item.type, id: item.id, name: item.name })),
    ...activities.map((item) => ({ kind: "activity" as const, id: item.id, name: item.name, hint: `${item.organization.name} · ${item.program.name}` })),
  ];
}
/** 그 단위의 역할 목록 (Bearer, `GET /v1/roles?<kind>Id=`); an empty `q` lists them all. */
export const searchRoles = (owner: { kind: RoleOwnerKind; id: number }, q: string) =>
  account<RolePage>(`/roles?${owner.kind}Id=${owner.id}${q.trim() ? `&q=${term(q)}` : ""}&size=20`);

export const githubStart = () => accountSend<{ authorizeUrl: string }>("/me/contacts/github/start", "POST", { platform: "web" });
export const githubComplete = (code: string) => accountSend<Profile>("/me/contacts/github/complete", "POST", { code });
export const emailChallenge = (email: string) => accountSend<{ challengeId: string; expiresAt: string }>("/me/contacts/email/challenges", "POST", { email });
export const emailVerify = (challengeId: string, code: string) => accountSend<Profile>("/me/contacts/email/verify", "POST", { challengeId, code });

export const recommendationPreferences = () => account<RecommendationPreferences>("/me/recommendation-preferences");
export const eligibilityProfile = () => account<EligibilityProfile>("/me/eligibility-profile");
