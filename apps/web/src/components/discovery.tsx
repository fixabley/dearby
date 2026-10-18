"use client";
import Form from "next/form";
import Link from "next/link";
import { useEffect, useState } from "react";
import {
  type ActivityPage,
  type ProgramPage,
  type ProgramSummary,
  deadlineLabel,
  httpsUrl,
  recruitment,
  scheduleLabel,
} from "@/lib/models";
import { errorMessage, request } from "@/lib/client";
import { Empty, Failure, Loading } from "@/shared/ui/states";
import { Icon } from "@/shared/ui/icon";
import { ActivityCard } from "@/widgets/activity/activity-card";

function programsPath(q: string, cursor?: number) {
  const params = new URLSearchParams();
  if (q) params.set("q", q);
  if (cursor !== undefined) params.set("cursor", String(cursor));
  return `/discovery/programs?${params}`;
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
  useEffect(() => {
    const controller = new AbortController();
    request<ProgramPage>(programsPath(q), { signal: controller.signal })
      .then((result) => {
        setPage(result);
        setExpanded(new Set(result.items.filter(startsOpen).map((p) => p.id)));
      })
      .catch((error) => {
        if (!controller.signal.aborted) setError(errorMessage(error));
      });
    return () => controller.abort();
  }, [q, attempt]);
  function loadMore() {
    if (page?.nextCursor == null || loadingMore) return;
    setLoadingMore(true);
    setMoreError("");
    request<ProgramPage>(programsPath(q, page.nextCursor))
      .then((next) => {
        setPage((current) => ({
          items: [...(current?.items ?? []), ...next.items],
          nextCursor: next.nextCursor,
        }));
        setExpanded((ids) => {
          const all = new Set(ids);
          next.items.filter(startsOpen).forEach((p) => all.add(p.id));
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
          <Empty title="지금 모집 중인 프로그램이 없어요">
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

/** Single open activity, or a search hit on an activity name, starts expanded. */
function startsOpen(program: ProgramSummary) {
  return program.openActivityCount === 1 || program.matchedActivityIds.length > 0;
}

/** One program header plus, when open, its open activities fetched from `GET /activities?programId=`. */
function ProgramRow({
  program,
  open,
  toggle,
}: {
  program: ProgramSummary;
  open: boolean;
  toggle: () => void;
}) {
  const [activities, setActivities] = useState<ActivityPage>();
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const [now, setNow] = useState(Date.now);
  const panelId = `program-${program.id}-activities`;
  useEffect(() => {
    if (!open || activities) return;
    const controller = new AbortController();
    request<ActivityPage>(`/activities?programId=${program.id}&size=100`, {
      signal: controller.signal,
    })
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
  const deadline = deadlineLabel(program.nearestRecruitmentEndAt);
  const openActivities = activities?.items.filter(
    (activity) => recruitment(activity.application, now).open,
  );
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
            열린 활동 {program.openActivityCount}개
            {deadline && (
              <>
                <span className="dot">·</span>
                {deadline}
              </>
            )}
          </span>
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
            <p className="muted">지금 모집 중인 활동이 없어요.</p>
          ) : (
            <ul className="activity-list">
              {openActivities.map((activity) => (
                <li key={activity.id}>
                  <ActivityCard
                    id={activity.id}
                    name={activity.name}
                    description={activity.description}
                    organizationName={activity.organization.name}
                    statusLabel={recruitment(activity.application, now).label}
                    dateLabel={scheduleLabel(activity.activitySchedule)}
                    imageUrl={httpsUrl(activity.imageUrl)}
                  />
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </li>
  );
}
