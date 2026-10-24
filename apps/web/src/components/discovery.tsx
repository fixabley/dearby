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
  deadlineLabel,
  fieldTagsOf,
  traitTagsOf,
  programLogoSrc,
  startLabel,
  recruitment,
  scheduleLabel,
} from "@/lib/models";
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
import { Switch } from "@/shared/ui/switch";
import { ActivityCard, ActivityCardSkeleton, type StatusTone } from "@/widgets/activity/activity-card";
import { ProgramAvatar } from "@/widgets/activity/program-avatar";
import { groupByCategory } from "@/lib/category";
import { isEmpty, useRecommendationPreferences } from "@/lib/recommendation";
import { RecommendationFunnel } from "@/components/recommendation-funnel";

function programsPath(q: string, openOnly: boolean, cursor?: number) {
  const params = new URLSearchParams();
  if (q) params.set("q", q);
  if (!openOnly) params.set("openOnly", "false");
  if (cursor !== undefined) params.set("cursor", String(cursor));
  return `/discovery/programs?${params}`;
}

/**
 * 발견 탭 (contract "추천 설정 퍼널"): the first visit without a query opens the recommendation funnel; afterwards the program
 * list with the `오늘의 맞춤 대기열` entry on top. Nothing but the loading shape renders until this browser's saved settings are read, so the
 * list never flashes before the funnel.
 */
export function Discovery({ q }: { q: string }) {
  const preferences = useRecommendationPreferences();
  const [funnel, setFunnel] = useState<"first" | "edit">();
  if (preferences === null && !q && !funnel) setFunnel("first");
  if (preferences === undefined)
    return (
      <>
        <LargeTitle>활동 둘러보기</LargeTitle>
        <ProgramsLoading />
      </>
    );
  if (funnel)
    return (
      <RecommendationFunnel
        initial={funnel === "edit" ? preferences : null}
        onClose={() => {
          window.scrollTo(0, 0);
          setFunnel(undefined);
        }}
      />
    );
  return (
    <Programs
      q={q}
      top={
        !q && preferences ? (
          <QueueEntry
            onFunnel={(mode) => {
              window.scrollTo(0, 0);
              setFunnel(mode);
            }}
            skipped={isEmpty(preferences)}
          />
        ) : null
      }
    />
  );
}

/**
 * 발견 탭 맨 위 `오늘의 맞춤 대기열` 한 줄 (contract "맞춤 대기열(스토리형 쇼츠)", 2026-10-25 — 추천 묶음 대신): opens the queue, or
 * the funnel first when it was skipped (its result opens the queue). `설정` re-opens the funnel with the saved picks.
 */
function QueueEntry({ skipped, onFunnel }: { skipped: boolean; onFunnel: (mode: "first" | "edit") => void }) {
  const label = (
    <>
      <Icon name="play" tone="teal" />
      <span className="flex flex-1 flex-col">
        <span className="text-body font-semibold">오늘의 맞춤 대기열</span>
        <span className="text-footnote text-secondary-label">나에게 맞는 활동을 한 장씩 넘겨 봐요</span>
      </span>
    </>
  );
  const row = "flex min-h-11 flex-1 items-center gap-3 px-4 py-3 text-left active:opacity-70";
  return (
    <Card className="mb-4 flex items-center">
      {skipped ? (
        <button type="button" onClick={() => onFunnel("first")} className={row}>
          {label}
        </button>
      ) : (
        <Link href="/queue" className={row}>
          {label}
        </Link>
      )}
      {!skipped && (
        <Button variant="plain" size="sm" onClick={() => onFunnel("edit")} aria-label="추천 설정" className="mr-1">
          설정
        </Button>
      )}
    </Card>
  );
}

/**
 * Discovery as a program accordion (contract "발견 프로그램 묶음 — 2026-10-18"): programs with open activities,
 * searched by `q` (from the `/?q=` URL; tag names match too, there is no tag chip row — contract "태그 칩 줄 제거") and paged
 * with the API cursor. A card expands only when tapped — never on first load, search, recommendations or coming back (contract
 * "태그 아이콘+라벨·분야 1개·자동 펼침 없음"); expanding a header loads that program's open activities as the usual activity cards.
 */
function Programs({ q, top }: { q: string; top: ReactNode }) {
  const [page, setPage] = useState<ProgramPage>();
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const [loadingMore, setLoadingMore] = useState(false);
  const [moreError, setMoreError] = useState("");
  const [expanded, setExpanded] = useState<Set<number>>(new Set());
  // 마감 숨기기 (contract "마감 숨기기"): on by default hides only closed activities (open + upcoming programs stay); off also lists programs active in the last three years and shows 지난 활동 under every expanded program.
  const [openOnly, setOpenOnly] = useState(true);
  useEffect(() => {
    const controller = new AbortController();
    request<ProgramPage>(programsPath(q, openOnly), { signal: controller.signal })
      .then(setPage)
      .catch((error) => {
        if (!controller.signal.aborted) setError(errorMessage(error));
      });
    return () => controller.abort();
  }, [q, openOnly, attempt]);
  function loadMore() {
    if (page?.nextCursor == null || loadingMore) return;
    setLoadingMore(true);
    setMoreError("");
    request<ProgramPage>(programsPath(q, openOnly, page.nextCursor))
      .then((next) => {
        setPage((current) => ({
          items: [...(current?.items ?? []), ...next.items],
          nextCursor: next.nextCursor,
        }));
      })
      .catch((error) => setMoreError(errorMessage(error)))
      .finally(() => setLoadingMore(false));
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
      <SearchField q={q} />
      {/* 마감 숨기기 as an inset-grouped switch row; the whole row (the label) toggles (contract "발견 검색·토글"). */}
      <label
        htmlFor="open-only"
        className="mt-3 mb-2.5 flex min-h-11 cursor-pointer items-center justify-between gap-3 rounded-[14px] bg-card px-4 py-1.5"
      >
        <span className="text-body">마감 숨기기</span>
        <Switch
          id="open-only"
          checked={openOnly}
          onCheckedChange={(checked) => {
            // Clear the list so the loading state shows while the other mode loads.
            setPage(undefined);
            setOpenOnly(checked);
          }}
        />
      </label>
      {top}
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
            title={`‘${q}’에 맞는 프로그램이 없어요`}
            message="다른 검색어로 찾아보거나 전체 프로그램을 확인해 보세요."
            primary={{ label: "전체 프로그램 보기", href: "/" }}
          />
        ) : (
          <CenteredMessage
            icon="explore"
            title={openOnly ? "지금 모집 중인 프로그램이 없어요" : "최근 3년 안에 활동한 프로그램이 없어요"}
            message="새로운 모집이 열리면 이곳에서 확인할 수 있어요. 잠시 후 다시 찾아와 주세요."
          />
        )
      ) : (
        <>
          <ul className="flex flex-col gap-2.5">
            {page.items.map((program) => (
              <ProgramRow
                key={program.id}
                program={program}
                open={expanded.has(program.id)}
                toggle={() => toggle(program.id)}
                openOnly={openOnly}
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
 * clear button; Enter submits the form so `/?q=` stays the source of truth. Clearing an applied query returns to `/`.
 */
function SearchField({ q }: { q: string }) {
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
        {value && (
          <button
            type="button"
            aria-label="지우기"
            className="absolute right-0 grid size-11 place-items-center text-secondary-label"
            onClick={() => {
              setValue("");
              if (q) router.push("/");
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
 * Props the renewed activity card needs (contract "발견 카드 리뉴얼"): the chip tone comes from the group the caller placed the
 * activity in (open list → live recruitment state; 모집 예정 group → soon; 지난 활동 → closed), and the `모집` row is emphasised
 * only while recruitment is not past and a period exists.
 */
function cardProps(activity: Activity, statusTone: StatusTone, now: number) {
  const recruitmentLabel = scheduleLabel(activity.application, "recruitment", now);
  return {
    id: activity.id,
    name: activity.name,
    statusTone,
    recruitmentLabel,
    recruitmentActive: statusTone !== "closed" && recruitmentLabel !== "",
    activityLabel: scheduleLabel(activity.activitySchedule, "activity", now),
    // 장소 이름만 (contract "활동 장소"); the collected phrase stands in when no place is curated.
    placeName: activity.place?.name ?? activity.location ?? null,
    fieldTag: fieldTagsOf(activity)[0],
    traitTags: traitTagsOf(activity),
  };
}
/** Chip tone for an activity the server lists as open: 모집 예정 before its start, 마감 once passed, otherwise 모집 중. */
function openTone(activity: Activity, now: number): StatusTone {
  const { label } = recruitment(activity.application, now);
  return label === "모집 예정" ? "soon" : label === "모집 마감" ? "closed" : "open";
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
/** 지난 활동 window (contract "모집 중인 프로그램만 보기", 2026-10-19 저녁: 3년). */
const THREE_YEARS = 1095 * 24 * 60 * 60 * 1000;

/** Recruitment start (timed, else the date at UTC midnight), or the activity start when there is no recruitment window; NaN without one. */
function upcomingKey(activity: Activity): number {
  const key = activity.application
    ? (activity.application.startAt ?? activity.application.startDate)
    : (activity.activitySchedule.startAt ?? activity.activitySchedule.startDate);
  return key ? Date.parse(key) : NaN;
}
/**
 * The other activities of a program (contract "모집 중인 프로그램만 보기"): `모집 예정` (recruitment or activity start still ahead,
 * nearest first) and `지난 활동` (activity start in the last three years, newest first); an empty group is omitted. Activities
 * already listed as open (`excludeIds`) are left out so the toggle-off view does not repeat them.
 */
function OtherActivities({
  page,
  now,
  excludeIds,
  showPast,
  onEmpty,
}: {
  /** Every activity of the program, fetched by the row together with its open ones. */
  page: ActivityPage;
  now: number;
  excludeIds?: Set<number>;
  /** 지난 활동 only while 마감 숨기기 is off; 모집 예정 always (contract "펼침", 2026-10-19 밤). */
  showPast: boolean;
  /** Rendered when no group has items (the caller shows nothing else). */
  onEmpty?: ReactNode;
}) {
  const others = page.items.filter((activity) => !excludeIds?.has(activity.id));
  const upcoming = others
    .filter((activity) => {
      const start = upcomingKey(activity);
      return !Number.isNaN(start) && start > now;
    })
    .sort((a, b) => upcomingKey(a) - upcomingKey(b));
  const past = others
    .filter((activity) => {
      if (upcoming.includes(activity)) return false;
      const start = activityStart(activity);
      return !Number.isNaN(start) && start >= now - THREE_YEARS && start <= now;
    })
    .sort((a, b) => activityStart(b) - activityStart(a));
  const groups = (
    [
      { label: "모집 예정", tone: "soon", items: upcoming },
      { label: "지난 활동", tone: "closed", items: showPast ? past : [] },
    ] satisfies { label: string; tone: StatusTone; items: Activity[] }[]
  ).filter((group) => group.items.length);
  if (!groups.length) return <>{onEmpty ?? null}</>;
  return (
    <>
      {groups.map((group) => (
        <section key={group.label} className="flex flex-col gap-2">
          <CategoryHeading>{group.label}</CategoryHeading>
          <ul className="flex flex-col gap-2">
            {group.items.map((activity) => (
              <li key={activity.id}>
                <ActivityCard {...cardProps(activity, group.tone, now)} />
              </li>
            ))}
          </ul>
        </section>
      ))}
    </>
  );
}

/**
 * One program card (contract "발견 카드 리뉴얼 — 2026-10-19"): logo or initial avatar, name · organization · one line of tag chips
 * (분야 1개 + 성격, contract "태그 아이콘+라벨·분야 1개·자동 펼침 없음") · `맞는 활동 n개` for an activity-name search hit, and on the right the
 * open + upcoming count badge that becomes an up-chevron `접기` button once expanded. The whole header toggles. The body stays
 * mounted so `grid-template-rows 0fr→1fr` can animate; its open activities come from `GET /activities?programId=&open=true`
 * (the server decides what is open) on the first expansion.
 */
function ProgramRow({
  program,
  open,
  toggle,
  openOnly,
}: {
  program: ProgramSummary;
  open: boolean;
  toggle: () => void;
  /** 마감 숨기기 state: when off, 모집 예정·지난 활동 are shown under the open activities as well (contract "펼침"). */
  openOnly: boolean;
}) {
  // The open activities and every activity (for 모집 예정·지난 활동) load together, so one loading state covers both.
  const [activities, setActivities] = useState<{ open: ActivityPage; all: ActivityPage }>();
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const [now, setNow] = useState(Date.now);
  const panelId = `program-${program.id}-activities`;
  useEffect(() => {
    if (!open || activities) return;
    const controller = new AbortController();
    const { signal } = controller;
    Promise.all([
      request<ActivityPage>(`/activities?programId=${program.id}&open=true&size=100`, { signal }),
      request<ActivityPage>(`/activities?programId=${program.id}&size=20`, { signal }),
    ])
      .then(([open, all]) => setActivities({ open, all }))
      .catch((error) => {
        if (!controller.signal.aborted) setError(errorMessage(error));
      });
    return () => controller.abort();
  }, [open, activities, program.id, attempt]);
  useEffect(() => {
    if (!open) return;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [open]);
  const logoSrc = programLogoSrc(program.logoUrl);
  const count = program.openActivityCount + (program.upcomingActivityCount ?? 0);
  // Meta line precedence: a deadline while something is open, else the next start, else the latest activity month.
  const meta =
    (program.openActivityCount > 0 && deadlineLabel(program.nearestRecruitmentEndAt)) ||
    ((program.upcomingActivityCount ?? 0) > 0 && startLabel(program.nearestRecruitmentStartAt ?? null)) ||
    `최근 ${monthLabel(program.latestActivityAt ?? null)}`;
  // The server already applied its open rule (contract "열린 활동 판정 일원화"); the client does not filter again.
  const openActivities = activities?.open.items;
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
  ) : !activities || !openActivities ? (
    // Both lists arrive together, so the empty-state line never shows before the second one (contract "빈 상태는 모두 받은 뒤에만").
    open ? (
      <SkeletonGroup className="flex flex-col gap-2">
        <ActivityCardSkeleton />
        <ActivityCardSkeleton />
      </SkeletonGroup>
    ) : null
  ) : (
    <>
      {openActivities.length > 0 &&
        // Grouped by 활동 종류 (contract "활동 종류"); a single group needs no heading.
        groupByCategory(openActivities, (activity) => activity.category).map((group, _, groups) => (
          <section key={group.label} className="flex flex-col gap-2">
            {groups.length > 1 && (
              <CategoryHeading>
                {group.label} ({group.items.length})
              </CategoryHeading>
            )}
            <ul className="flex flex-col gap-2">
              {[...group.items].sort(byDeadline).map((activity) => (
                <li key={activity.id}>
                  <ActivityCard {...cardProps(activity, openTone(activity, now), now)} />
                </li>
              ))}
            </ul>
          </section>
        ))}
      {/* 모집 예정 always; 지난 활동 only while 마감 숨기기 is off (contract "펼침", 2026-10-19 밤). */}
      <OtherActivities
        page={activities.all}
        now={now}
        excludeIds={new Set(openActivities.map((activity) => activity.id))}
        showPast={!openOnly}
        onEmpty={
          !openActivities.length ? (
            <p className="px-1.5 text-subheadline text-secondary-label">지금 모집 중인 활동이 없어요.</p>
          ) : null
        }
      />
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
            <Skeleton className="h-4 w-2/3" />
            <Skeleton className="h-3.5 w-1/3" />
          </div>
          <Skeleton className="h-[34px] w-[34px] rounded-full" />
        </Card>
      ))}
    </SkeletonGroup>
  );
}
