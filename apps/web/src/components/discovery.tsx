"use client";
import Form from "next/form";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import {
  type Activity,
  type ActivityPage,
  type ProgramPage,
  type ProgramSummary,
  type StatusGroup,
  STATUS_GROUPS,
  STATUS_GROUP_IDS,
  activityStatus,
  applicable,
  deadlineLabel,
  fieldTagsOf,
  groupStatuses,
  traitTagsOf,
  programLogoSrc,
  startLabel,
  recruitmentDate,
  scheduleLabel,
} from "@/lib/models";
import { track } from "@/shared/lib/analytics";
import { errorMessage, request } from "@/lib/client";
import { cn } from "@/shared/lib/utils";
import { Failure } from "@/shared/ui/states";
import { CenteredMessage } from "@/shared/ui/centered-message";
import { Icon } from "@/shared/ui/icon";
import { TagChipRow } from "@/shared/ui/tag-icon";
import { Button } from "@/shared/ui/button";
import { Card } from "@/shared/ui/card";
import { Input } from "@/shared/ui/input";
import { LargeTitle } from "@/shared/ui/nav-title";
import { Skeleton, SkeletonGroup } from "@/shared/ui/skeleton";
import { Segmented } from "@/shared/ui/segmented";
import { MultiSelect } from "@/shared/ui/multi-select";
import { ActivityCard, ActivityCardSkeleton } from "@/widgets/activity/activity-card";
import { ProgramAvatar } from "@/widgets/activity/program-avatar";
import { categoriesSummary, categoryLabels, groupByCategory, kindCategories } from "@/lib/category";
import { CategoryIcon } from "@/shared/ui/category-icon";

/**
 * 프로그램 | 이벤트 (contract "프로그램·이벤트 전환"): the server's `kind` filter — 이벤트 is 컨퍼런스·워크샵·밋업·강연, 프로그램 every other
 * 종류 and 미분류.
 */
export type Kind = "program" | "event";
const KIND_LABEL: Record<Kind, string> = { program: "프로그램", event: "이벤트" };
/** `?status=` value of the chosen 활동 상태 묶음; empty for the default `지원 가능` alone, which the URL leaves out. */
function statusParam(groups: StatusGroup[]): string {
  return groups.length === 1 && groups[0] === "available" ? "" : groups.join(",");
}
/**
 * `/` or `/?kind=event`, plus the search, the 활동 상태 묶음 and the 종류; all live in the URL so a search, 전체 보기 or a reload keeps
 * them (contract "활동 상태"·"종류 필터": 선택 기억은 웹 URL 쿼리). The 종류 belong to one side, so switching sides drops them.
 */
function homePath(kind: Kind, q = "", groups: StatusGroup[] = ["available"], categories: string[] = []) {
  const params = new URLSearchParams();
  if (q) params.set("q", q);
  if (kind === "event") params.set("kind", kind);
  if (statusParam(groups)) params.set("status", statusParam(groups));
  if (categories.length) params.set("category", categories.join(","));
  return params.size ? `/?${params}` : "/";
}
/** `지원 가능` · `지원 가능·진행 중` · `전체`. */
function groupsSummary(groups: StatusGroup[]): string {
  return groups.length === STATUS_GROUP_IDS.length ? "전체" : groups.map((group) => STATUS_GROUPS[group].label).join("·");
}

function programsPath(q: string, groups: StatusGroup[], kind: Kind, categories: string[], cursor?: number) {
  const params = new URLSearchParams();
  if (q) params.set("q", q);
  params.set("kind", kind);
  if (categories.length) params.set("category", categories.join(","));
  params.set("status", groupStatuses(groups).join(","));
  if (cursor !== undefined) params.set("cursor", String(cursor));
  return `/discovery/programs?${params}`;
}

/**
 * 발견 탭 as a program accordion (contract "발견 프로그램 묶음 — 2026-10-18"; the web has no sign-in, so no 추천 설정 퍼널 or
 * 맞춤 대기열 — contract "비로그인에서 만들기·추천을 막음"): programs with open activities,
 * searched by `q` (from the `/?q=` URL; tag names match too, there is no tag chip row — contract "태그 칩 줄 제거") and paged
 * with the API cursor. A card expands only when tapped — never on first load, search or coming back (contract
 * "태그 아이콘+라벨·분야 1개·자동 펼침 없음"); expanding a header loads that program's activities in the chosen 활동 상태 묶음
 * (`groups`, contract "활동 상태" 2026-10-26) as the usual activity cards.
 */
export function Discovery({ q, kind, groups, categories }: { q: string; kind: Kind; groups: StatusGroup[]; categories: string[] }) {
  const router = useRouter();
  const [page, setPage] = useState<ProgramPage>();
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const [loadingMore, setLoadingMore] = useState(false);
  const [moreError, setMoreError] = useState("");
  const [expanded, setExpanded] = useState<Set<number>>(new Set());
  // The groups as one value, so the effect reruns when the URL's `status` changes.
  const statusKey = groups.join(",");
  const categoryKey = categories.join(",");
  useEffect(() => {
    const controller = new AbortController();
    const chosen = categoryKey ? categoryKey.split(",") : [];
    request<ProgramPage>(programsPath(q, statusKey.split(",") as StatusGroup[], kind, chosen), { signal: controller.signal })
      .then(setPage)
      .catch((error) => {
        if (!controller.signal.aborted) setError(errorMessage(error));
      });
    return () => controller.abort();
  }, [q, statusKey, categoryKey, kind, attempt]);
  function loadMore() {
    if (page?.nextCursor == null || loadingMore) return;
    setLoadingMore(true);
    setMoreError("");
    request<ProgramPage>(programsPath(q, groups, kind, categories, page.nextCursor))
      .then((next) => {
        setPage((current) => ({
          items: [...(current?.items ?? []), ...next.items],
          nextCursor: next.nextCursor,
        }));
      })
      .catch((error) => setMoreError(errorMessage(error)))
      .finally(() => setLoadingMore(false));
  }
  /** The user changed the 종류 (사용 통계 `discovery_category_filter`, empty for 전체): reloads the list with them in the URL. */
  function chooseCategories(next: string[]) {
    if (next.join(",") === categoryKey) return;
    track("discovery_category_filter", { categories: next.map((code) => code.toLowerCase()).join(",") });
    setPage(undefined);
    setExpanded(new Set());
    router.replace(homePath(kind, q, groups, next), { scroll: false });
  }
  function toggle(id: number) {
    setExpanded((ids) => {
      const next = new Set(ids);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }
  return (
    <>
      <LargeTitle>활동 둘러보기</LargeTitle>
      <SearchField q={q} kind={kind} groups={groups} categories={categories} />
      <Segmented
        label="활동 묶음"
        className="mt-3"
        options={[
          { value: "program", label: KIND_LABEL.program },
          { value: "event", label: KIND_LABEL.event },
        ]}
        value={kind}
        onChange={(next) => next !== kind && router.replace(homePath(next, q, groups), { scroll: false })}
      />
      {/* 활동 상태 (contract "활동 상태" 2026-10-26, replacing 마감 숨기기): a multi-select row; none chosen falls back to 지원 가능. */}
      <MultiSelect
        label="활동 상태"
        summary={groupsSummary(groups)}
        className="mt-3 mb-2.5"
        options={STATUS_GROUP_IDS.map((id) => ({ value: id, label: STATUS_GROUPS[id].label }))}
        value={groups}
        onChange={(next) => {
          const chosen: StatusGroup[] = next.length ? next : ["available"];
          if (chosen.join(",") === statusKey) return;
          track("discovery_status_filter", { groups: chosen.join(",") });
          // Clear the list so the loading state shows while the other states load.
          setPage(undefined);
          setExpanded(new Set());
          router.replace(homePath(kind, q, chosen, categories), { scroll: false });
        }}
      />
      {/* 종류 (contract "종류 필터" 2026-10-26): this side's 종류 with their icons; none chosen is 전체. */}
      <MultiSelect
        label="종류"
        summary={categoriesSummary(categories)}
        className="mb-2.5"
        options={kindCategories(kind).map((code) => ({ value: code, label: categoryLabels[code], icon: <CategoryIcon code={code} /> }))}
        value={categories}
        onChange={(next) => chooseCategories(next)}
      />
      {error ? (
        <Failure
          message={error}
          retry={() => {
            setError("");
            setAttempt((value) => value + 1);
          }}
        />
      ) : !page ? (
        <ProgramsLoading />
      ) : !page.items.length ? (
        q ? (
          <CenteredMessage
            icon="search"
            title={`‘${q}’에 맞는 ${kind === "event" ? "이벤트가" : "프로그램이"} 없어요`}
            message={`다른 검색어로 찾아보거나 전체 ${kind === "event" ? "이벤트를" : "프로그램을"} 확인해 보세요.`}
            primary={{ label: `전체 ${KIND_LABEL[kind]} 보기`, href: homePath(kind, "", groups, categories) }}
          />
        ) : categories.length ? (
          <CenteredMessage
            icon="explore"
            title={`고른 조건의 ${kind === "event" ? "이벤트가" : "프로그램이"} 없어요`}
            message="다른 종류를 고르거나 종류 필터를 지워 보세요."
            primary={{ label: "종류 필터 지우기", onClick: () => chooseCategories([]) }}
          />
        ) : groups.includes("past") ? (
          <CenteredMessage
            icon="explore"
            title={kind === "event" ? "최근 3년 안에 열린 이벤트가 없어요" : "최근 3년 안에 활동한 프로그램이 없어요"}
            message="새로운 모집이 열리면 이곳에서 확인할 수 있어요. 잠시 후 다시 찾아와 주세요."
          />
        ) : (
          <CenteredMessage
            icon="explore"
            title={
              statusKey !== "available"
                ? `고른 조건의 ${kind === "event" ? "이벤트가" : "프로그램이"} 없어요`
                : kind === "event"
                  ? "지금 열린 이벤트가 없어요"
                  : "지금 모집 중인 프로그램이 없어요"
            }
            message="새로운 모집이 열리면 이곳에서 확인할 수 있어요. ‘지난 활동’도 고르면 최근 활동한 프로그램도 보여요."
            primary={{ label: "지난 활동도 보기", href: homePath(kind, q, [...groups, "past"]) }}
          />
        )
      ) : (
        <>
          {/* Keyed by the groups so each program loads its activities again for new ones. */}
          <ul key={`${statusKey}:${categoryKey}`} className="flex flex-col gap-2.5">
            {page.items.map((program) => (
              <ProgramRow
                key={program.id}
                program={program}
                open={expanded.has(program.id)}
                toggle={() => toggle(program.id)}
                groups={groups}
                kind={kind}
                categories={categories}
              />
            ))}
          </ul>
          {page.nextCursor != null && (
            <div className="mt-4 flex flex-col items-center gap-2">
              {moreError && (
                <p role="alert" className="text-footnote text-danger">
                  {moreError}
                </p>
              )}
              <Button variant="tinted" size="lg" onClick={loadMore} disabled={loadingMore}>
                {loadingMore ? "불러오는 중이에요" : moreError ? "다시 시도" : "더 보기"}
              </Button>
            </div>
          )}
        </>
      )}
      <aside className="mt-6">
        <Link href="/saved" className="flex min-h-11 items-center gap-3 rounded-[14px] bg-card px-4 py-3 active:opacity-70">
          <Icon name="card" tone="teal" />
          <span className="flex-1 text-subheadline break-keep">
            받은 명함 링크에서 공개 이력서를 보고, 로그인 없이 명함을 저장할 수 있어요.
          </span>
          <span className="text-subheadline whitespace-nowrap text-teal">
            저장한 명함 <span aria-hidden="true">›</span>
          </span>
        </Link>
      </aside>
    </>
  );
}

/**
 * Discovery search (contract "발견 검색·토글"): a stacked label over a system-looking `type="search"` field with a magnifier and a
 * clear button; Enter submits the form so `/?q=` stays the source of truth. Clearing an applied query returns to `/` (keeping `?kind=event`).
 */
function SearchField({ q, kind, groups, categories }: { q: string; kind: Kind; groups: StatusGroup[]; categories: string[] }) {
  const router = useRouter();
  const [value, setValue] = useState(q);
  return (
    <Form action="/" role="search" className="mt-1">
      <label className="sr-only" htmlFor="discovery-q">
        프로그램·조직·활동 검색
      </label>
      <div className="relative flex items-center">
        <span className="pointer-events-none absolute left-2 flex text-secondary-label">
          <Icon name="search" size="inline" />
        </span>
        <Input
          id="discovery-q"
          type="search"
          name="q"
          value={value}
          maxLength={100}
          placeholder="프로그램·활동·태그 검색"
          enterKeyHint="search"
          className="pr-10 pl-8"
          onChange={(event) => setValue(event.target.value)}
        />
        {kind === "event" && <input type="hidden" name="kind" value={kind} />}
        {statusParam(groups) && <input type="hidden" name="status" value={statusParam(groups)} />}
        {categories.length > 0 && <input type="hidden" name="category" value={categories.join(",")} />}
        {value && (
          <button
            type="button"
            aria-label="지우기"
            className="absolute right-0 grid size-11 place-items-center text-secondary-label"
            onClick={() => {
              setValue("");
              if (q) router.push(homePath(kind, "", groups, categories));
            }}
          >
            <Icon name="clear" size="inline" />
          </button>
        )}
      </div>
    </Form>
  );
}

/**
 * Props the renewed activity card needs (contract "발견 카드 리뉴얼"·"활동 상태"): the capsule is the server's status; 모집 정보 없음 has
 * none and its `모집` row reads `모집 일정 미정`; the row stands out only while 모집 중·모집 예정 and a period exists.
 */
function cardProps(activity: Activity, now: number) {
  const status = activityStatus(activity, now);
  // 모집 날짜 표기 (contract "모집 날짜 표기 — 2026-10-26"): `10.1 – 10.20` + `D-3` while open; an end alone keeps `~ … 마감`.
  const { text, dday: recruitmentDday } = recruitmentDate(activity.application, now);
  const recruitmentLabel = status === "NO_RECRUITMENT" ? "" : text;
  return {
    id: activity.id,
    name: activity.name,
    status,
    recruitmentLabel,
    recruitmentDday,
    recruitmentActive: applicable(status) && recruitmentLabel !== "",
    activityLabel: scheduleLabel(activity.activitySchedule, "activity", now),
    // 장소 이름만 (contract "활동 장소"); the collected phrase stands in when no place is curated.
    placeName: activity.place?.name ?? activity.location ?? null,
    fieldTag: fieldTagsOf(activity)[0],
    traitTags: traitTagsOf(activity),
  };
}

/** 모집일 순 (contract "활동 종류"): earlier recruitment deadline first, no deadline last, ties by name. */
function byDeadline(a: Activity, b: Activity): number {
  return deadline(a) - deadline(b) || a.name.localeCompare(b.name, "ko");
}
/** Sort key (contract "활동 종류"): the application end; when no deadline is known, the activity start stands in for it; neither → last. */
function deadline(activity: Activity): number {
  const key =
    activity.application?.endAt ??
    activity.application?.endDateExclusive ??
    activity.activitySchedule.startAt ??
    activity.activitySchedule.startDate;
  const instant = key ? Date.parse(key) : NaN;
  return Number.isNaN(instant) ? Infinity : instant;
}
/** `yyyy.M` of the latest activity start in Seoul time; `없음` when unknown. */
function monthLabel(value: string | null): string {
  const time = value ? Date.parse(value) : NaN;
  if (Number.isNaN(time)) return "없음";
  const parts = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul", year: "numeric", month: "numeric" }).formatToParts(new Date(time));
  const year = parts.find((p) => p.type === "year")?.value;
  const month = parts.find((p) => p.type === "month")?.value;
  return `${year}.${month}`;
}
/** Activity start as an instant (timed start, else the date at UTC midnight for a day-level comparison); NaN without one. */
function activityStart(activity: Activity): number {
  const key = activity.activitySchedule.startAt ?? activity.activitySchedule.startDate;
  return key ? Date.parse(key) : NaN;
}
/** Recruitment start (timed, else the date at UTC midnight); NaN without one. */
function upcomingKey(activity: Activity): number {
  const key = activity.application?.startAt ?? activity.application?.startDate;
  return key ? Date.parse(key) : NaN;
}
/** 지난 활동 window (contract "활동 상태": the server's `status` has no window on activities, so the page keeps the last three years). */
const THREE_YEARS = 1095 * 24 * 60 * 60 * 1000;
/** Latest activity start first; undated ones last. */
function latestFirst(a: Activity, b: Activity): number {
  return (activityStart(b) || -Infinity) - (activityStart(a) || -Infinity) || a.name.localeCompare(b.name, "ko");
}

/**
 * A program's activities by 활동 상태 for the chosen groups (contract "활동 상태"): 모집 중 (grouped by 활동 종류 on the page), 모집 예정
 * (nearest recruitment start first), 진행·예정 (진행 중·모집 마감·모집 정보 없음: running ones first, then the nearest activity start, undated
 * last) and 지난 활동 (종료 started within three years, undated ones kept, latest start first). An activity outside the groups (an older
 * server ignores `status`) is left out.
 */
function byStatus(items: Activity[], groups: StatusGroup[], now: number) {
  const of = (group: StatusGroup) =>
    groups.includes(group)
      ? items.filter((activity) => (STATUS_GROUPS[group].statuses as readonly string[]).includes(activityStatus(activity, now)))
      : [];
  const available = of("available");
  return {
    recruiting: available.filter((activity) => activityStatus(activity, now) === "RECRUITING"),
    upcoming: available
      .filter((activity) => activityStatus(activity, now) === "RECRUITING_UPCOMING")
      .sort((a, b) => (upcomingKey(a) || Infinity) - (upcomingKey(b) || Infinity) || a.name.localeCompare(b.name, "ko")),
    inProgress: of("in_progress").sort(
      (a, b) => (activityStart(a) || Infinity) - (activityStart(b) || Infinity) || a.name.localeCompare(b.name, "ko"),
    ),
    past: of("past")
      .filter((activity) => {
        const start = activityStart(activity);
        return Number.isNaN(start) || start >= now - THREE_YEARS;
      })
      .sort(latestFirst),
  };
}

/** One titled group of activity cards; nothing when empty. */
function StatusSection({ label, items, now }: { label: string; items: Activity[]; now: number }) {
  if (!items.length) return null;
  return (
    <section className="flex flex-col gap-2">
      <CategoryHeading>{label}</CategoryHeading>
      <ul className="flex flex-col gap-2">
        {items.map((activity) => (
          <li key={activity.id}>
            <ActivityCard {...cardProps(activity, now)} />
          </li>
        ))}
      </ul>
    </section>
  );
}

/**
 * One program card (contract "발견 카드 리뉴얼 — 2026-10-19"): logo or initial avatar, name · organization · one line of tag chips
 * (분야 1개 + 성격, contract "태그 아이콘+라벨·분야 1개·자동 펼침 없음") · `맞는 활동 n개` for an activity-name search hit, and on the right the
 * count badge (Σ`statusCounts`, older responses open + upcoming) that becomes an up-chevron `접기` button once expanded. The whole
 * header toggles. The body stays mounted so `grid-template-rows 0fr→1fr` can animate; its activities come from
 * `GET /activities?programId=&status=` (the server judges the state) on the first expansion.
 */
function ProgramRow({
  program,
  open,
  toggle,
  groups,
  kind,
  categories,
}: {
  program: ProgramSummary;
  open: boolean;
  toggle: () => void;
  /** 프로그램 | 이벤트, so an expanded program lists only that side's activities. */
  kind: Kind;
  /** The chosen 활동 상태 묶음: the same `status` as the list. */
  groups: StatusGroup[];
  /** The chosen 종류 (none: every 종류): the same `category` as the list. */
  categories: string[];
}) {
  const [activities, setActivities] = useState<ActivityPage>();
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const [now, setNow] = useState(Date.now);
  const panelId = `program-${program.id}-activities`;
  useEffect(() => {
    if (!open || activities) return;
    const controller = new AbortController();
    const status = groupStatuses(groups).join(",");
    const category = categories.length ? `&category=${categories.join(",")}` : "";
    request<ActivityPage>(`/activities?programId=${program.id}&kind=${kind}&status=${status}${category}&size=100`, {
      signal: controller.signal,
    })
      .then(setActivities)
      .catch((error) => {
        if (!controller.signal.aborted) setError(errorMessage(error));
      });
    return () => controller.abort();
  }, [open, activities, program.id, kind, groups, categories, attempt]);
  useEffect(() => {
    if (!open) return;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [open]);
  const logoSrc = programLogoSrc(program.logoUrl);
  const counts = program.statusCounts;
  const count = counts
    ? Object.values(counts).reduce((sum, value) => sum + value, 0)
    : program.openActivityCount + (program.upcomingActivityCount ?? 0);
  const upcomingCount = program.upcomingActivityCount ?? 0;
  const running = counts?.IN_PROGRESS ?? 0;
  // Meta line precedence: a deadline while something is open, else the next start, else `진행 중 n개` when only running ones are in scope
  // (contract "활동 상태"), else the latest activity month. An older server's activity-start stand-in (`nearestIsActivityStart`) is not a
  // deadline (user 2026-10-26: no recruitment schedule = 마감).
  const meta =
    (program.openActivityCount > 0 &&
      !program.nearestIsActivityStart &&
      deadlineLabel(program.nearestRecruitmentEndAt)) ||
    (upcomingCount > 0 && startLabel(program.nearestRecruitmentStartAt ?? null)) ||
    (program.openActivityCount === 0 && upcomingCount === 0 && running > 0 && `진행 중 ${running}개`) ||
    `최근 ${monthLabel(program.latestActivityAt ?? null)}`;
  const shown = activities && byStatus(activities.items, groups, now);
  // Content is kept while collapsed so the close animation has something to shrink; nothing is fetched until the first open.
  const body = error ? (
    <Failure
      compact
      message={error}
      retry={() => {
        setError("");
        setAttempt((value) => value + 1);
      }}
    />
  ) : !shown ? (
    open ? (
      <SkeletonGroup className="flex flex-col gap-2">
        <ActivityCardSkeleton />
        <ActivityCardSkeleton />
      </SkeletonGroup>
    ) : null
  ) : (
    <>
      {!shown.recruiting.length && !shown.upcoming.length && !shown.inProgress.length && !shown.past.length && (
        <p className="px-1.5 text-subheadline text-secondary-label">
          {groups.join(",") === "available" ? "지금 모집 중인 활동이 없어요." : "고른 상태의 활동이 없어요."}
        </p>
      )}
      {shown.recruiting.length > 0 &&
        // Grouped by 활동 종류 (contract "활동 종류"); a single group needs no heading.
        groupByCategory(shown.recruiting, (activity) => activity.category).map((group, _, categories) => (
          <section key={group.label} className="flex flex-col gap-2">
            {categories.length > 1 && (
              <CategoryHeading>
                {group.label} ({group.items.length})
              </CategoryHeading>
            )}
            <ul className="flex flex-col gap-2">
              {[...group.items].sort(byDeadline).map((activity) => (
                <li key={activity.id}>
                  <ActivityCard {...cardProps(activity, now)} />
                </li>
              ))}
            </ul>
          </section>
        ))}
      <StatusSection label="모집 예정" items={shown.upcoming} now={now} />
      <StatusSection label={STATUS_GROUPS.in_progress.label} items={shown.inProgress} now={now} />
      <StatusSection label="지난 활동" items={shown.past} now={now} />
    </>
  );
  return (
    <li>
      <Card>
        <div className="flex items-start gap-3 pt-3.5 pr-4 pb-3 pl-4">
          <button
            type="button"
            className="flex min-w-0 flex-1 items-start gap-3 text-left"
            aria-expanded={open}
            aria-controls={panelId}
            onClick={toggle}
          >
            <ProgramAvatar key={logoSrc ?? ""} name={program.name} src={logoSrc} />
            <span className="flex min-w-0 flex-1 flex-col gap-0.5">
              {/* 종류 줄 (contract "종류 아이콘 라벨" 2026-10-26): read first; none for 미분류 or an unknown code. */}
              {program.category && categoryLabels[program.category] && (
                <span className="flex items-center gap-1 text-caption text-secondary-label">
                  <CategoryIcon code={program.category} />
                  {categoryLabels[program.category]}
                </span>
              )}
              <span className="text-headline font-semibold break-keep">{program.name}</span>
              <span className="text-subheadline text-secondary-label">{program.organization.name}</span>
              <TagChipRow field={fieldTagsOf(program)[0]} traits={traitTagsOf(program)} className="mt-1.5" />
              {program.matchedActivityIds.length > 0 && (
                <span className="mt-1 text-footnote text-secondary-label">맞는 활동 {program.matchedActivityIds.length}개</span>
              )}
            </span>
          </button>
          <div className="flex shrink-0 flex-col items-end gap-1.5">
            {open ? (
              <button
                type="button"
                aria-label="접기"
                aria-controls={panelId}
                onClick={toggle}
                className="grid size-[34px] place-items-center rounded-full bg-mint text-teal"
              >
                <span className="flex rotate-180">
                  <Icon name="chevron-down" size="inline" />
                </span>
              </button>
            ) : count > 0 ? (
              <span className="grid h-[34px] min-w-[34px] place-items-center rounded-full bg-teal-fill px-3 text-subheadline font-semibold text-on-teal tabular-nums">
                <span className="sr-only">활동 </span>
                {count}
                <span className="sr-only">개</span>
              </span>
            ) : (
              <span className="h-[34px]" />
            )}
            <span className="text-caption2 whitespace-nowrap text-secondary-label">{meta}</span>
          </div>
        </div>
        {/* Kept mounted so `grid-template-rows 0fr→1fr` animates the height; `inert` while collapsed. */}
        <div
          id={panelId}
          aria-hidden={!open}
          inert={!open}
          className={cn(
            "grid transition-[grid-template-rows,opacity] duration-300 ease-out",
            open ? "grid-rows-[1fr] opacity-100" : "grid-rows-[0fr] opacity-0",
          )}
        >
          <div className="overflow-hidden">
            <div className="flex flex-col gap-2 px-2.5 pb-3">{body}</div>
          </div>
        </div>
      </Card>
    </li>
  );
}

/** Group heading inside an expanded program (iOS caption semibold, secondary). */
function CategoryHeading({ children }: { children: ReactNode }) {
  return <h2 className="px-1.5 pt-1.5 text-caption font-semibold text-secondary-label">{children}</h2>;
}

/** First load: three program-card placeholders, announced as loading. */
function ProgramsLoading() {
  return (
    <SkeletonGroup className="flex flex-col gap-2.5">
      {[0, 1, 2].map((key) => (
        <Card key={key} className="flex items-start gap-3 p-4">
          <Skeleton className="size-11 rounded-[14px]" />
          <div className="flex flex-1 flex-col gap-2 pt-1">
            <Skeleton className="h-3 w-1/5" />
            <Skeleton className="h-4 w-2/3" />
            <Skeleton className="h-3.5 w-1/3" />
          </div>
          <Skeleton className="h-[34px] w-[34px] rounded-full" />
        </Card>
      ))}
    </SkeletonGroup>
  );
}
