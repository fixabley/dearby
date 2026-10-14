"use client";
import Form from "next/form";
import Link from "next/link";
import { useEffect, useState } from "react";
import {
  type ActivityPage,
  httpsUrl,
  recruitment,
  scheduleLabel,
} from "@/lib/models";
import { errorMessage, request } from "@/lib/client";
import { Empty, Failure, Loading } from "@/shared/ui/states";
import { Icon } from "@/shared/ui/icon";
import { ActivityCard } from "@/widgets/activity/activity-card";

function activitiesPath(q: string, cursor?: number) {
  const params = new URLSearchParams();
  if (q) params.set("q", q);
  if (cursor !== undefined) params.set("cursor", String(cursor));
  return `/activities?${params}`;
}

/** Server search by `q` (from the `/?q=` URL), appended page by page with the API cursor. */
export function Discovery({ q }: { q: string }) {
  const [page, setPage] = useState<ActivityPage>();
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const [loadingMore, setLoadingMore] = useState(false);
  const [moreError, setMoreError] = useState("");
  const [now, setNow] = useState(Date.now);
  useEffect(() => {
    const controller = new AbortController();
    request<ActivityPage>(activitiesPath(q), { signal: controller.signal })
      .then(setPage)
      .catch((error) => {
        if (!controller.signal.aborted) setError(errorMessage(error));
      });
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => {
      controller.abort();
      clearInterval(timer);
    };
  }, [q, attempt]);
  function loadMore() {
    if (page?.nextCursor == null || loadingMore) return;
    setLoadingMore(true);
    setMoreError("");
    request<ActivityPage>(activitiesPath(q, page.nextCursor))
      .then((next) =>
        setPage((current) => ({
          items: [...(current?.items ?? []), ...next.items],
          nextCursor: next.nextCursor,
        })),
      )
      .catch((error) => setMoreError(errorMessage(error)))
      .finally(() => setLoadingMore(false));
  }
  return (
    <>
      <div className="page-heading">
        <p className="eyebrow">새로운 경험, 새로운 연결</p>
        <h1>활동 탐색</h1>
        <p className="muted">
          관심 있는 활동을 찾아보고, 다음 경험을 시작해 보세요.
        </p>
      </div>
      <Form action="/" className="search" role="search">
        <input
          type="search"
          name="q"
          defaultValue={q}
          maxLength={100}
          placeholder="활동 이름이나 설명으로 찾기"
          aria-label="활동 검색"
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
          <Empty title={`‘${q}’에 맞는 활동이 없어요`}>
            <p>다른 검색어로 찾아보거나 전체 활동을 확인해 보세요.</p>
            <Link className="text-link" href="/">
              전체 활동 보기
            </Link>
          </Empty>
        ) : (
          <Empty title="아직 공개된 활동이 없어요">
            <p>
              새로운 활동이 공개되면 이곳에서 확인할 수 있어요.
              <br />
              잠시 후 다시 찾아와 주세요.
            </p>
          </Empty>
        )
      ) : (
        <>
          <ul className="activity-list">
            {page.items.map((activity) => (
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
          공유받은 명함 링크에서 공개 이력서를 보고,
          <br className="mobile-break" /> 로그인 없이 명함을 저장할 수 있어요.
        </p>
        <Link href="/saved">
          저장한 명함 <span aria-hidden="true">→</span>
        </Link>
      </aside>
    </>
  );
}
