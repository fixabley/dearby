"use client";
import Form from "next/form";
import Link from "next/link";
import { useEffect, useState } from "react";
import {
  type Activity,
  type ActivityPage,
  type ProgramPage,
  type ProgramSummary,
  type Tag,
  deadlineLabel,
  httpsUrl,
  recruitment,
  scheduleLabel,
} from "@/lib/models";
import { errorMessage, request } from "@/lib/client";
import { Empty, Failure, Loading } from "@/shared/ui/states";
import { Icon } from "@/shared/ui/icon";
import { ActivityCard } from "@/widgets/activity/activity-card";
import { groupByCategory } from "@/lib/category";

function programsPath(q: string, openOnly: boolean, tagIds: number[], cursor?: number) {
  const params = new URLSearchParams();
  if (q) params.set("q", q);
  if (!openOnly) params.set("openOnly", "false");
  // 태그 필터 (contract "태그"): every selected tag must match (AND).
  if (tagIds.length) params.set("tagIds", tagIds.join(","));
  if (cursor !== undefined) params.set("cursor", String(cursor));
  return `/discovery/programs?${params}`;
}

/** `&tagIds=1,2` when tags are selected, else nothing. */
function tagQuery(tagKey: string): string {
  return tagKey ? `&tagIds=${tagKey}` : "";
}

/**
 * Discovery as a program accordion (contract "발견 프로그램 묶음 — 2026-10-18"): programs with open activities,
 * searched by `q` (from the `/?q=` URL) and paged with the API cursor. Expanding a header loads that program's
 * open activities as the usual activity cards.
 */
export function Discovery({ q }: { q: string }) {
  const [page, setPage] = useState<ProgramPage>();
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const [loadingMore, setLoadingMore] = useState(false);
  const [moreError, setMoreError] = useState("");
  const [expanded, setExpanded] = useState<Set<number>>(new Set());
  // 모집 중만 보기 (contract "모집 중인 프로그램만 보기"): on by default; off also lists programs active in the last two years.
  const [openOnly, setOpenOnly] = useState(true);
  // Selected 얻을 수 있는 것 tags (contract "태그"); memory only.
  const [tagIds, setTagIds] = useState<number[]>([]);
  const tagKey = tagIds.join(",");
  useEffect(() => {
    const controller = new AbortController();
    request<ProgramPage>(programsPath(q, openOnly, tagIds), { signal: controller.signal })
      .then((result) => {
        setPage(result);
        // Auto-expand applies only while the toggle is on.
        setExpanded(new Set(openOnly ? result.items.filter(startsOpen).map((p) => p.id) : []));
      })
      .catch((error) => {
        if (!controller.signal.aborted) setError(errorMessage(error));
      });
    return () => controller.abort();
    // tagKey stands in for the tagIds array so a re-render with the same selection does not refetch.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [q, openOnly, tagKey, attempt]);
  function loadMore() {
    if (page?.nextCursor == null || loadingMore) return;
    setLoadingMore(true);
    setMoreError("");
    request<ProgramPage>(programsPath(q, openOnly, tagIds, page.nextCursor))
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
        모집 중만 보기
      </label>
      <TagFilter
        selected={tagIds}
        onChange={(next) => {
          setPage(undefined);
          setTagIds(next);
        }}
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
          <Empty title={openOnly ? "지금 모집 중인 프로그램이 없어요" : "최근 2년 안에 활동한 프로그램이 없어요"}>
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
                tagKey={tagKey}
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
 * Tag chip row under the search (contract "태그"): the dictionary from `GET /tags`, multi-select; a selection reloads
 * the program list with `tagIds`. Hidden while the dictionary is unavailable or empty.
 */
function TagFilter({ selected, onChange }: { selected: number[]; onChange: (next: number[]) => void }) {
  const [tags, setTags] = useState<Tag[]>([]);
  useEffect(() => {
    const controller = new AbortController();
    request<{ items: Tag[] }>("/tags", { signal: controller.signal })
      .then((result) => setTags(result.items))
      .catch(() => setTags([]));
    return () => controller.abort();
  }, []);
  if (!tags.length) return null;
  function toggle(id: number) {
    onChange(selected.includes(id) ? selected.filter((value) => value !== id) : [...selected, id]);
  }
  return (
    <ul className="tag-filter" aria-label="얻을 수 있는 것으로 거르기">
      {tags.map((tag) => {
        const on = selected.includes(tag.id);
        return (
          <li key={tag.id}>
            <button type="button" className={`tag-chip${on ? " selected" : ""}`} aria-pressed={on} onClick={() => toggle(tag.id)}>
              {tag.name}
            </button>
          </li>
        );
      })}
    </ul>
  );
}
/** Up to three tag chips and a `+n` remainder (contract "태그") for a program header. */
function TagChips({ tags }: { tags: Tag[] }) {
  if (!tags.length) return null;
  const shown = tags.slice(0, 3);
  const rest = tags.length - shown.length;
  return (
    <span className="tag-chips" aria-label="얻을 수 있는 것">
      {shown.map((tag) => (
        <span key={tag.id} className="badge neutral">
          {tag.name}
        </span>
      ))}
      {rest > 0 && <span className="badge neutral">+{rest}</span>}
    </span>
  );
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
const TWO_YEARS = 2 * 365 * 24 * 60 * 60 * 1000;

/** Recruitment start (timed, else the date at UTC midnight), or the activity start when there is no recruitment window; NaN without one. */
function upcomingKey(activity: Activity): number {
  const key = activity.application
    ? (activity.application.startAt ?? activity.application.startDate)
    : (activity.activitySchedule.startAt ?? activity.activitySchedule.startDate);
  return key ? Date.parse(key) : NaN;
}
/**
 * The other activities of a program without open ones (contract "모집 중인 프로그램만 보기"): `모집 예정` (recruitment or activity start
 * still ahead, nearest first) and `지난 활동` (activity start in the last two years, newest first); an empty group is omitted.
 */
function OtherActivities({ programId, now, tagKey }: { programId: number; now: number; tagKey: string }) {
  const [page, setPage] = useState<ActivityPage>();
  const [error, setError] = useState("");
  useEffect(() => {
    const controller = new AbortController();
    request<ActivityPage>(`/activities?programId=${programId}&size=20${tagQuery(tagKey)}`, { signal: controller.signal })
      .then(setPage)
      .catch((error) => {
        if (!controller.signal.aborted) setError(errorMessage(error));
      });
    return () => controller.abort();
  }, [programId, tagKey]);
  if (error) return <p className="muted">{error}</p>;
  if (!page) return <Loading />;
  const upcoming = page.items
    .filter((activity) => {
      const start = upcomingKey(activity);
      return !Number.isNaN(start) && start > now;
    })
    .sort((a, b) => upcomingKey(a) - upcomingKey(b));
  const past = page.items
    .filter((activity) => {
      if (upcoming.includes(activity)) return false;
      const start = activityStart(activity);
      return !Number.isNaN(start) && start >= now - TWO_YEARS && start <= now;
    })
    .sort((a, b) => activityStart(b) - activityStart(a));
  const groups = [
    { label: "모집 예정", items: upcoming },
    { label: "지난 활동", items: past },
  ].filter((group) => group.items.length);
  if (!groups.length) return null;
  return (
    <>
      {groups.map((group) => (
        <section key={group.label} className="category-group">
          <h3 className="category-heading">{group.label}</h3>
          <ul className="activity-list">
            {group.items.map((activity) => (
              <li key={activity.id}>
                <ActivityCard
                  id={activity.id}
                  name={activity.name}
                  description={activity.description}
                  organizationName={activity.organization.name}
                  statusLabel={recruitment(activity.application, now).label}
                  dateLabel={scheduleLabel(activity.activitySchedule)}
                  imageUrl={httpsUrl(activity.imageUrl)}
                  tags={activity.tags ?? []}
                />
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

/** One program header plus, when open, its open activities from `GET /activities?programId=&open=true` (the server decides what is open). */
function ProgramRow({
  program,
  open,
  toggle,
  tagKey,
}: {
  program: ProgramSummary;
  open: boolean;
  toggle: () => void;
  /** Selected tag ids as `1,2`; the expanded rows follow the same filter as the program list (alignment with the apps). */
  tagKey: string;
}) {
  const [activities, setActivities] = useState<ActivityPage>();
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const [now, setNow] = useState(Date.now);
  const panelId = `program-${program.id}-activities`;
  useEffect(() => {
    if (!open || activities) return;
    const controller = new AbortController();
    request<ActivityPage>(`/activities?programId=${program.id}&open=true&size=100${tagQuery(tagKey)}`, {
      signal: controller.signal,
    })
      .then(setActivities)
      .catch((error) => {
        if (!controller.signal.aborted) setError(errorMessage(error));
      });
    return () => controller.abort();
  }, [open, activities, program.id, attempt, tagKey]);
  useEffect(() => {
    if (!open) return;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [open]);
  const deadline = deadlineLabel(program.nearestRecruitmentEndAt);
  // The server already applied its open rule (contract "열린 활동 판정 일원화"); the client does not filter again.
  const openActivities = activities?.items;
  return (
    <li className="program">
      <button
        type="button"
        className="program-header"
        aria-expanded={open}
        aria-controls={panelId}
        onClick={toggle}
      >
        <span className="program-copy">
          <span className="eyebrow">{program.organization.name}</span>
          <span className="program-name">{program.name}</span>
          <span className="program-meta">
            {program.openActivityCount > 0 ? (
              <>
                모집 중 {program.openActivityCount}개
                {deadline && (
                  <>
                    <span className="dot">·</span>
                    {deadline}
                  </>
                )}
              </>
            ) : (
              <>최근 활동 {monthLabel(program.latestActivityAt ?? null)}</>
            )}
          </span>
          <TagChips tags={program.tags ?? []} />
        </span>
        <span className="program-chevron" aria-hidden="true">
          ›
        </span>
      </button>
      {open && (
        <div id={panelId} className="program-panel">
          {error ? (
            <Failure
              message={error}
              retry={() => {
                setError("");
                setAttempt((value) => value + 1);
              }}
            />
          ) : !openActivities ? (
            <Loading />
          ) : !openActivities.length ? (
            <>
              <p className="muted">지금 모집 중인 활동이 없어요.</p>
              <OtherActivities programId={program.id} now={now} tagKey={tagKey} />
            </>
          ) : (
            // Grouped by 활동 종류 (contract "활동 종류"); a single group needs no heading.
            groupByCategory(openActivities, (activity) => activity.category).map((group, _, groups) => (
              <section key={group.label} className="category-group">
                {groups.length > 1 && <h3 className="category-heading">{group.label}</h3>}
                <ul className="activity-list">
                  {[...group.items].sort(byDeadline).map((activity) => (
                    <li key={activity.id}>
                      <ActivityCard
                        id={activity.id}
                        name={activity.name}
                        description={activity.description}
                        organizationName={activity.organization.name}
                        statusLabel={recruitment(activity.application, now).label}
                        dateLabel={scheduleLabel(activity.activitySchedule)}
                        imageUrl={httpsUrl(activity.imageUrl)}
                        tags={activity.tags ?? []}
                      />
                    </li>
                  ))}
                </ul>
              </section>
            ))
          )}
        </div>
      )}
    </li>
  );
}
