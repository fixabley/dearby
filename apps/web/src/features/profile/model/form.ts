// 프로필 수정 폼 (contract "프로필과 버전 이력", "연락처 아이콘·바로가기" › 사용자명 입력, "이력"): the editable copy of a Profile, the
// username rules the input applies while typing, the client-side checks that mirror the API's 400s, and the PUT body.
import type { Profile, ProfileJob, ProfileRequest } from "@/lib/account-models";
import { type Card, type ContactKind, type RoleOwnerKind, contactUsername } from "@/lib/models";

export type ContactForm = { id: string; kind: ContactKind; label: string; value: string; verified: boolean };
export type JobForm = { id: string; jobId?: number; jobName: string; approval?: ProfileJob["approval"]; approvalReason?: string | null };
/**
 * 이력 = 역할 매칭 (contract "이력 (History) — 역할 매칭"): 어디에서(귀속 단위, 고르면 `ownerId`, 없으면 `ownerKind`와 이름 직접 입력) →
 * 역할(그 단위의 역할이면 `roleId`, 없으면 이름 직접 입력) → 기간 → 설명.
 */
export type HistoryForm = {
  id: string;
  ownerKind: RoleOwnerKind;
  ownerId?: number;
  ownerName: string;
  roleId?: number;
  roleName: string;
  startDate: string;
  endDate: string;
  description: string;
};
export type ProfileForm = { name: string; introLink: string; jobs: JobForm[]; contacts: ContactForm[]; histories: HistoryForm[] };

export const CONTACT_KIND_NAME: Record<ContactKind, string> = {
  phone: "전화",
  email: "이메일",
  kakao: "카카오톡",
  instagram: "Instagram",
  github: "GitHub",
  behance: "Behance",
};
/** GitHub·Instagram·Behance·카카오톡 take a username (contract "연락처 아이콘·바로가기"); the prefix shows before the input. */
export const USERNAME_INPUT: Partial<Record<ContactKind, { prefix?: string; host?: string; max: number }>> = {
  github: { prefix: "github.com/", host: "github.com", max: 39 },
  instagram: { prefix: "instagram.com/", host: "instagram.com", max: 30 },
  behance: { prefix: "behance.net/", host: "behance.net", max: 64 },
  kakao: { max: 20 },
};
/**
 * What the input keeps while typing or pasting: a profile URL of that service becomes its username, `@` and spaces go, only
 * letters·digits·`.`·`_`·`-` stay, cut to the service's length. A URL pasted into the 카카오톡 field is not an ID → empty.
 */
export function sanitizeUsername(kind: ContactKind, raw: string): string {
  const rule = USERNAME_INPUT[kind];
  if (!rule) return raw;
  let text = raw.trim();
  if (rule.host) {
    const prefix = new RegExp(`^(https?://)?((www|m)\\.)?${rule.host.replace(".", "\\.")}/`, "i").exec(text);
    if (prefix) text = text.slice(prefix[0].length).split(/[/?#]/)[0];
  } else if (/^(https?:\/\/|[\w.-]+\.[a-z]{2,}\/)/i.test(text)) return "";
  return text.replace(/[\s@]/g, "").replace(/[^A-Za-z0-9._-]/g, "").slice(0, rule.max);
}

export function fromProfile(profile: Profile): ProfileForm {
  return {
    name: profile.name,
    introLink: profile.introLink ?? "",
    jobs: profile.jobs.map((job) => ({
      id: job.id,
      jobId: job.job?.id,
      jobName: job.job?.name ?? job.jobName ?? "",
      approval: job.approval,
      approvalReason: job.approvalReason,
    })),
    contacts: profile.contacts.map((contact) => ({
      id: contact.id,
      kind: contact.kind,
      label: contact.label,
      // A profile URL saved before 2026-10-26 is shown as its username; an old 카카오톡 open-chat URL stays as it is.
      value: contactUsername(contact) ?? contact.value,
      verified: contact.verified ?? false,
    })),
    histories: profile.histories.map((history) => ({
      id: history.id,
      ownerKind: history.owner.kind,
      ownerId: history.owner.id ?? undefined,
      ownerName: history.owner.name,
      roleId: history.role?.id,
      roleName: history.role?.name ?? history.roleName,
      startDate: history.startDate,
      endDate: history.endDate ?? "",
      description: history.description,
    })),
  };
}

/** The API's rules, checked before sending so the message appears next to the field; the API still decides. */
export function validate(form: ProfileForm): string | undefined {
  if (!form.name.trim()) return "이름을 입력해 주세요.";
  if (form.introLink.trim() && !/^(https:\/\/)?[^\s/]+\.[^\s/]+(\/\S*)?$/i.test(form.introLink.trim())) return "소개 링크는 https 주소만 쓸 수 있습니다";
  if (form.jobs.some((job) => !job.jobId && !job.jobName.trim())) return "직무 이름을 입력해 주세요.";
  const phones = form.contacts.filter((contact) => contact.kind === "phone");
  const emails = form.contacts.filter((contact) => contact.kind === "email");
  if (!phones.length || !emails.length) return "전화와 이메일을 각각 하나 이상 넣어 주세요.";
  for (const contact of form.contacts) {
    const value = contact.value.trim();
    if (!value) return `${CONTACT_KIND_NAME[contact.kind]} 값을 입력해 주세요.`;
    if (contact.kind === "phone" && !(/^[\d+\-\s]+$/.test(value) && /^\d{8,15}$/.test(value.replace(/\D/g, "")))) return "전화번호는 숫자 8~15자리로 입력해 주세요.";
    if (contact.kind === "email" && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) return "이메일 형식을 확인해 주세요.";
  }
  for (const history of form.histories) {
    if (!history.ownerId && !history.ownerName.trim()) return "이력의 조직·프로그램·활동을 고르거나 입력해 주세요.";
    if (!history.roleId && !history.roleName.trim()) return "이력의 역할을 고르거나 입력해 주세요.";
    if (!history.startDate) return "이력의 시작일을 입력해 주세요.";
    if (history.endDate && history.endDate < history.startDate) return "종료일은 시작일보다 앞설 수 없어요.";
  }
}

/**
 * `PUT /v1/profile` body: ids win over names; the API normalizes usernames again. A history is `{roleId}` (the role knows its unit)
 * or the direct input `{ownerKind, ownerId | ownerName, roleName}` (contract "이력 (History) — 역할 매칭").
 */
export function toRequest(form: ProfileForm): ProfileRequest {
  return {
    name: form.name.trim(),
    introLink: form.introLink.trim() || null,
    jobs: form.jobs.map((job) => ({ id: job.id, ...(job.jobId ? { jobId: job.jobId } : { jobName: job.jobName.trim() }) })),
    contacts: form.contacts.map(({ id, kind, label, value }) => ({ id, kind, label: label.trim(), value: value.trim() })),
    histories: form.histories.map((history) => ({
      id: history.id,
      ...(history.roleId
        ? { roleId: history.roleId }
        : { ownerKind: history.ownerKind, ...(history.ownerId ? { ownerId: history.ownerId } : { ownerName: history.ownerName.trim() }), roleName: history.roleName.trim() }),
      startDate: history.startDate,
      endDate: history.endDate || null,
      description: history.description,
    })),
  };
}

/** 보기 모드 = 명함 디자인 (contract "프로필 읽기/수정 모드"): the profile in the card face's shape. */
export function profileToCard(profile: Profile, photo?: string): Card {
  return {
    id: profile.id,
    name: "",
    description: "",
    profileName: profile.name,
    job: profile.job,
    introLink: profile.introLink ?? null,
    contacts: profile.contacts,
    histories: profile.histories,
    createdAt: profile.updatedAt,
    photoUrl: profile.photoUrl ?? null,
    photo,
  };
}

/** `재학 중 · 3학년 · 2003년생` (contract "지원 자격 정보" 행 보조 글자), or undefined when nothing is saved. */
export function eligibilitySummary(e: { educationStatus: string | null; employment: string | null; schoolYear: number | null; birthYear: number | null }): string | undefined {
  const education: Record<string, string> = { enrolled: "재학 중", onLeave: "휴학 중", graduating: "졸업 예정", graduated: "졸업했어요" };
  const employment: Record<string, string> = { employed: "직장에 다녀요", unemployed: "미취업", founder: "창업" };
  const parts = [
    e.educationStatus ? education[e.educationStatus] : e.employment ? employment[e.employment] : undefined,
    e.schoolYear ? (e.schoolYear >= 5 ? "5학년 이상" : `${e.schoolYear}학년`) : undefined,
    e.birthYear ? `${e.birthYear}년생` : undefined,
  ].filter(Boolean);
  return parts.length ? parts.join(" · ") : undefined;
}

export function newId(): string {
  return crypto.randomUUID();
}
