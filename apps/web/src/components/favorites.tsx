"use client";
import { useEffect, useState } from "react";
import { type Favorites as FavoritesPage, type FavoriteSession, type StatusGroup, calendarDay, dayLabel } from "@/lib/models";
import { RequestError } from "@/lib/client";
import { useAuth } from "@/features/auth/model/auth-context";
import { favorites as listFavorites, programSeen, sessionFavorites as listSessionFavorites } from "@/features/favorites/api/favorites-client";
import { FavoriteHeart } from "@/features/favorites/ui/favorite-heart";
import { cn } from "@/shared/lib/utils";
import { Button } from "@/shared/ui/button";
import { Card } from "@/shared/ui/card";
import { CenteredMessage } from "@/shared/ui/centered-message";
import { Icon } from "@/shared/ui/icon";
import { InsetGroup } from "@/shared/ui/inset-group";
import { LargeTitle } from "@/shared/ui/nav-title";
import { Skeleton, SkeletonGroup } from "@/shared/ui/skeleton";
import { Failure } from "@/shared/ui/states";
import { ActivityCard } from "@/widgets/activity/activity-card";
import { MyTimetableList } from "@/widgets/activity/my-timetable";
import { cardProps, ProgramRow } from "./discovery";

/**
 * `/favorites` (contract "즐겨찾기(하트)", web "웹 화면 — 2단계 A"): the signed-in account's hearts. On top the `내 시간표 · 세션 n개` row
 * that opens the favorited sessions grouped by activity and day as 나만의 시간표; then the favorited programs as discovery rows (expand
 * for their open activities; `새 모집` turns off on the first open) and the favorited activities as cards in the server's order
 * (nearest deadline first, past ones last) — the web has no calendar view. Unfavoriting here flips the heart in place; the row goes
 * on the next load. Signed out: the sign-in CTA; without consent: 동의하기 like `/settings`.
 */
export function Favorites() {
  const auth = useAuth();
  return (
    <>
      <LargeTitle>즐겨찾기</LargeTitle>
      {auth.status === "loading" ? (
        <Loading />
      ) : !auth.signedIn ? (
        <CenteredMessage
          icon="heart"
          title="로그인하면 즐겨찾기가 모여요"
          message="프로그램·활동의 하트와 세션 찜을 이 계정에 모아 둘 수 있어요."
          primary={{ label: "로그인", onClick: auth.openSignIn }}
          secondary={{ label: "활동 둘러보기", href: "/" }}
        />
      ) : auth.consentRequired ? (
        <InsetGroup plain footer="동의하기 전에는 계정 기능을 쓸 수 없어요. 동의하지 않으면 로그인은 유지되고 다음에 다시 물어요.">
          <div className="flex flex-col gap-3 py-1">
            <p className="text-headline font-semibold">동의가 필요해요</p>
            <Button size="lg" onClick={auth.openConsent}>
              동의하기
            </Button>
          </div>
        </InsetGroup>
      ) : (
        <Content />
      )}
    </>
  );
}

function Content() {
  const { epoch, signedOutLocally } = useAuth();
  const [page, setPage] = useState<FavoritesPage>();
  const [sessions, setSessions] = useState<FavoriteSession[]>();
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const [now, setNow] = useState(Date.now);
  useEffect(() => {
    let cancelled = false;
    Promise.all([listFavorites(), listSessionFavorites()]).then(
      ([found, mine]) => {
        if (cancelled) return;
        setPage(found);
        setSessions(mine.items);
        setNow(Date.now());
      },
      (caught) => {
        if (cancelled) return;
        if (caught instanceof RequestError && caught.status === 401) signedOutLocally("로그인이 만료됐어요. 다시 로그인해 주세요.");
        else setError("즐겨찾기를 불러오지 못했어요.");
      },
    );
    return () => {
      cancelled = true;
    };
  }, [attempt, epoch, signedOutLocally]);
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
  if (!page || !sessions) return <Loading />;
  if (!page.programs.length && !page.activities.length && !sessions.length)
    return (
      <CenteredMessage
        icon="heart"
        title="하트를 누르면 여기에 모여요"
        message="프로그램·활동의 하트와 시간표의 세션 찜이 이곳에 모여요."
        primary={{ label: "활동 둘러보기", href: "/" }}
      />
    );
  return (
    <>
      {sessions.length > 0 && <MySessions items={sessions} />}
      {page.programs.length > 0 && (
        <section className="mt-7">
          <SectionTitle>프로그램</SectionTitle>
          <ul className="flex flex-col gap-2.5">
            {page.programs.map((item) => (
              <FavoriteProgram key={item.program.id} program={item.program} newRecruitment={item.newRecruitment} />
            ))}
          </ul>
        </section>
      )}
      {page.activities.length > 0 && (
        <section className="mt-7">
          <SectionTitle>활동</SectionTitle>
          <ul className="flex flex-col gap-2">
            {page.activities.map(({ activity, program }) => (
              <li key={activity.id}>
                <Card className="p-1.5">
                  <ActivityCard {...cardProps(activity, now)} subtitle={program.name} />
                </Card>
              </li>
            ))}
          </ul>
        </section>
      )}
    </>
  );
}

// Stable filter values for the reused discovery row (fresh literals would rerun its activity effect on every render).
const AVAILABLE: StatusGroup[] = ["available"];
const NO_CATEGORIES: string[] = [];
/** A favorited program as the discovery row (지원 가능 activities on expansion); the first open reports `seen` and drops `새 모집`. */
function FavoriteProgram({ program, newRecruitment }: { program: FavoritesPage["programs"][number]["program"]; newRecruitment: boolean }) {
  const [open, setOpen] = useState(false);
  const [fresh, setFresh] = useState(newRecruitment);
  return (
    <ProgramRow
      program={program}
      open={open}
      toggle={() => {
        if (!open && fresh) {
          setFresh(false);
          programSeen(program.id).catch(() => {});
        }
        setOpen((value) => !value);
      }}
      groups={AVAILABLE}
      categories={NO_CATEGORIES}
      newRecruitment={fresh}
    />
  );
}

type DayGroup = { key: string; activityId: number; activityName: string; timeZone: string; items: FavoriteSession[] };
/** Activities in the order of their first session, then their days in order (same grouping as iOS `MySessionsPage`). */
function dayGroups(items: FavoriteSession[]): DayGroup[] {
  const groups: DayGroup[] = [];
  for (const item of [...items].sort((a, b) => Date.parse(a.session.startAt) - Date.parse(b.session.startAt))) {
    const timeZone = item.schedule.timeZone ?? "Asia/Seoul";
    const key = `${item.activityId}|${calendarDay(Date.parse(item.session.startAt), timeZone)}`;
    const group = groups.find((candidate) => candidate.key === key);
    if (group) group.items.push(item);
    else groups.push({ key, activityId: item.activityId, activityName: item.activityName, timeZone, items: [item] });
  }
  // Keep every day of one activity together, activities by their first session.
  const order = [...new Set(groups.map((group) => group.activityId))];
  return groups.sort((a, b) => order.indexOf(a.activityId) - order.indexOf(b.activityId));
}

/**
 * `내 시간표 · 세션 n개` (contract "트랙·세션 시간표" › 활동 밖 찜한 세션): a disclosure row; opened, every activity's days as 나만의 시간표 drawn from
 * the list items' own `track` and `schedule` (no timetable is read again).
 */
function MySessions({ items }: { items: FavoriteSession[] }) {
  const [open, setOpen] = useState(false);
  const panelId = "my-timetable";
  return (
    <section className="mt-5">
      <Card>
        <button
          type="button"
          aria-expanded={open}
          aria-controls={panelId}
          onClick={() => setOpen((value) => !value)}
          className="flex min-h-[52px] w-full items-center gap-3 px-4 py-3 text-left active:bg-fill"
        >
          <Icon name="calendar" tone="teal" />
          <span className="flex-1 text-headline font-semibold">
            내 시간표 <span className="text-secondary-label">· 세션 {items.length}개</span>
          </span>
          <span className={cn("flex text-secondary-label transition-transform", open && "rotate-180")}>
            <Icon name="chevron-down" size="inline" />
          </span>
        </button>
        <div id={panelId} hidden={!open} className="flex flex-col gap-5 bg-background px-3 pt-3 pb-4">
          {dayGroups(items).map((group) => (
            <section key={group.key} className="flex flex-col gap-2">
              <h3 className="flex flex-col px-1">
                <span className="text-subheadline font-semibold break-keep">{group.activityName}</span>
                <span className="text-footnote text-secondary-label">{dayLabel(Date.parse(group.items[0].session.startAt), group.timeZone)}</span>
              </h3>
              <MyTimetableList
                items={group.items.map((item) => ({ session: item.session, track: item.track ?? null }))}
                timeZone={group.timeZone}
                favorite={(entry) => <FavoriteHeart kind="session" id={entry.session.id} favorited activityId={group.activityId} size="sm" />}
              />
            </section>
          ))}
        </div>
      </Card>
    </section>
  );
}

function SectionTitle({ children }: { children: React.ReactNode }) {
  return <h2 className="mb-1.5 px-4 text-footnote font-semibold text-secondary-label">{children}</h2>;
}

function Loading() {
  return (
    <SkeletonGroup className="mt-5 flex flex-col gap-2.5">
      {[0, 1, 2].map((key) => (
        <Card key={key} className="flex items-start gap-3 p-4">
          <Skeleton className="size-11 rounded-[14px]" />
          <div className="flex flex-1 flex-col gap-2 pt-1">
            <Skeleton className="h-4 w-2/3" />
            <Skeleton className="h-3.5 w-1/3" />
          </div>
        </Card>
      ))}
    </SkeletonGroup>
  );
}
