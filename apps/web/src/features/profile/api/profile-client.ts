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
} from "@/lib/account-models";
import type { ActivityPage } from "@/lib/models";

export const getProfile = () => account<Profile>("/profile");
/** `If-Match` carries the version the form was loaded from; the API answers 409 when someone else saved since. */
export const putProfile = (body: ProfileRequest, version: number) => accountSend<Profile>("/profile", "PUT", body, { "If-Match": String(version) });

export const putPhoto = (file: File) => accountUpload<{ photoUrl: string }>("/profile/photo", "PUT", file);
export const deletePhoto = () => accountSend<void>("/profile/photo", "DELETE");

export const uploadEvidence = (historyId: string, file: File) => accountUpload<Evidence>(`/profile/histories/${historyId}/evidence`, "POST", file);
export const deleteEvidence = (historyId: string, evidenceId: string) => accountSend<void>(`/profile/histories/${historyId}/evidence/${evidenceId}`, "DELETE");

export type InfoKind = "organizations" | "programs" | "roles" | "jobs";
/** 이력·직무 입력의 선택 창 검색 (Bearer); `q` is trimmed and capped like the proxy expects. */
export const searchInfo = (kind: InfoKind, q: string) => account<ActivityInfoPage>(`/${kind}?q=${encodeURIComponent(q.trim().slice(0, 100))}&size=10`);
/** 활동 검색 uses the public list; items carry `organization`·`program` for the history's chain. */
export const searchActivities = (q: string) => request<ActivityPage>(`/activities?q=${encodeURIComponent(q.trim().slice(0, 100))}&size=10`);

export const githubStart = () => accountSend<{ authorizeUrl: string }>("/me/contacts/github/start", "POST", { platform: "web" });
export const githubComplete = (code: string) => accountSend<Profile>("/me/contacts/github/complete", "POST", { code });
export const emailChallenge = (email: string) => accountSend<{ challengeId: string; expiresAt: string }>("/me/contacts/email/challenges", "POST", { email });
export const emailVerify = (challengeId: string, code: string) => accountSend<Profile>("/me/contacts/email/verify", "POST", { challengeId, code });

export const recommendationPreferences = () => account<RecommendationPreferences>("/me/recommendation-preferences");
export const eligibilityProfile = () => account<EligibilityProfile>("/me/eligibility-profile");
