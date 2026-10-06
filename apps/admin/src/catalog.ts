import type { Criteria } from "./criteria";
export type Organization = {
  id: string;
  name: string;
  description: string;
  /** Absent until the catalog_organization_tree migration is applied. */
  parent_id?: string | null;
};
export const maxOrganizationDepth = 4;
/** Root → id chain. Stops on a cycle, which the database already forbids. */
export function organizationPath(
  organizations: Organization[],
  id: string | null,
): Organization[] {
  const byId = new Map(organizations.map((o) => [o.id, o]));
  const path: Organization[] = [];
  let o = id ? byId.get(id) : undefined;
  while (o && !path.includes(o)) {
    path.unshift(o);
    o = o.parent_id ? byId.get(o.parent_id) : undefined;
  }
  return path;
}
function subtreeHeight(organizations: Organization[], id: string): number {
  const children = organizations.filter((o) => o.parent_id === id);
  return (
    1 + Math.max(0, ...children.map((c) => subtreeHeight(organizations, c.id)))
  );
}
/** Valid parents: not itself or a descendant, and the moved subtree stays within four levels. */
export function parentCandidates(
  organizations: Organization[],
  id?: string,
): Organization[] {
  const height = id ? subtreeHeight(organizations, id) : 1;
  return organizations.filter((candidate) => {
    const path = organizationPath(organizations, candidate.id);
    return (
      !path.some((o) => o.id === id) &&
      path.length + height <= maxOrganizationDepth
    );
  });
}
export type Program = {
  collection_enabled: boolean;
  collection_hosts: string[];
  id: string;
  organization_id: string;
  title: string;
  description: string;
};
export type Schedule = {
  id: string;
  title: string;
  startAt: string | null;
  endAt: string | null;
  dateLabel: string;
  timeZone: string;
};
export type Activity = {
  id: string;
  program_id: string;
  organization_id: string;
  title: string;
  summary: string;
  participation_type: "registration" | "selection";
  recruitment_status: "open" | "scheduled" | "closed" | "unknown";
  publication_status: "draft" | "published" | "hidden";
  recruitment_start_at: string | null;
  recruitment_end_at: string | null;
  date_label: string;
  location: string | null;
  cost: string | null;
  audience: string | null;
  qualification: string | null;
  roles: string[];
  criteria: Criteria;
  schedules: Schedule[];
  official_url: string;
  application_url: string | null;
  image_url: string | null;
  source_checked_at: string | null;
  valid_until: string | null;
  freshness: "verified" | "stale" | "unavailable";
  /** Internal verification evidence; never shown to visitors. */
  source_note: string;
  /** Shown to visitors as the confirmation note (web falls back to default text when empty). */
  public_note: string;
  updated_at: string;
};
export const publicationLabels = {
  draft: "초안",
  published: "게시",
  hidden: "숨김",
};
export const recruitmentLabels = {
  open: "모집 중",
  scheduled: "모집 예정",
  closed: "모집 마감",
  unknown: "미확인",
};
/**
 * Recruitment status from the period (2026-10-06 user decision, mirrors the API's atTime):
 * an administrator's "closed" always wins, then the dates decide; without dates the stored choice stands.
 */
export function inferredRecruitment(
  activity: Pick<
    Activity,
    "recruitment_status" | "recruitment_start_at" | "recruitment_end_at"
  >,
  now = Date.now(),
): { status: Activity["recruitment_status"]; byPeriod: boolean } {
  const start = Date.parse(activity.recruitment_start_at ?? "");
  const end = Date.parse(activity.recruitment_end_at ?? "");
  if (activity.recruitment_status === "closed")
    return { status: "closed", byPeriod: false };
  if (now >= end) return { status: "closed", byPeriod: true };
  if (now < start) return { status: "scheduled", byPeriod: true };
  if (start || end) return { status: "open", byPeriod: true };
  return { status: activity.recruitment_status, byPeriod: false };
}
/** Discovery shows an activity only when published, verified within 24 hours and recruiting now. */
export function discoveryStatus(
  activity: Activity,
  now = Date.now(),
): { label: string; color: string } {
  const hidden = (reason: string, color = "default") => ({
    label: `미노출 · ${reason}`,
    color,
  });
  if (activity.publication_status !== "published")
    return hidden(publicationLabels[activity.publication_status]);
  const { status } = inferredRecruitment(activity, now);
  if (status === "closed") return hidden("마감");
  const checked = Date.parse(activity.source_checked_at ?? "");
  const until = Date.parse(activity.valid_until ?? "");
  if (
    activity.freshness !== "verified" ||
    !(checked <= now && now < until && until <= checked + 86400000)
  )
    return hidden(
      activity.source_checked_at ? "공식 확인 만료" : "공식 확인 없음",
      "orange",
    );
  if (status === "open") return { label: "발견 노출 중", color: "cyan" };
  return hidden(recruitmentLabels[status]);
}
export function dateText(value: string | null | undefined) {
  return value
    ? new Intl.DateTimeFormat("ko-KR", {
        dateStyle: "medium",
        timeStyle: "short",
      }).format(new Date(value))
    : "미확인";
}
export function normalizeSchedules(schedules: Schedule[]): Schedule[] {
  return schedules.map((item) => {
    const start = item.startAt?.trim() || null,
      end = item.endAt?.trim() || null;
    for (const value of [start, end])
      if (
        value &&
        (!/(Z|[+-]\d{2}:\d{2})$/.test(value) ||
          !Number.isFinite(Date.parse(value)))
      )
        throw new Error(
          "일정 시각에 시간대가 필요합니다. 예: 2026-10-24T14:00:00+09:00",
        );
    if (start && end && Date.parse(start) >= Date.parse(end))
      throw new Error("일정 종료는 시작보다 늦어야 합니다.");
    try {
      new Intl.DateTimeFormat("ko-KR", { timeZone: item.timeZone }).format();
    } catch {
      throw new Error("올바른 일정 시간대를 입력해 주세요.");
    }
    return {
      ...item,
      id: item.id || crypto.randomUUID(),
      startAt: start,
      endAt: end,
    };
  });
}
/** Blocked collection jobs share status=blocked; the worker's error prefix says what to fix. */
export function collectionBlock(error: string | null) {
  if (error?.startsWith("BLOCKED: Codex subscription"))
    return {
      kind: "subscription",
      label: "구독 차단 · 전체 일시정지",
      hint: "Mac의 Codex 로그인·구독 한도를 확인한 뒤 재시도하세요. 해소 전까지 모든 프로그램 수집이 멈춥니다.",
    };
  if (/^BLOCKED: (Official|All candidates)/.test(error ?? ""))
    return {
      kind: "source",
      label: "원문 차단 · 이 프로그램만",
      hint: "공식 원문의 호스트·크기(3 MB 이하)·JS 전용 여부를 확인하고 출처를 고친 뒤 재시도하세요.",
    };
  if (
    /^BLOCKED: (Configure official hosts|Source must use a configured official HTTPS host)/.test(
      error ?? "",
    )
  )
    return {
      kind: "setup",
      label: "설정 필요 · 이 프로그램만",
      hint: "프로그램 관리에서 공식 출처 호스트를 입력하거나 고친 뒤 재시도하세요. 원문은 등록된 HTTPS 호스트여야 합니다.",
    };
  return { kind: "other", label: "조치 필요", hint: "" };
}

/** One row per activity (catalog_activity_evidence): the quote the worker re-checks and its last result. */
export type ActivityEvidence = {
  activity_id: string;
  quote: string;
  verified_at: string;
  last_check_at: string | null;
  last_check_ok: boolean | null;
  last_error: string | null;
};
/** Mirrors the database rule, plus Markdown symbols that a copied passage never contains. */
export function evidenceQuoteError(value: string) {
  const quote = value.trim();
  if (!quote) return null;
  if ([...quote].length < 20 || [...quote].length > 200)
    return "구절은 20~200자로 입력해 주세요.";
  if (/[\r\n]/.test(quote)) return "줄바꿈 없이 한 문장으로 입력해 주세요.";
  if (/…|\.\.\./.test(quote)) return "생략부호(… 또는 ...)는 쓸 수 없어요.";
  if (/[#*`>|~[\]]/.test(quote))
    return "마크다운 기호(# * ` > | ~ [ ])는 빼고 원문 그대로 붙여 넣어 주세요.";
  return null;
}
function remaining(until: number, now: number) {
  const minutes = Math.floor((until - now) / 60000);
  return `만료까지 ${Math.floor(minutes / 60)}시간 ${minutes % 60}분`;
}
/** Automatic re-check only extends a published activity whose verification is still valid. */
export function recheckState(
  activity: Activity,
  evidence: ActivityEvidence | undefined,
  now = Date.now(),
): { label: string; color: string; detail: string } | null {
  if (activity.publication_status !== "published") return null;
  const until = Date.parse(activity.valid_until ?? "");
  if (activity.freshness !== "verified" || !(until > now))
    return {
      label: "수동 확인 필요",
      color: "red",
      detail: activity.source_checked_at
        ? "공식 확인이 만료되어 자동 재확인으로 연장되지 않아요."
        : "공식 확인 기록이 없어요. 확인을 기록해야 발견에 노출돼요.",
    };
  const expiry = remaining(until, now);
  if (!evidence)
    return {
      label: "수동 확인 필요",
      color: "red",
      detail: `재확인 기준 구절이 없어 자동 재확인 대상이 아니에요 · ${expiry}`,
    };
  if (evidence.last_check_ok === null)
    return { label: "자동 재확인 대기", color: "default", detail: expiry };
  if (evidence.last_check_ok)
    return {
      label: "자동 재확인 성공",
      color: "cyan",
      detail: `${dateText(evidence.last_check_at)} · ${expiry}`,
    };
  const blocked = evidence.last_error?.startsWith("BLOCKED: ");
  const kind = collectionBlock(evidence.last_error).kind;
  return {
    label: "수동 확인 필요",
    color: "red",
    detail: `${
      blocked
        ? `재확인 불가${kind === "source" ? "(원문 차단)" : kind === "setup" ? "(호스트 설정 필요)" : ""}`
        : "자동 재확인 실패 · 1시간 뒤 다시 시도"
    }: ${evidence.last_error ?? "사유 없음"} · ${expiry}`,
  };
}
