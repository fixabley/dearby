"use client";
import { useEffect, useId, useRef, useState } from "react";
import {
  type Activity,
  type Schedule,
  type ScheduleDay,
  type Tag,
  fieldTagsOf,
  foldScheduleDays,
  httpsUrl,
  traitTagsOf,
  STATUS_BADGE,
  activityStatus,
  applicable,
  recruitmentDate,
  scheduleDayRange,
  scheduleLabel,
  upcomingStartDday,
} from "@/lib/models";
import { downloadIcs, icsOf } from "@/lib/ics";
import { eligibilityLines } from "@/lib/eligibility";
import { categoryLabel } from "@/lib/category";
import { CategoryIcon } from "@/shared/ui/category-icon";
import { errorMessage, RequestError } from "@/lib/client";
import { publicRead } from "@/features/auth/api/auth-client";
import { useAuth } from "@/features/auth/model/auth-context";
import { track } from "@/shared/lib/analytics";
import { cn } from "@/shared/lib/utils";
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
import { TimetableSheet, type TimetableTarget } from "@/widgets/activity/timetable";
import { statusVariant } from "@/widgets/activity/activity-card";
import { FavoriteHeart } from "@/features/favorites/ui/favorite-heart";

/** One row of a schedule section: the block it stands for, its heading, the date text and an optional `D-n` badge (모집 기간 only). */
type ScheduleRow = { schedule: Schedule; title: string; label: string; badge?: string };
/** A section lists plain rows and, where several timed blocks share a Seoul day, one folded day row (2026-10-26 하루 시간표). */
type SectionItem = ScheduleRow | ScheduleDay;

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
  // What the 시간표 modal shows — a block or a whole day (contract "트랙·세션 시간표" › 앱·웹 화면; 2026-10-26 하루 시간표); the
  // target outlives `open` so the closing animation keeps its content.
  const [timetableTarget, setTimetableTarget] = useState<TimetableTarget>();
  const [timetableOpen, setTimetableOpen] = useState(false);
  // A sign-in or sign-out reloads the activity so `favorited` follows the account; `activity_view` counts only the first load.
  const { epoch } = useAuth();
  const viewed = useRef(false);
  useEffect(() => {
    const controller = new AbortController();
    publicRead<Activity>(`/activities/${id}`, { signal: controller.signal })
      .then((found) => {
        setActivity(found);
        if (!viewed.current) track("activity_view", eventParams(found));
        viewed.current = true;
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
  }, [id, attempt, epoch]);
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
  const startDday = upcomingStartDday(activity, now);
  const official = httpsUrl(activity.officialUrl);
  // CTA: 마감이 아니면 신청 링크(없으면 공식 안내)로, 마감이면 공식 안내로(contract "신청 확인 조건 완화·공개 applicationUrl").
  const closed = !applicable(status);
  const apply = closed ? undefined : httpsUrl(activity.applicationUrl ?? null);
  const cta = apply ?? official;
  const eligibility = eligibilityLines(activity.eligibility);
  const place = activity.place ?? null;
  // 모집 날짜 표기 (contract "모집 날짜 표기 — 2026-10-26"): `10.1 – 10.20` with `D-3` while open; an end alone keeps `~ … 마감`.
  const recruitmentDay = recruitmentDate(activity.application, now);
  const recruitmentText = (status !== "NO_RECRUITMENT" && recruitmentDay.text) || "모집 일정 미정";
  const detailRow = (schedule: Schedule): ScheduleRow => ({ schedule, title: schedule.title, label: scheduleLabel(schedule, "activity") || "일정 미확인" });
  const applicationRow: ScheduleRow[] = activity.application
    ? [{ schedule: activity.application, title: "모집 기간", label: recruitmentText, badge: recruitmentDay.dday }]
    : [];
  const periodRow: ScheduleRow = { ...detailRow(activity.activitySchedule), title: "활동 기간" };
  // 선발 과정·활동 일정 (contract "선발 과정·활동 일정 분리" 2026-10-26, same on iOS·Android): 모집 기간 row + SELECTION schedules,
  // 활동 기간 row + ACTIVITY schedules, each period row first; the two rows then leave `활동 안내`. An older server (no `phase`)
  // keeps the single `일정` list — detail schedules in server order, the 활동 기간 row only alone or when it carries a timetable.
  const phased = !!activity.activitySchedule.phase && activity.schedules.every((schedule) => schedule.phase);
  // 하루 시간표 (2026-10-26): 활동 일정 blocks of one Seoul day fold into a day row; 선발 과정, period and recruitment rows never fold.
  const detailItems = (schedules: Schedule[]): SectionItem[] =>
    foldScheduleDays(schedules, now).map((item) => ("schedules" in item ? item : detailRow(item)));
  const scheduleSections: [string, SectionItem[]][] = phased
    ? [
        ["선발 과정", [...applicationRow, ...activity.schedules.filter((schedule) => schedule.phase === "SELECTION").map(detailRow)]],
        ["활동 일정", [periodRow, ...detailItems(activity.schedules.filter((schedule) => schedule.phase === "ACTIVITY"))]],
      ]
    : [["일정", [...(!activity.schedules.length || activity.activitySchedule.hasTimetable ? [periodRow] : []), ...detailItems(activity.schedules)]]];
  const openTimetable = (target: TimetableTarget) => {
    setTimetableTarget(target);
    setTimetableOpen(true);
  };
  // A block with a timetable is one whole-row button that opens the modal; other blocks stay plain rows. Inside a day row the
  // block's day travels along for `이 날 전체 보기`.
  const scheduleRow = (schedule: Schedule, body: React.ReactNode, day?: ScheduleDay) =>
    schedule.hasTimetable ? (
      <button
        type="button"
        aria-haspopup="dialog"
        className="flex w-full items-start gap-3 px-4 py-3 text-left hover:bg-fill/50 active:bg-fill"
        onClick={() => openTimetable({ kind: "block", schedule, day })}
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
  const rowBody = ({ schedule, title, label, badge }: ScheduleRow, icon: "calendar" | "clock" = "calendar") => (
    <>
      <Icon name={icon} tone="teal" />
      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
        <h3 className="text-subheadline font-semibold">{title}</h3>
        <p className="text-footnote text-secondary-label">
          {label}
          {badge && (
            <Badge variant="filled" className="ml-1.5 align-[1px] text-caption2 tabular-nums">
              {badge}
            </Badge>
          )}
        </p>
        {schedule.address && <p className="text-footnote text-secondary-label">{schedule.address}</p>}
      </div>
    </>
  );
  // `.ics 내려받기` (web stand-in for the apps' 캘린더에 추가): one event per block of the day.
  const downloadDay = (day: ScheduleDay) =>
    downloadIcs(
      `dearby-${activity.id}-${day.day}.ics`,
      icsOf(
        day.schedules.map((schedule) => ({
          uid: `dearby-schedule-${schedule.id}@${window.location.hostname}`,
          summary: `${activity.name} · ${schedule.title || "활동"}`,
          start: Date.parse(schedule.startAt!),
          end: Date.parse(schedule.endAt!),
          location: schedule.address,
          url: `${window.location.origin}/activities/${activity.id}`,
        })),
      ),
    );
  const facts: [string, string, string?][] = [
    ["주최", activity.organization.name],
    ["프로그램", activity.program.name],
    ...(phased ? [] : ([["모집 기간", recruitmentText, recruitmentDay.dday], ["활동 기간", periodRow.label]] as [string, string, string?][])),
  ];
  return (
    <>
      <article className="pt-2 pb-48">
        <div className="flex flex-col gap-3">
          <div className="flex flex-wrap gap-1.5">
            {STATUS_BADGE[status] && (
              <Badge variant={statusVariant[status]}>{STATUS_BADGE[status]}</Badge>
            )}
            {/* 모집 시작 D-day (contract "모집 예정 시작 D-day — 2026-10-26") in the 모집 예정 colours beside the status. */}
            {startDday && (
              <Badge variant="tinted" className="text-caption2 tabular-nums">
                {startDday}
              </Badge>
            )}
            {activity.category && (
              <Badge>
                <CategoryIcon code={activity.category} />
                {categoryLabel(activity.category)}
              </Badge>
            )}
            {/* 활동 하트 (contract "즐겨찾기(하트)" › 활동 상세 머리; web since "웹 화면 — 2단계 A"). The `+` 내 활동 button is a later PR. */}
            <FavoriteHeart kind="activity" id={activity.id} favorited={activity.favorited ?? false} category={activity.category} className="ml-auto" />
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
        {/* One inset group per non-empty section (a 선발 없는 컨퍼런스 shows 활동 일정 alone). */}
        {scheduleSections
          .filter(([, items]) => items.length)
          .map(([title, items]) => (
            <InsetGroup key={title} title={title}>
              {items.map((item) =>
                "schedules" in item ? (
                  <li key={item.day}>
                    <DayRow
                      day={item}
                      onTimetable={item.schedules.some((schedule) => schedule.hasTimetable) ? () => openTimetable({ kind: "day", day: item }) : undefined}
                      onDownload={() => downloadDay(item)}
                    >
                      {item.schedules.map((schedule) => (
                        <li key={schedule.id}>{scheduleRow(schedule, rowBody(detailRow(schedule), "clock"), item)}</li>
                      ))}
                    </DayRow>
                  </li>
                ) : (
                  <li key={item.schedule.id}>{scheduleRow(item.schedule, rowBody(item))}</li>
                ),
              )}
            </InsetGroup>
          ))}
        <TimetableSheet
          activityId={activity.id}
          activityName={activity.name}
          target={timetableTarget}
          open={timetableOpen}
          onOpenChange={setTimetableOpen}
          onTargetChange={setTimetableTarget}
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

/**
 * 하루 시간표 day row (2026-10-26 사용자 "10/28일 누르면 시간표보기 일정추가 이런식으로"): collapsed, `10월 28일 (수)` over
 * `10:00 ~ 17:30 · 블록 4개`; expanded (the `discovery.tsx` disclosure: `aria-expanded`, animated grid rows, `inert` while shut),
 * `시간표 보기` (only when a block of the day has one) and `.ics 내려받기`, then the day's block rows as their own list. The row
 * button and the panel are siblings, since block rows are buttons themselves.
 */
function DayRow({
  day,
  onTimetable,
  onDownload,
  children,
}: {
  day: ScheduleDay;
  onTimetable?: () => void;
  onDownload: () => void;
  children: React.ReactNode;
}) {
  const [open, setOpen] = useState(false);
  const panelId = useId();
  return (
    <>
      <button
        type="button"
        aria-expanded={open}
        aria-controls={panelId}
        className="flex w-full items-start gap-3 px-4 py-3 text-left hover:bg-fill/50 active:bg-fill"
        onClick={() => setOpen((value) => !value)}
      >
        <Icon name="calendar" tone="teal" />
        <span className="flex min-w-0 flex-1 flex-col gap-0.5">
          <h3 className="text-subheadline font-semibold">{day.label}</h3>
          <span className="text-footnote text-secondary-label tabular-nums">{scheduleDayRange(day)}</span>
        </span>
        <span className={cn("flex self-center text-secondary-label transition-transform duration-300", open && "rotate-180")}>
          <Icon name="chevron-down" size="inline" />
        </span>
      </button>
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
          <div className="flex flex-wrap gap-2 px-4 pb-3">
            {onTimetable && (
              <Button variant="tinted" size="sm" aria-haspopup="dialog" onClick={onTimetable}>
                시간표 보기
              </Button>
            )}
            <Button variant="tinted" size="sm" onClick={onDownload}>
              .ics 내려받기
            </Button>
          </div>
          <ul className="divide-y divide-separator border-t border-separator pl-4 [&>li]:min-h-11">{children}</ul>
        </div>
      </div>
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
