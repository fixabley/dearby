"use client";
import Form from "next/form";
import Link from "next/link";
import { useEffect, useState, type ReactNode } from "react";
import {
  type Activity,
  type ActivityPage,
  type ProgramPage,
  type ProgramSummary,
  type Tag,
  deadlineLabel,
  programLogoSrc,
  startLabel,
  recruitment,
  scheduleLabel,
} from "@/lib/models";
import { errorMessage, request } from "@/lib/client";
import { Empty, Failure, Loading } from "@/shared/ui/states";
import { Icon } from "@/shared/ui/icon";
import { ActivityCard, type StatusTone } from "@/widgets/activity/activity-card";
import { groupByCategory } from "@/lib/category";

function programsPath(q: string, openOnly: boolean, cursor?: number) {
  const params = new URLSearchParams();
  if (q) params.set("q", q);
  if (!openOnly) params.set("openOnly", "false");
  if (cursor !== undefined) params.set("cursor", String(cursor));
  return `/discovery/programs?${params}`;
}

/**
 * Discovery as a program accordion (contract "발견 프로그램 묶음 — 2026-10-18"): programs with open activities,
 * searched by `q` (from the `/?q=` URL; tag names match too, there is no tag chip row — contract "태그 칩 줄 제거") and paged
 * with the API cursor. Expanding a header loads that program's open activities as the usual activity cards.
 */
export function Discovery({ q }: { q: string }) {
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
      .then((result) => {
        setPage(result);
        // Auto-expand applies only while the toggle is on.
        setExpanded(new Set(openOnly ? result.items.filter(startsOpen).map((p) => p.id) : []));
      })
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
        setExpanded((ids) => {
          const all = new Set(ids);
          if (openOnly) next.items.filter(startsOpen).forEach((p) => all.add(p.id));
          return all;
        });
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
      <div className="page-heading">
        <p className="eyebrow">새로운 경험, 새로운 연결</p>
        <h1>활동 탐색</h1>
        <p className="muted">
          프로그램을 펼쳐 지금 모집 중인 활동을 찾아보세요.
        </p>
      </div>
      <Form action="/" className="search" role="search">
        <input
          type="search"
          name="q"
          defaultValue={q}
          maxLength={100}
          placeholder="프로그램, 조직, 활동 이름으로 찾기"
          aria-label="프로그램 검색"
        />
        <button className="button" type="submit">
          검색
        </button>
      </Form>
      <label className="open-only">
        <input
          type="checkbox"
          checked={openOnly}
          onChange={(event) => {
            // Clear the list so the loading state shows while the other mode loads.
            setPage(undefined);
            setOpenOnly(event.target.checked);
          }}
        />
        마감 숨기기
      </label>
      {error ? (
        <Failure
          message={error}
          retry={() => {
            setError("");
            setAttempt((value) => value + 1);
          }}
        />
      ) : !page ? (
        <Loading />
      ) : !page.items.length ? (
        q ? (
          <Empty title={`‘${q}’에 맞는 프로그램이 없어요`}>
            <p>다른 검색어로 찾아보거나 전체 프로그램을 확인해 보세요.</p>
            <Link className="text-link" href="/">
              전체 프로그램 보기
            </Link>
          </Empty>
        ) : (
          <Empty title={openOnly ? "지금 모집 중인 프로그램이 없어요" : "최근 3년 안에 활동한 프로그램이 없어요"}>
            <p>
              새로운 모집이 열리면 이곳에서 확인할 수 있어요.
              <br />
              잠시 후 다시 찾아와 주세요.
            </p>
          </Empty>
        )
      ) : (
        <>
          <ul className="program-list">
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
            <div className="load-more">
              {moreError && <p role="alert">{moreError}</p>}
              <button
                className="button secondary"
                onClick={loadMore}
                disabled={loadingMore}
              >
                {loadingMore
                  ? "불러오는 중이에요"
                  : moreError
                    ? "다시 시도"
                    : "더 보기"}
              </button>
            </div>
          )}
        </>
      )}
      <aside className="inline-note">
        <Icon name="card" />
        <p>
          받은 명함 링크에서 공개 이력서를 보고,
          <br className="mobile-break" /> 로그인 없이 명함을 저장할 수 있어요.
        </p>
        <Link href="/saved">
          저장한 명함 <span aria-hidden="true">→</span>
        </Link>
      </aside>
    </>
  );
}

/**
 * 44px logo slot (contract "프로그램 로고"): the program logo through the same-origin proxy, else — null, older response,
 * or a failed load — the initial-letter avatar. Keyed by `src` upstream so a new `v` retries after a failure.
 */
function ProgramAvatar({ name, src }: { name: string; src: string | undefined }) {
  const [failed, setFailed] = useState(false);
  return (
    <span className="program-avatar" aria-hidden="true">
      {src && !failed ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={src} alt="" loading="lazy" decoding="async" onError={() => setFailed(true)} />
      ) : (
        ([...name.trim()][0] ?? "?")
      )}
    </span>
  );
}
/** Up to three tag chips and a `+n` remainder (contract "태그") under the program name; nothing without tags. */
function TagChips({ tags }: { tags: Tag[] }) {
  if (!tags.length) return null;
  const shown = tags.slice(0, 3);
  const rest = tags.length - shown.length;
  return (
    <span className="program-tags" aria-label="얻을 수 있는 것">
      {shown.map((tag) => (
        <span key={tag.id} className="program-tag">
          {tag.name}
        </span>
      ))}
      {rest > 0 && <span className="program-tag more">+{rest}</span>}
    </span>
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
  };
}
/** Chip tone for an activity the server lists as open: 모집 예정 before its start, 마감 once passed, otherwise 모집 중. */
function openTone(activity: Activity, now: number): StatusTone {
  const { label } = recruitment(activity.application, now);
  return label === "모집 예정" ? "soon" : label === "모집 마감" ? "closed" : "open";
}

/** Single open activity, or a search hit on an activity name, starts expanded. */
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
  programId,
  now,
  excludeIds,
  showPast,
  onEmpty,
}: {
  programId: number;
  now: number;
  excludeIds?: Set<number>;
  /** 지난 활동 only while 마감 숨기기 is off; 모집 예정 always (contract "펼침", 2026-10-19 밤). */
  showPast: boolean;
  /** Rendered when no group has items (the caller shows nothing else). */
  onEmpty?: ReactNode;
}) {
  const [page, setPage] = useState<ActivityPage>();
  const [error, setError] = useState("");
  useEffect(() => {
    const controller = new AbortController();
    request<ActivityPage>(`/activities?programId=${programId}&size=20`, { signal: controller.signal })
      .then(setPage)
      .catch((error) => {
        if (!controller.signal.aborted) setError(errorMessage(error));
      });
    return () => controller.abort();
  }, [programId]);
  if (error) return <p className="muted">{error}</p>;
  if (!page) return <Loading />;
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
        <section key={group.label} className="category-group">
          <h3 className="category-heading">{group.label}</h3>
          <ul className="activity-list">
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
function startsOpen(program: ProgramSummary) {
  return program.openActivityCount === 1 || program.matchedActivityIds.length > 0;
}

/**
 * One program card (contract "발견 카드 리뉴얼 — 2026-10-19"): logo or initial avatar, name · organization · tag chips, and on the right the
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
  const [activities, setActivities] = useState<ActivityPage>();
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const [now, setNow] = useState(Date.now);
  const panelId = `program-${program.id}-activities`;
  useEffect(() => {
    if (!open || activities) return;
    const controller = new AbortController();
    request<ActivityPage>(`/activities?programId=${program.id}&open=true&size=100`, { signal: controller.signal })
      .then(setActivities)
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
  const openActivities = activities?.items;
  // Content is kept while collapsed so the close animation has something to shrink; nothing is fetched until the first open.
  const body = error ? (
    <Failure
      message={error}
      retry={() => {
        setError("");
        setAttempt((value) => value + 1);
      }}
    />
  ) : !openActivities ? (
    open ? <Loading /> : null
  ) : (
    <>
      {openActivities.length > 0 &&
        // Grouped by 활동 종류 (contract "활동 종류"); a single group needs no heading.
        groupByCategory(openActivities, (activity) => activity.category).map((group, _, groups) => (
          <section key={group.label} className="category-group">
            {groups.length > 1 && <h3 className="category-heading">{group.label}</h3>}
            <ul className="activity-list">
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
        programId={program.id}
        now={now}
        excludeIds={new Set(openActivities.map((activity) => activity.id))}
        showPast={!openOnly}
        onEmpty={!openActivities.length ? <p className="muted">지금 모집 중인 활동이 없어요.</p> : null}
      />
    </>
  );
  return (
    <li className="program" data-open={open || undefined}>
      <div className="program-header">
        <button type="button" className="program-toggle" aria-expanded={open} aria-controls={panelId} onClick={toggle}>
          <ProgramAvatar key={logoSrc ?? ""} name={program.name} src={logoSrc} />
          <span className="program-copy">
            <span className="program-name">{program.name}</span>
            <span className="program-org">{program.organization.name}</span>
            <TagChips tags={program.tags ?? []} />
          </span>
        </button>
        <div className="program-side">
          {open ? (
            <button type="button" className="program-collapse" aria-label="접기" aria-controls={panelId} onClick={toggle}>
              <svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M6 9l6 6 6-6" />
              </svg>
            </button>
          ) : (
            <span className="program-count" aria-hidden={count === 0}>
              {count > 0 ? (
                <>
                  <span className="sr-only">활동 </span>
                  <span>{count}</span>
                  <span className="sr-only">개</span>
                </>
              ) : null}
            </span>
          )}
          <span className="program-meta">{meta}</span>
        </div>
      </div>
      <div id={panelId} className="program-body" aria-hidden={!open} inert={!open}>
        <div>
          <div className="program-inner">{body}</div>
        </div>
      </div>
    </li>
  );
}
