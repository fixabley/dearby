"use client";
import { useEffect, useState } from "react";
import {
  type Activity,
  type Schedule,
  type Tag,
  fieldTagsOf,
  httpsUrl,
  traitTagsOf,
  STATUS_BADGE,
  activityStatus,
  applicable,
  recruitmentDate,
  scheduleLabel,
} from "@/lib/models";
import { eligibilityLines } from "@/lib/eligibility";
import { categoryLabel } from "@/lib/category";
import { errorMessage, request, RequestError } from "@/lib/client";
import { track } from "@/shared/lib/analytics";
import { Failure } from "@/shared/ui/states";
import { CenteredMessage } from "@/shared/ui/centered-message";
import { Icon } from "@/shared/ui/icon";
import { Badge } from "@/shared/ui/badge";
import { BottomBar } from "@/shared/ui/bottom-bar";
import { Button } from "@/shared/ui/button";
import { Card } from "@/shared/ui/card";
import { InsetGroup } from "@/shared/ui/inset-group";
import { TagIcon } from "@/shared/ui/tag-icon";
import { LargeTitle } from "@/shared/ui/nav-title";
import { Separator } from "@/shared/ui/separator";
import { Skeleton, SkeletonGroup } from "@/shared/ui/skeleton";
import { TimetableSheet } from "@/widgets/activity/timetable";
import { statusVariant } from "@/widgets/activity/activity-card";

/** 사용 통계 매개변수 (contract analytics-v1.md "웹"): `source` is `link`, the web page being the shared link itself. */
function eventParams(activity: Activity) {
  return {
    activity_id: activity.id,
    ...(activity.category && { category: activity.category.toLowerCase() }),
    source: "link",
  };
}

/**
 * Activity detail (contract "웹 리디자인" › 활동 상세, iOS `ActivityDetailView`): an information block (status/category capsules,
 * the name as a title that collapses into the bar, organization, description), inset-grouped sections, and a sticky bottom CTA.
 */
export function ActivityDetail({ id }: { id: number }) {
  const [activity, setActivity] = useState<Activity | null>();
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const [now, setNow] = useState(Date.now);
  // The schedule block whose 시간표 modal is open (contract "트랙·세션 시간표" › 앱·웹 화면); the id outlives `open` so the
  // closing animation keeps its content.
  const [timetableId, setTimetableId] = useState<number>();
  const [timetableOpen, setTimetableOpen] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    request<Activity>(`/activities/${id}`, { signal: controller.signal })
      .then((found) => {
        setActivity(found);
        track("activity_view", eventParams(found));
      })
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
  if (activity === undefined) return <DetailLoading />;
  if (!activity)
    return (
      <CenteredMessage
        icon="info"
        level={1}
        title="활동을 찾을 수 없어요"
        message="삭제되었거나 더 이상 공개되지 않는 활동이에요."
        primary={{ label: "활동 둘러보기", href: "/" }}
      />
    );
  // 활동 상태 (contract "활동 상태" 2026-10-26): the server's, else read from the recruitment window (older servers).
  const status = activityStatus(activity, now);
  const official = httpsUrl(activity.officialUrl);
  // CTA: 마감이 아니면 신청 링크(없으면 공식 안내)로, 마감이면 공식 안내로(contract "신청 확인 조건 완화·공개 applicationUrl").
  const closed = !applicable(status);
  const apply = closed ? undefined : httpsUrl(activity.applicationUrl ?? null);
  const cta = apply ?? official;
  const eligibility = eligibilityLines(activity.eligibility);
  const place = activity.place ?? null;
  // The 활동 기간 block can carry a timetable too; with 세부 일정 listed it gets its own row so the button does not disappear.
  const periodRow = activity.schedules.length && activity.activitySchedule.hasTimetable ? [activity.activitySchedule] : [];
  const openTimetable = [activity.activitySchedule, ...activity.schedules].find((schedule) => schedule.id === timetableId);
  // A block with a timetable is one whole-row button that opens the modal; other blocks stay plain rows.
  const scheduleRow = (schedule: Schedule, body: React.ReactNode) =>
    schedule.hasTimetable ? (
      <button
        type="button"
        aria-haspopup="dialog"
        className="flex w-full items-start gap-3 px-4 py-3 text-left hover:bg-fill/50 active:bg-fill"
        onClick={() => {
          setTimetableId(schedule.id);
          setTimetableOpen(true);
        }}
      >
        {body}
        <span className="flex shrink-0 items-center gap-0.5 self-center text-subheadline text-secondary-label">
          시간표
          <Icon name="chevron-right" size="inline" />
        </span>
      </button>
    ) : (
      <div className="flex items-start gap-3 px-4 py-3">{body}</div>
    );
  // 모집 날짜 표기 (contract "모집 날짜 표기 — 2026-10-26"): `10.1 – 10.20` with `D-3` while open; an end alone keeps `~ … 마감`.
  const recruitmentDay = recruitmentDate(activity.application, now);
  const facts: [string, string, string?][] = [
    ["주최", activity.organization.name],
    ["프로그램", activity.program.name],
    ["모집 기간", (status !== "NO_RECRUITMENT" && recruitmentDay.text) || "모집 일정 미정", recruitmentDay.dday],
    ["활동 기간", scheduleLabel(activity.activitySchedule, "activity")],
  ];
  return (
    <>
      <article className="pt-2 pb-48">
        <div className="flex flex-col gap-3">
          <div className="flex flex-wrap gap-1.5">
            {STATUS_BADGE[status] && (
              <Badge variant={statusVariant[status]}>{STATUS_BADGE[status]}</Badge>
            )}
            {activity.category && <Badge>{categoryLabel(activity.category)}</Badge>}
          </div>
          <LargeTitle size="title1">{activity.name}</LargeTitle>
          <p className="text-headline font-semibold text-secondary-label">{activity.organization.name}</p>
          {activity.description && (
            <>
              <Separator className="my-1" />
              <p className="leading-relaxed break-keep whitespace-pre-line">{activity.description}</p>
            </>
          )}
        </div>
        <InsetGroup title="활동 안내">
          {facts.map(([label, value, badge]) => (
            <li key={label} className="flex items-baseline gap-4 px-4 py-3">
              <span className="w-20 shrink-0 text-subheadline text-secondary-label">{label}</span>
              <span className="flex-1 text-subheadline break-keep">
                {value || "미확인"}
                {badge && (
                  <Badge variant="filled" className="ml-1.5 align-[1px] text-caption2 tabular-nums">
                    {badge}
                  </Badge>
                )}
              </span>
            </li>
          ))}
        </InsetGroup>
        {/* 분야·얻을 수 있는 것 as icon+text chips, each group omitted when empty (contract "분야·성격 태그와 아이콘"). */}
        <TagGroup title="분야" tags={activity.fieldTagsAll ?? fieldTagsOf(activity)} />
        <TagGroup title="얻을 수 있는 것" tags={traitTagsOf(activity)} />
        {eligibility.length > 0 && (
          <InsetGroup title="대상" plain>
            <ul className="flex flex-wrap gap-1.5">
              {eligibility.map((line) => (
                <li key={line}>
                  <Badge variant="tinted" className="px-2.5 py-1 text-footnote">
                    {line}
                  </Badge>
                </li>
              ))}
            </ul>
          </InsetGroup>
        )}
        <InsetGroup title="일정">
          {activity.schedules.length ? (
            [...periodRow, ...activity.schedules].map((schedule) => (
              <li key={schedule.id}>
                {scheduleRow(
                  schedule,
                  <>
                    <Icon name="calendar" tone="teal" />
                    <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                      <h3 className="text-subheadline font-semibold">{schedule.title}</h3>
                      <p className="text-footnote text-secondary-label">{scheduleLabel(schedule, "activity") || "일정 미확인"}</p>
                      {schedule.address && <p className="text-footnote text-secondary-label">{schedule.address}</p>}
                    </div>
                  </>,
                )}
              </li>
            ))
          ) : (
            <li className="text-subheadline">
              {scheduleRow(
                activity.activitySchedule,
                <>
                  <Icon name="calendar" tone="teal" />
                  <span className="min-w-0 flex-1">{scheduleLabel(activity.activitySchedule, "activity") || "일정 미확인"}</span>
                </>,
              )}
            </li>
          )}
        </InsetGroup>
        <TimetableSheet
          activityId={activity.id}
          activityName={activity.name}
          schedule={openTimetable}
          open={timetableOpen}
          onOpenChange={setTimetableOpen}
        />
        {/* 활동 장소 (contract "활동 장소 — 2026-10-19"): curated place with a map link; otherwise the collected phrase alone. */}
        {place ? (
          <InsetGroup title="장소">
            <li>
              <a
                className="flex min-h-11 items-center gap-3 px-4 py-3 active:bg-fill"
                href={`https://maps.google.com/?q=${place.lat},${place.lng}`}
                target="_blank"
                rel="noopener noreferrer"
              >
                <Icon name="map-pin" tone="teal" />
                <span className="flex flex-1 flex-col gap-0.5">
                  <strong className="text-subheadline font-semibold">{place.name}</strong>
                  {place.address && <span className="text-footnote text-secondary-label">{place.address}</span>}
                </span>
                <Icon name="external-link" size="inline" tone="secondary" />
              </a>
            </li>
          </InsetGroup>
        ) : activity.location ? (
          <InsetGroup title="장소">
            <li className="flex items-center gap-3 px-4 py-3 text-subheadline">
              <Icon name="map-pin" tone="teal" />
              {activity.location}
            </li>
          </InsetGroup>
        ) : null}
        <InsetGroup
          title="공식 출처"
          footer={
            <>
              신청 조건과 최신 일정은 공식 사이트에서 확인해 주세요.
              <br />
              웹에서는 개인 캘린더의 겹치는 시간을 확인할 수 없어요.
            </>
          }
        >
          {official ? (
            <li>
              <a
                className="flex min-h-11 items-center justify-between gap-3 px-4 py-3 text-body text-teal active:bg-fill"
                href={official}
                target="_blank"
                rel="noopener noreferrer"
              >
                공식 안내 보기 <Icon name="external-link" size="inline" />
              </a>
            </li>
          ) : (
            <li className="flex items-center px-4 py-3 text-subheadline text-secondary-label">연결 가능한 공식 링크가 없어요.</li>
          )}
        </InsetGroup>
      </article>
      {/* Sticky bottom CTA (contract "웹 리디자인" › 활동 상세): full-width teal fill, a text secondary, safe-area padding. */}
      <BottomBar>
          {cta ? (
            <Button asChild size="lg" variant="filled">
              <a
                href={cta}
                target="_blank"
                rel="noopener noreferrer"
                onClick={() => apply && track("apply_click", eventParams(activity))}
              >
                {closed ? "공식 안내 보기" : apply ? "신청 사이트 열기" : "공식 사이트에서 신청"}{" "}
                <Icon name="external-link" size="inline" />
              </a>
            </Button>
          ) : (
            <p role="status" className="py-3 text-subheadline text-secondary-label">
              연결 가능한 공식 링크가 없어요.
            </p>
          )}
          {apply && official && apply !== official && (
            <Button asChild size="sm" variant="plain">
              <a href={official} target="_blank" rel="noopener noreferrer">
                공식 안내 보기
              </a>
            </Button>
          )}
          <p className="text-caption text-secondary-label">신청은 공식 사이트에서 진행돼요.</p>
      </BottomBar>
    </>
  );
}

/** First load: the information block (capsules, title, organization) and the four-row `활동 안내` group as placeholders. */
function DetailLoading() {
  return (
    <SkeletonGroup className="pt-2">
      <div className="flex flex-col gap-3">
        <div className="flex gap-1.5">
          <Skeleton className="h-6 w-16 rounded-full" />
          <Skeleton className="h-6 w-14 rounded-full" />
        </div>
        <Skeleton className="h-8 w-3/4" />
        <Skeleton className="h-5 w-1/3" />
      </div>
      <Card className="mt-7 divide-y divide-separator">
        {[0, 1, 2, 3].map((key) => (
          <div key={key} className="flex min-h-11 items-center gap-4 px-4 py-3">
            <Skeleton className="h-4 w-16 shrink-0" />
            <Skeleton className="h-4 flex-1" />
          </div>
        ))}
      </Card>
    </SkeletonGroup>
  );
}

/** One tag group on the detail page: every tag as an icon+name chip; nothing when empty. */
function TagGroup({ title, tags }: { title: string; tags: Tag[] }) {
  if (!tags.length) return null;
  return (
    <InsetGroup title={title} plain>
      <ul className="flex flex-wrap gap-1.5">
        {tags.map((tag) => (
          <li key={tag.id}>
            <Badge className="gap-1.5 px-2.5 py-1 text-footnote">
              <TagIcon nameEn={tag.nameEn} />
              {tag.name}
            </Badge>
          </li>
        ))}
      </ul>
    </InsetGroup>
  );
}
