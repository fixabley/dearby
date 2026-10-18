"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import {
  type Activity,
  httpsUrl,
  recruitment,
  scheduleLabel,
} from "@/lib/models";
import { eligibilityLines } from "@/lib/eligibility";
import { errorMessage, request, RequestError } from "@/lib/client";
import { Failure, Loading } from "@/shared/ui/states";
import { Icon } from "@/shared/ui/icon";

export function ActivityDetail({ id }: { id: number }) {
  const [activity, setActivity] = useState<Activity | null>();
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const [now, setNow] = useState(Date.now);
  useEffect(() => {
    const controller = new AbortController();
    request<Activity>(`/activities/${id}`, { signal: controller.signal })
      .then(setActivity)
      .catch((error) => {
        if (controller.signal.aborted) return;
        if (error instanceof RequestError && error.status === 404) setActivity(null);
        else setError(errorMessage(error));
      });
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => {
      controller.abort();
      clearInterval(timer);
    };
  }, [id, attempt]);
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
  if (activity === undefined) return <Loading />;
  if (!activity)
    return (
      <div className="state">
        <h1>활동을 찾을 수 없어요</h1>
        <Link className="button secondary" href="/">
          활동 탐색하기
        </Link>
      </div>
    );
  const status = recruitment(activity.application, now);
  const official = httpsUrl(activity.officialUrl);
  const image = httpsUrl(activity.imageUrl);
  const eligibility = eligibilityLines(activity.eligibility);
  return (
    <article className="detail">
      <Link className="back-link" href="/">
        <Icon name="arrow" size={20} />
        활동 탐색
      </Link>
      <div className="detail-heading">
        {image && (
          // Activity images come from arbitrary official hosts, so next/image optimization is not configured for them.
          // eslint-disable-next-line @next/next/no-img-element
          <img className="detail-image" src={image} alt="" />
        )}
        <p className="eyebrow">{activity.organization.name}</p>
        <span className={`badge ${status.open ? "" : "neutral"}`}>
          {status.label}
        </span>
        <h1>{activity.name}</h1>
        <p className="lead">{activity.description}</p>
      </div>
      <section className="detail-section">
        <h2>활동 안내</h2>
        <dl className="facts">
          {[
            ["주최", activity.organization.name],
            ["프로그램", activity.program.name],
            ["모집 기간", scheduleLabel(activity.application)],
            ["활동 기간", scheduleLabel(activity.activitySchedule)],
          ].map(([label, value]) => (
            <div key={label}>
              <dt>{label}</dt>
              <dd>{value || "미확인"}</dd>
            </div>
          ))}
        </dl>
      </section>
      {eligibility.length > 0 && (
        <section className="detail-section">
          <h2>대상</h2>
          <ul className="eligibility-list">
            {eligibility.map((line) => (
              <li key={line} className="badge neutral">
                {line}
              </li>
            ))}
          </ul>
        </section>
      )}
      <section className="detail-section">
        <h2>일정</h2>
        {activity.schedules.length ? (
          <ul className="schedule-list">
            {activity.schedules.map((schedule) => (
              <li key={schedule.id}>
                <Icon name="calendar" />
                <div>
                  <h3>{schedule.title}</h3>
                  <p>{scheduleLabel(schedule) || "일정 미확인"}</p>
                  {schedule.address && <p>{schedule.address}</p>}
                </div>
              </li>
            ))}
          </ul>
        ) : (
          <p>{scheduleLabel(activity.activitySchedule) || "일정 미확인"}</p>
        )}
      </section>
      <section className="detail-section source">
        <h2>공식 출처</h2>
        <p>신청 조건과 최신 일정은 공식 사이트에서 확인해 주세요.</p>
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
        {official ? (
          <a
            className={`button ${status.open ? "" : "secondary"}`}
            href={official}
            target="_blank"
            rel="noopener noreferrer"
          >
            {status.open
              ? "공식 사이트에서 신청하기"
              : "공식 사이트에서 모집 확인"}{" "}
            <Icon name="external" size={18} />
          </a>
        ) : (
          <p role="status">연결 가능한 공식 링크가 없어요.</p>
        )}
      </div>
    </article>
  );
}
