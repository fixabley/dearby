"use client";
import Link from "next/link";
import { useEffect, useId, useState } from "react";
import { NOTIFICATION_KIND_LABEL, type NotificationGroup, type NotificationItem } from "@/lib/account-models";
import { programLogoSrc } from "@/lib/models";
import { errorMessage, RequestError } from "@/lib/client";
import { useAuth } from "@/features/auth/model/auth-context";
import { AccountGate } from "@/features/auth/ui/account-gate";
import { Badge } from "@/shared/ui/badge";
import { Button } from "@/shared/ui/button";
import { Card } from "@/shared/ui/card";
import { CenteredMessage } from "@/shared/ui/centered-message";
import { Icon } from "@/shared/ui/icon";
import { LargeTitle } from "@/shared/ui/nav-title";
import { Skeleton, SkeletonGroup } from "@/shared/ui/skeleton";
import { Failure } from "@/shared/ui/states";
import { ProgramAvatar } from "@/widgets/activity/program-avatar";
import { markAllRead, markRead, notificationPage } from "../api/notifications-client";
import { useUnread } from "../model/unread";

/** `/notifications` (contract "알림 목록(발견 우상단 종)", web rules in "웹 화면 — 2단계 C"). */
export function NotificationsPage() {
  return (
    <>
      <LargeTitle>알림</LargeTitle>
      <AccountGate description="로그인하면 즐겨찾기한 프로그램·활동의 소식이 여기에 모여요." skeleton={<ListSkeleton />}>
        <NotificationList />
      </AccountGate>
    </>
  );
}

const WEEK = 7 * 86_400_000;
/**
 * 알림을 프로그램 카드로 묶는다 (contract "알림 목록" › 화면 모양): the discovery program card's head (logo, name, organization) with the
 * program's unread count in the badge spot; tapping expands the rows — activity name, kind chip, received time, a teal dot while
 * unread — each leading to the activity detail and marking that notification read. Groups within the last 7 days sit under `이번 주`,
 * the rest under `지난 알림`; `모두 읽음` at the top right. Nothing expands on its own. There is no web push: this list is the whole feature.
 */
function NotificationList() {
  const { signedOutLocally } = useAuth();
  const unread = useUnread();
  const [groups, setGroups] = useState<NotificationGroup[]>();
  const [cursor, setCursor] = useState<number | null>(null);
  /** When the list arrived: the `이번 주` split and the time labels are judged against it, not against every render. */
  const [loadedAt, setLoadedAt] = useState(0);
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const [loadingMore, setLoadingMore] = useState(false);
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState("");
  useEffect(() => {
    let cancelled = false;
    notificationPage().then(
      (page) => {
        if (cancelled) return;
        setGroups(page.groups);
        setCursor(page.nextCursor);
        setLoadedAt(Date.now());
      },
      (caught) => {
        if (cancelled) return;
        if (caught instanceof RequestError && caught.status === 401) signedOutLocally("로그인이 만료됐어요. 다시 로그인해 주세요.");
        else setError(errorMessage(caught));
      },
    );
    return () => {
      cancelled = true;
    };
  }, [attempt, signedOutLocally]);
  async function more() {
    if (cursor === null) return;
    setLoadingMore(true);
    try {
      const page = await notificationPage(cursor);
      setGroups((current) => [...(current ?? []), ...page.groups]);
      setCursor(page.nextCursor);
    } catch (caught) {
      setActionError(errorMessage(caught));
    } finally {
      setLoadingMore(false);
    }
  }
  /** Marks one row read: the server first, then this list and the bar's badge. */
  function read(item: NotificationItem) {
    if (item.readAt) return;
    const now = new Date().toISOString();
    setGroups((current) =>
      current?.map((group) =>
        group.items.some((other) => other.id === item.id)
          ? { ...group, unread: Math.max(0, group.unread - 1), items: group.items.map((other) => (other.id === item.id ? { ...other, readAt: now } : other)) }
          : group,
      ),
    );
    unread.setCount(Math.max(0, unread.count - 1));
    markRead(item.id).catch(() => unread.refresh());
  }
  async function readAll() {
    setBusy(true);
    setActionError("");
    try {
      await markAllRead();
      const now = new Date().toISOString();
      setGroups((current) => current?.map((group) => ({ ...group, unread: 0, items: group.items.map((item) => ({ ...item, readAt: item.readAt ?? now })) })));
      unread.setCount(0);
    } catch (caught) {
      if (caught instanceof RequestError && caught.status === 401) signedOutLocally("로그인이 만료됐어요. 다시 로그인해 주세요.");
      else setActionError(errorMessage(caught));
    } finally {
      setBusy(false);
    }
  }
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
  if (!groups) return <ListSkeleton />;
  if (!groups.length)
    return (
      <CenteredMessage
        icon="bell"
        title="아직 알림이 없어요"
        message="즐겨찾기한 프로그램·활동의 소식이 여기에 모여요"
        primary={{ label: "활동 둘러보기", href: "/" }}
      />
    );
  const now = loadedAt;
  const latest = (group: NotificationGroup) => Math.max(...group.items.map((item) => Date.parse(item.createdAt) || 0));
  const thisWeek = groups.filter((group) => now - latest(group) < WEEK);
  const older = groups.filter((group) => now - latest(group) >= WEEK);
  const anyUnread = groups.some((group) => group.unread > 0);
  return (
    <>
      <div className="mt-1 flex min-h-11 items-center justify-end">
        {anyUnread && (
          <Button variant="plain" size="sm" disabled={busy} onClick={readAll}>
            모두 읽음
          </Button>
        )}
      </div>
      {actionError && (
        <p role="alert" className="px-4 text-footnote text-danger">
          {actionError}
        </p>
      )}
      {[
        ["이번 주", thisWeek],
        ["지난 알림", older],
      ].map(([title, list]) =>
        (list as NotificationGroup[]).length ? (
          <section key={title as string} className="mt-3 flex flex-col gap-3" aria-label={title as string}>
            <h2 className="px-4 text-footnote font-semibold text-secondary-label">{title as string}</h2>
            {(list as NotificationGroup[]).map((group) => (
              <GroupCard key={group.program.id} group={group} now={now} onRead={read} />
            ))}
          </section>
        ) : null,
      )}
      {cursor !== null && (
        <Button variant="tinted" className="mx-auto mt-5 flex" disabled={loadingMore} onClick={more}>
          {loadingMore ? "불러오는 중…" : "더 보기"}
        </Button>
      )}
    </>
  );
}

function GroupCard({ group, now, onRead }: { group: NotificationGroup; now: number; onRead: (item: NotificationItem) => void }) {
  const [open, setOpen] = useState(false);
  const panelId = useId();
  const { program } = group;
  return (
    <Card>
      <div className="flex items-start gap-3 p-4">
        <button type="button" className="flex min-w-0 flex-1 items-start gap-3 text-left active:opacity-70" aria-expanded={open} aria-controls={panelId} onClick={() => setOpen((value) => !value)}>
          <ProgramAvatar name={program.name} src={programLogoSrc(program.logoUrl)} />
          <span className="flex min-w-0 flex-1 flex-col gap-0.5">
            <span className="text-headline font-semibold break-keep">{program.name}</span>
            <span className="text-subheadline text-secondary-label">{program.organization.name}</span>
            <span className="mt-1 text-footnote text-secondary-label">알림 {group.items.length}개</span>
          </span>
        </button>
        <div className="flex shrink-0 flex-col items-end gap-1.5">
          {open ? (
            <button type="button" aria-label="접기" aria-controls={panelId} onClick={() => setOpen(false)} className="grid size-[34px] place-items-center rounded-full bg-mint text-teal">
              <span className="flex rotate-180">
                <Icon name="chevron-down" size="inline" />
              </span>
            </button>
          ) : group.unread > 0 ? (
            <span className="grid h-[34px] min-w-[34px] place-items-center rounded-full bg-teal-fill px-3 text-subheadline font-semibold text-on-teal tabular-nums">
              <span className="sr-only">읽지 않은 알림 </span>
              {group.unread}
              <span className="sr-only">개</span>
            </span>
          ) : (
            <span className="grid size-[34px] place-items-center rounded-full bg-fill text-secondary-label">
              <Icon name="chevron-down" size="inline" />
            </span>
          )}
        </div>
      </div>
      {open && (
        <ul id={panelId} className="divide-y divide-separator border-t border-separator">
          {group.items.map((item) => (
            <li key={item.id}>
              <Link
                href={`/activities/${item.activity.id}`}
                onClick={() => onRead(item)}
                className="flex min-h-11 items-center gap-3 px-4 py-3 active:opacity-70"
                aria-label={[item.readAt ? "" : "읽지 않음", item.activity.name, NOTIFICATION_KIND_LABEL[item.kind] ?? item.kind, timeLabel(item.createdAt, now)].filter(Boolean).join(", ")}
              >
                <span aria-hidden="true" className={`size-2 shrink-0 rounded-full ${item.readAt ? "bg-transparent" : "bg-teal-fill"}`} />
                <span className="flex min-w-0 flex-1 flex-col gap-1">
                  <span className="text-body font-semibold break-keep">{item.activity.name}</span>
                  <span className="flex flex-wrap items-center gap-1.5 text-footnote text-secondary-label">
                    <Badge variant="tinted">{NOTIFICATION_KIND_LABEL[item.kind] ?? item.kind}</Badge>
                    {timeLabel(item.createdAt, now)}
                  </span>
                </span>
                <Icon name="chevron-right" size="inline" tone="secondary" />
              </Link>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

/** `오늘 08:00` · `어제` · `10월 21일` in Seoul time (contract "알림 목록" › 받은 시각). */
function timeLabel(iso: string, now: number): string {
  const time = Date.parse(iso);
  if (Number.isNaN(time)) return "";
  const day = (value: number) => new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date(value));
  const today = day(now);
  if (day(time) === today) return `오늘 ${new Intl.DateTimeFormat("ko-KR", { timeZone: "Asia/Seoul", hour: "2-digit", minute: "2-digit", hourCycle: "h23" }).format(new Date(time))}`;
  if (day(time) === day(now - 86_400_000)) return "어제";
  return new Intl.DateTimeFormat("ko-KR", { timeZone: "Asia/Seoul", month: "long", day: "numeric" }).format(new Date(time));
}

function ListSkeleton() {
  return (
    <SkeletonGroup className="mt-5 flex flex-col gap-3">
      {[0, 1].map((key) => (
        <Card key={key} className="flex items-center gap-3 p-4">
          <Skeleton className="size-11 shrink-0 rounded-[14px]" />
          <div className="flex flex-1 flex-col gap-1.5">
            <Skeleton className="h-4 w-1/2" />
            <Skeleton className="h-3.5 w-1/3" />
          </div>
        </Card>
      ))}
    </SkeletonGroup>
  );
}
