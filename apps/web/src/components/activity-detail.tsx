"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { type Catalog, isRecruiting, safeWebUrl } from "@/lib/models";
import { errorMessage, request } from "@/lib/client";
import { Failure, Loading } from "@/shared/ui/states";
import { Icon } from "@/shared/ui/icon";
export function ActivityDetail({ id }: { id: string }) {
  const [catalog, setCatalog] = useState<Catalog>();
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
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
  if (error)
    return (
      <Failure
        message={error}
        retry={() => {
          setError("");
          setAttempt((value) => value + 1);
        }}
      />
    );
  if (!catalog) return <Loading />;
  const activity = catalog.activities.find(
    (item) => item.id.toLowerCase() === id,
  );
  if (!activity)
    return (
      <div className="state">
        <h1>활동을 찾을 수 없어요</h1>
        <Link className="button secondary" href="/">
          활동 탐색하기
        </Link>
      </div>
    );
  const open = isRecruiting(activity, now);
  const official = safeWebUrl(activity.officialUrl);
  const application = safeWebUrl(activity.applicationUrl);
  return (
    <article className="detail">
      <Link className="back-link" href="/">
        <Icon name="arrow" size={20} />
        활동 탐색
      </Link>
      <div className="detail-heading">
        <p className="eyebrow">
          {catalog.organizations.find(
            (org) => org.id === activity.organizationId,
          )?.name ?? "주최 정보 미확인"}
        </p>
        <span className={`badge ${open ? "" : "neutral"}`}>
          {open ? "모집 중" : "현재 모집 여부 확인 필요"}
        </span>
        <h1>{activity.title}</h1>
        <p className="lead">{activity.summary}</p>
      </div>
      <section className="detail-section">
        <h2>활동 안내</h2>
        <dl className="facts">
          {[
            [
              "참여 방식",
              activity.participationType === "registration"
                ? "바로 신청"
                : "선발형",
            ],
            ["대상", activity.audience],
            ["지원 자격", activity.qualification],
            ["역할", activity.roles.join(", ")],
            ["비용", activity.cost],
            ["장소", activity.location],
          ].map(([label, value]) => (
            <div key={label}>
              <dt>{label}</dt>
              <dd>{value || "미확인"}</dd>
            </div>
          ))}
        </dl>
      </section>
      <section className="detail-section">
        <h2>일정</h2>
        {activity.schedules.length ? (
          <ul className="schedule-list">
            {activity.schedules.map((schedule) => (
              <li key={schedule.id}>
                <Icon name="calendar" />
                <div>
                  <h3>{schedule.title}</h3>
                  <p>{schedule.dateLabel || "일정 미확인"}</p>
                </div>
              </li>
            ))}
          </ul>
        ) : (
          <p>{activity.dateLabel || "일정 미확인"}</p>
        )}
      </section>
      <section className="detail-section source">
        <h2>공식 출처</h2>
        <p>
          {activity.sourceNote ||
            "신청 조건과 최신 일정은 공식 사이트에서 확인해 주세요."}
        </p>
        <p className="muted">
          확인 시각:{" "}
          {activity.sourceCheckedAt
            ? new Date(activity.sourceCheckedAt).toLocaleString("ko-KR", {
                timeZone: "Asia/Seoul",
              }) + " (한국 시간)"
            : "미확인"}
        </p>
        {official && (
          <a
            className="text-link"
            href={official}
            target="_blank"
            rel="noopener noreferrer"
          >
            공식 안내 보기 <Icon name="external" size={16} />
          </a>
        )}
      </section>
      <div className="detail-action">
        <p className="muted">
          신청은 공식 사이트에서 진행돼요.
          <br />
          웹에서는 개인 캘린더의 겹치는 시간을 확인할 수 없어요.
        </p>
        {open && application ? (
          <a
            className="button"
            href={application}
            target="_blank"
            rel="noopener noreferrer"
          >
            공식 사이트에서 신청하기 <Icon name="external" size={18} />
          </a>
        ) : official ? (
          <a
            className="button secondary"
            href={official}
            target="_blank"
            rel="noopener noreferrer"
          >
            공식 사이트에서 모집 확인 <Icon name="external" size={18} />
          </a>
        ) : (
          <p role="status">연결 가능한 공식 링크가 없어요.</p>
        )}
      </div>
    </article>
  );
}
