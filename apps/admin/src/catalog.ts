import type { Criteria } from "./criteria";
export type Organization = { id: string; name: string; description: string };
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
  source_note: string;
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
export function discoveryStatus(
  activity: Activity,
  now = Date.now(),
): { label: string; color: string } {
  if (activity.publication_status !== "published")
    return {
      label: publicationLabels[activity.publication_status],
      color: "default",
    };
  const start = activity.recruitment_start_at
    ? Date.parse(activity.recruitment_start_at)
    : null;
  const end = activity.recruitment_end_at
    ? Date.parse(activity.recruitment_end_at)
    : null;
  if (end !== null && now >= end)
    return { label: "마감 · 탐색 제외", color: "default" };
  const checked = Date.parse(activity.source_checked_at ?? "");
  const until = Date.parse(activity.valid_until ?? "");
  if (
    activity.freshness !== "verified" ||
    !(checked <= now && now < until && until <= checked + 86400000)
  )
    return { label: "재확인 필요", color: "orange" };
  if (
    activity.recruitment_status === "open" &&
    (start === null || start <= now)
  )
    return { label: "탐색 노출", color: "cyan" };
  return {
    label:
      activity.recruitment_status === "open" && start !== null && start > now
        ? "모집 예정"
        : recruitmentLabels[activity.recruitment_status],
    color: "default",
  };
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
      label: "구독 차단 · 전체 일시정지",
      hint: "Mac의 Codex 로그인·구독 한도를 확인한 뒤 재시도하세요. 해소 전까지 모든 프로그램 수집이 멈춥니다.",
    };
  if (/^BLOCKED: (Official|All candidates)/.test(error ?? ""))
    return {
      label: "원문 차단 · 이 프로그램만",
      hint: "공식 원문의 호스트·크기(1 MB 이하)·JS 전용 여부를 확인하고 출처를 고친 뒤 재시도하세요.",
    };
  if (error?.startsWith("BLOCKED: Configure official hosts"))
    return {
      label: "설정 필요 · 이 프로그램만",
      hint: "프로그램 관리에서 공식 출처 호스트를 입력한 뒤 재시도하세요.",
    };
  return { label: "조치 필요", hint: "" };
}
