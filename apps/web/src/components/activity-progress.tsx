"use client";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import type { ScheduleStatus } from "@/lib/models";
import { useAuth } from "@/features/auth/model/auth-context";
import { useMine } from "@/features/mine/model/mine-context";
import { type Stage, buildProgress, markedStatus, progressBadge, stageDate, statusOptions } from "@/features/mine/model/stages";
import { cn } from "@/shared/lib/utils";
import { Badge } from "@/shared/ui/badge";
import { Button } from "@/shared/ui/button";
import { Card } from "@/shared/ui/card";
import { CenteredMessage } from "@/shared/ui/centered-message";
import { Dialog, DialogActions, DialogClose, DialogContent, DialogDescription, DialogTitle } from "@/shared/ui/dialog";
import { Icon } from "@/shared/ui/icon";
import { InsetGroup } from "@/shared/ui/inset-group";
import { LargeTitle } from "@/shared/ui/nav-title";
import { Skeleton, SkeletonGroup } from "@/shared/ui/skeleton";
import { Failure } from "@/shared/ui/states";
import { ActivityCard, activityCardProps } from "@/widgets/activity/activity-card";
import { TimetableSheet } from "@/widgets/activity/timetable";

/**
 * `/mine/[id]` (contract "진행 단계 화면 정리"·"선발 과정·활동 일정 분리"·"트랙·세션 시간표" 내 활동, web 2단계 B): the activity card (no tag line)
 * leading to the detail, then `선발 과정` and `활동 일정` as inset groups — one line per stage (icon · name · date · 지남/진행 중/예정), a
 * native select for a 선발 단계's 대기/통과/탈락 (the schedule's own statuses), `시간표` on a block with one — and `내 활동에서 빼기` last.
 * The web has no device calendar, so no `캘린더에 추가`·`겹치는 시간 확인`.
 */
export function ActivityProgress({ id }: { id: number }) {
  const auth = useAuth();
  const mine = useMine();
  const router = useRouter();
  const [now, setNow] = useState(Date.now);
  const [timetableId, setTimetableId] = useState<number>();
  const [timetableOpen, setTimetableOpen] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 60_000);
    return () => clearInterval(timer);
  }, []);
  const entry = mine.entry(id);
  if (auth.status === "loading" || mine.status === "loading" || (entry && entry.activity === undefined)) return <ProgressLoading />;
  if (!auth.signedIn || auth.consentRequired)
    return (
      <CenteredMessage
        icon="check-circle"
        level={1}
        title="로그인하면 진행 단계를 볼 수 있어요"
        primary={{ label: auth.signedIn ? "동의하기" : "로그인", onClick: auth.signedIn ? auth.openConsent : auth.openSignIn }}
        secondary={{ label: "활동 상세 보기", href: `/activities/${id}` }}
      />
    );
  if (mine.status === "error") return <Failure message="내 활동을 불러오지 못했어요." retry={mine.reload} />;
  if (!entry || !entry.activity)
    return (
      <CenteredMessage
        icon="info"
        level={1}
        title={entry ? "삭제된 활동이에요" : "내 활동에 없는 활동이에요"}
        message={entry ? "활동이 지워졌거나 더 이상 공개되지 않아요." : "활동 상세의 + 버튼으로 내 활동에 넣을 수 있어요."}
        primary={entry ? { label: "내 활동에서 빼기", onClick: () => mine.remove(id).then(() => router.replace("/mine")) } : { label: "활동 상세 보기", href: `/activities/${id}` }}
        secondary={{ label: "내 활동으로", href: "/mine" }}
      />
    );
  const activity = entry.activity;
  const progress = buildProgress(activity, entry.item, now);
  const selection = progress.stages.filter((stage) => !stage.inActivitySection);
  const activityStages = progress.stages.filter((stage) => stage.inActivitySection);
  const badge = progressBadge(progress, now);
  const openTimetable = progress.stages.find((stage) => stage.id === timetableId)?.schedule;
  async function pick(stage: Stage, status: ScheduleStatus) {
    setError("");
    try {
      await mine.mark(activity.id, stage, status);
    } catch {
      setError("표시를 저장하지 못했어요. 잠시 후 다시 시도해 주세요.");
    }
  }
  async function remove() {
    setConfirming(false);
    setBusy(true);
    try {
      await mine.remove(activity.id);
      router.push("/mine");
    } catch {
      setError("내 활동에서 빼지 못했어요. 잠시 후 다시 시도해 주세요.");
      setBusy(false);
    }
  }
  const section = (title: string, stages: Stage[], trailing: React.ReactNode) =>
    stages.length > 0 && (
      <InsetGroup key={title} title={title} trailing={trailing}>
        {stages.map((stage) => (
          <StageRow key={stage.id} stage={stage} now={now} onPick={(status) => pick(stage, status)} onTimetable={() => {
            setTimetableId(stage.id);
            setTimetableOpen(true);
          }} />
        ))}
      </InsetGroup>
    );
  const statusBadge = (text: string, emphasized = false) => (
    <Badge variant={progress.failed ? "neutral" : emphasized ? "filled" : "tinted"} className={cn("tabular-nums", progress.failed && "text-danger")}>
      {text}
    </Badge>
  );
  return (
    <>
      <LargeTitle>진행 단계</LargeTitle>
      <Card className="mt-3 p-2.5">
        <ActivityCard {...activityCardProps(activity, now)} fieldTag={undefined} traitTags={[]} />
      </Card>
      <p className="mt-1.5 px-4 text-footnote text-secondary-label">
        {activity.program.name} · {activity.organization.name}
      </p>
      {error && (
        <p role="alert" className="mt-3 px-4 text-footnote text-danger">
          {error}
        </p>
      )}
      {/* 참여 중 shows once, on the 활동 일정 header; the first header's badge is then empty (contract "선발 과정·활동 일정 분리"). */}
      {section("선발 과정", selection, !progress.participating && statusBadge(badge))}
      {section("활동 일정", activityStages, progress.participating ? statusBadge("참여 중", true) : !selection.length && statusBadge(badge))}
      <p className="mt-1.5 px-4 text-footnote text-secondary-label">표시는 내가 남긴 것이며 주최 측 결과가 아니에요.</p>
      <TimetableSheet activityId={activity.id} activityName={activity.name} schedule={openTimetable} open={timetableOpen} onOpenChange={setTimetableOpen} />
      <Button variant="destructive" className="mx-auto mt-8 flex" disabled={busy} onClick={() => setConfirming(true)}>
        내 활동에서 빼기
      </Button>
      <Dialog open={confirming} onOpenChange={setConfirming}>
        <DialogContent>
          <DialogTitle>내 활동에서 뺄까요?</DialogTitle>
          <DialogDescription>단계 표시도 함께 지워져요.</DialogDescription>
          <DialogActions>
            <DialogClose className="text-body text-teal">취소</DialogClose>
            <button type="button" className="text-body font-semibold text-danger" onClick={remove}>
              빼기
            </button>
          </DialogActions>
        </DialogContent>
      </Dialog>
    </>
  );
}

const statusKey = (status: ScheduleStatus) => (status.id === null ? `kind:${status.kind}` : `id:${status.id}`);
/**
 * One stage line: kind icon (past secondary, current teal, upcoming faint, 탈락 danger), name (current bold), `날짜 · 지남/진행 중/예정`
 * (진행 중 only on a 선발 단계); on the right the 시간표 button when the block has one and, for a 선발 단계, the status select.
 */
function StageRow({ stage, now, onPick, onTimetable }: { stage: Stage; now: number; onPick: (status: ScheduleStatus) => void; onTimetable: () => void }) {
  const marked = markedStatus(stage);
  const options = statusOptions(stage.schedule);
  const failed = stage.mark.kind === "FAILED";
  const timing = stage.state === "past" ? "지남" : stage.state === "current" && stage.selection ? "진행 중" : "예정";
  return (
    <li className="flex items-center gap-3 px-4 py-2.5">
      <span className={cn("flex shrink-0", failed ? "text-danger" : stage.state === "current" ? "text-teal" : stage.state === "past" ? "text-secondary-label" : "text-secondary-label/50")}>
        <Icon name={stage.icon} />
      </span>
      <span className="flex min-w-0 flex-1 flex-col gap-0.5">
        <span className={cn("text-subheadline break-keep", stage.state === "current" && "font-semibold")}>{stage.name}</span>
        <span className="text-footnote text-secondary-label">
          {stageDate(stage, now)} · {timing}
        </span>
      </span>
      {stage.schedule.hasTimetable && (
        <button
          type="button"
          aria-haspopup="dialog"
          aria-label={`${stage.name} 시간표 보기`}
          onClick={onTimetable}
          className="flex min-h-11 shrink-0 items-center gap-1 text-subheadline text-teal active:opacity-70"
        >
          <Icon name="table" size="inline" />
          시간표
        </button>
      )}
      {stage.selection && (
        <select
          aria-label={`${stage.name} 상태`}
          value={statusKey(marked)}
          onChange={(event) => {
            const next = options.find((option) => statusKey(option) === event.target.value);
            if (next && statusKey(next) !== statusKey(marked)) onPick(next);
          }}
          className={cn(
            "min-h-9 shrink-0 rounded-control bg-fill px-2 text-subheadline font-semibold",
            marked.kind === "PASSED" ? "text-teal" : marked.kind === "FAILED" ? "text-danger" : "text-secondary-label",
          )}
        >
          {options.map((option) => (
            <option key={statusKey(option)} value={statusKey(option)}>
              {option.name}
            </option>
          ))}
        </select>
      )}
    </li>
  );
}

function ProgressLoading() {
  return (
    <SkeletonGroup className="pt-1">
      <Skeleton className="h-9 w-40" />
      <Card className="mt-3 p-2.5">
        <div className="flex flex-col gap-2 rounded-[14px] bg-background px-3 pt-3 pb-2.5">
          <Skeleton className="h-4 w-2/3" />
          <Skeleton className="h-3.5 w-1/2" />
        </div>
      </Card>
      <Card className="mt-7 divide-y divide-separator">
        {[0, 1, 2].map((key) => (
          <div key={key} className="flex min-h-11 items-center gap-3 px-4 py-3">
            <Skeleton className="size-6 rounded-full" />
            <Skeleton className="h-4 flex-1" />
          </div>
        ))}
      </Card>
    </SkeletonGroup>
  );
}
