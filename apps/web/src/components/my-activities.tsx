"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { type Activity, type MyActivity } from "@/lib/models";
import { useAuth } from "@/features/auth/model/auth-context";
import { type MineEntry, useMine } from "@/features/mine/model/mine-context";
import { type Progress, buildProgress, ended, nextStart, progressBadge } from "@/features/mine/model/stages";
import { cn } from "@/shared/lib/utils";
import { Badge } from "@/shared/ui/badge";
import { Button } from "@/shared/ui/button";
import { CenteredMessage } from "@/shared/ui/centered-message";
import { Icon } from "@/shared/ui/icon";
import { LargeTitle } from "@/shared/ui/nav-title";
import { Skeleton, SkeletonGroup } from "@/shared/ui/skeleton";
import { Failure } from "@/shared/ui/states";
import { StageTrack } from "@/widgets/activity/stage-track";

type Row = { entry: MineEntry; activity: Activity; progress: Progress };

/**
 * `/mine` (contract "내 활동 진행 트랙" › 화면, web 2단계 B): the signed-in account's 내 활동 as progress rows — name (with a `시간표` badge),
 * 프로그램 · 조직, the segment track and the `D-3 · 면접`/`참여 중`/`지남`/`탈락` badge — sorted by the next stage, and `끝난 활동` collapsed
 * below. Each row opens the progress page. The web keeps no device copy, so signed out there is nothing but the sign-in CTA.
 */
export function MyActivities() {
  const auth = useAuth();
  const mine = useMine();
  const [now, setNow] = useState(Date.now);
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 60_000);
    return () => clearInterval(timer);
  }, []);
  let body: React.ReactNode;
  if (auth.status === "loading" || mine.status === "loading") body = <ListLoading />;
  else if (!auth.signedIn)
    body = (
      <CenteredMessage
        icon="check-circle"
        title="로그인하면 내 활동을 볼 수 있어요"
        message="신청한 활동의 선발 과정과 활동 일정을 한눈에 봐요. 표시는 이 계정에 저장돼요."
        primary={{ label: "로그인", onClick: auth.openSignIn }}
        secondary={{ label: "활동 둘러보기", href: "/" }}
      />
    );
  else if (auth.consentRequired)
    body = <CenteredMessage icon="info" title="동의가 필요해요" message="동의하기 전에는 내 활동을 볼 수 없어요." primary={{ label: "동의하기", onClick: auth.openConsent }} />;
  else if (mine.status === "error") body = <Failure message="내 활동을 불러오지 못했어요." retry={mine.reload} />;
  else if (!mine.entries.length)
    body = (
      <CenteredMessage
        icon="check-circle"
        title="아직 내 활동이 없어요"
        message="활동 카드나 상세의 + 버튼으로 신청한 활동을 넣어 두면 진행 단계를 볼 수 있어요."
        primary={{ label: "활동 둘러보기", href: "/" }}
      />
    );
  else {
    const rows: Row[] = [];
    const pending: MineEntry[] = [];
    const gone: MineEntry[] = [];
    for (const entry of mine.entries) {
      if (entry.activity === undefined) pending.push(entry);
      else if (entry.activity === null) gone.push(entry);
      else rows.push({ entry, activity: entry.activity, progress: buildProgress(entry.activity, entry.item, now) });
    }
    const active = rows.filter((row) => !ended(row.progress)).sort((a, b) => nextStart(a.progress) - nextStart(b.progress) || a.activity.name.localeCompare(b.activity.name, "ko"));
    const over = rows.filter((row) => ended(row.progress));
    body = (
      <>
        <ul className="mt-5 flex flex-col gap-2.5">
          {active.map((row) => (
            <ProgressRow key={row.activity.id} {...row} now={now} />
          ))}
          {pending.map((entry) => (
            <li key={entry.item.activityId}>
              <RowSkeleton />
            </li>
          ))}
          {gone.map((entry) => (
            <GoneRow key={entry.item.activityId} item={entry.item} />
          ))}
        </ul>
        {!active.length && !pending.length && !gone.length && (
          <p className="mt-5 px-4 text-subheadline text-secondary-label">진행 중인 활동이 없어요.</p>
        )}
        {over.length > 0 && (
          <details className="group mt-7">
            <summary className="flex min-h-11 cursor-pointer list-none items-center gap-1 px-4 text-footnote font-semibold text-secondary-label">
              끝난 활동 {over.length}개
              <span className="flex transition-transform group-open:rotate-180">
                <Icon name="chevron-down" size="inline" />
              </span>
            </summary>
            <ul className="mt-1.5 flex flex-col gap-2.5">
              {over.map((row) => (
                <ProgressRow key={row.activity.id} {...row} now={now} />
              ))}
            </ul>
          </details>
        )}
        <p className="mt-4 px-4 text-footnote text-secondary-label">참여·통과 표시는 내가 남긴 것이며 주최 측 확인이 아니에요.</p>
      </>
    );
  }
  return (
    <>
      <LargeTitle>내 활동</LargeTitle>
      {body}
    </>
  );
}

function ProgressRow({ activity, progress, now }: Row & { now: number }) {
  const badge = progressBadge(progress, now);
  const timetable = activity.hasTimetable ?? (activity.activitySchedule.hasTimetable || activity.schedules.some((schedule) => schedule.hasTimetable));
  return (
    <li>
      <Link href={`/mine/${activity.id}`} className="flex flex-col gap-3 rounded-card bg-card p-3.5 transition-opacity active:opacity-70">
        <span className="flex items-start gap-3">
          <span className="flex min-w-0 flex-1 flex-col gap-0.5">
            <span className="flex flex-wrap items-baseline gap-1.5">
              <span className="text-headline font-semibold break-keep">{activity.name}</span>
              {timetable && (
                <Badge className="gap-1">
                  <Icon name="table" size="inline" />
                  시간표
                </Badge>
              )}
            </span>
            <span className="truncate text-caption text-secondary-label">
              {activity.program.name} · {activity.organization.name}
            </span>
          </span>
          <Badge variant={progress.failed ? "neutral" : progress.participating ? "filled" : "tinted"} className={cn("tabular-nums", progress.failed && "text-danger")}>
            {badge}
          </Badge>
        </span>
        <StageTrack progress={progress} />
      </Link>
    </li>
  );
}

/** A 내 활동 item whose activity is gone (`deleted`, or the activity 404s): only `빼기` is left. */
function GoneRow({ item }: { item: MyActivity }) {
  const mine = useMine();
  const [busy, setBusy] = useState(false);
  return (
    <li className="flex items-center gap-3 rounded-card bg-card py-2 pr-2 pl-3.5">
      <span className="flex-1 text-subheadline text-secondary-label">삭제된 활동</span>
      <Button
        variant="destructive"
        size="sm"
        disabled={busy}
        onClick={() => {
          setBusy(true);
          mine.remove(item.activityId).catch(() => setBusy(false));
        }}
      >
        빼기
      </Button>
    </li>
  );
}

function RowSkeleton() {
  return (
    <div className="flex flex-col gap-3 rounded-card bg-card p-3.5">
      <div className="flex items-start gap-3">
        <div className="flex flex-1 flex-col gap-1.5">
          <Skeleton className="h-4 w-2/3" />
          <Skeleton className="h-3 w-1/3" />
        </div>
        <Skeleton className="h-5 w-16 rounded-full" />
      </div>
      <Skeleton className="h-1.5 w-full rounded-full" />
    </div>
  );
}
function ListLoading() {
  return (
    <SkeletonGroup className="mt-5 flex flex-col gap-2.5">
      <RowSkeleton />
      <RowSkeleton />
    </SkeletonGroup>
  );
}
