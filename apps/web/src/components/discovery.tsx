"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { type Catalog, isRecruiting } from "@/lib/models";
import { errorMessage, request } from "@/lib/client";
import { Empty, Failure, Loading } from "@/shared/ui/states";
import { Icon } from "@/shared/ui/icon";
import { ActivityCard } from "@/widgets/activity/activity-card";
export function Discovery() {
  const [catalog, setCatalog] = useState<Catalog>();
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const [filter, setFilter] = useState("all");
  const [now, setNow] = useState(Date.now);
  useEffect(() => {
    const controller = new AbortController();
    request<Catalog>("/catalog", { signal: controller.signal })
      .then(setCatalog)
      .catch((error) => {
        if (!controller.signal.aborted) setError(errorMessage(error));
      });
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => {
      controller.abort();
      clearInterval(timer);
    };
  }, [attempt]);
  const activities =
    catalog?.activities.filter(
      (activity) =>
        isRecruiting(activity, now) &&
        (filter === "all" || activity.participationType === filter),
    ) ?? [];
  return (
    <>
      <div className="page-heading">
        <p className="eyebrow">새로운 경험, 새로운 연결</p>
        <h1>모집 중인 활동</h1>
        <p className="muted">
          관심 있는 활동을 만나고, 다음 경험을 시작해 보세요.
        </p>
      </div>
      <div className="chips" aria-label="참여 방식">
        {[
          ["all", "전체"],
          ["registration", "바로 신청"],
          ["selection", "선발형"],
        ].map(([value, label]) => (
          <button
            key={value}
            aria-pressed={filter === value}
            onClick={() => setFilter(value)}
          >
            {label}
          </button>
        ))}
      </div>
      {error ? (
        <Failure
          message={error}
          retry={() => {
            setError("");
            setAttempt((value) => value + 1);
          }}
        />
      ) : !catalog ? (
        <Loading />
      ) : !activities.length ? (
        <Empty title="지금은 모집 중인 활동이 없어요">
          <p>
            새로운 활동이 공개되면 이곳에서 확인할 수 있어요.
            <br />
            잠시 후 다시 찾아와 주세요.
          </p>
        </Empty>
      ) : (
        <ul className="activity-list">
          {activities.map((activity) => (
            <li key={activity.id}>
              <ActivityCard
                id={activity.id}
                title={activity.title}
                summary={activity.summary}
                organizationName={
                  catalog.organizations.find(
                    (org) => org.id === activity.organizationId,
                  )?.name ?? "주최 정보 미확인"
                }
                participationLabel={
                  activity.participationType === "registration"
                    ? "바로 신청"
                    : "선발형"
                }
                dateLabel={activity.dateLabel}
                location={activity.location}
              />
            </li>
          ))}
        </ul>
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
