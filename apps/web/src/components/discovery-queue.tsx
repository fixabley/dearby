"use client";
import * as DialogPrimitive from "@radix-ui/react-dialog";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useEffectEvent, useRef, useState } from "react";
import { categoryLabel } from "@/lib/category";
import { RequestError, errorMessage, request } from "@/lib/client";
import {
  type QueuePosition,
  type QueueSignals,
  fieldTagsOfItem,
  isShown,
  nextRound,
  readPosition,
  saveSignals,
  savePosition,
  toggled,
  useQueueSignals,
} from "@/lib/discovery-queue";
import { eligibilityLines } from "@/lib/eligibility";
import {
  type Highlight,
  type Recommendation,
  daysToDeadline,
  programCoverSrc,
  programLogoSrc,
  recruitmentPhrase,
  scheduleLabel,
  traitTagsOf,
} from "@/lib/models";
import { isEmpty, recommendationsPath, useRecommendationPreferences } from "@/lib/recommendation";
import { cn } from "@/shared/lib/utils";
import { Button } from "@/shared/ui/button";
import { CenteredMessage } from "@/shared/ui/centered-message";
import { Icon, type IconName } from "@/shared/ui/icon";
import { Sheet, SheetContent, SheetTitle } from "@/shared/ui/sheet";
import { Failure } from "@/shared/ui/states";
import { ProgramAvatar } from "@/widgets/activity/program-avatar";

const SCENE_MS = 4000;
const TOAST_MS = 5000;
// Taps closer than this make a double tap (관심 있어요) instead of two scene steps.
const DOUBLE_TAP_MS = 250;
// More candidates than one queue, so `더 받기` can make the next round from the rest (contract "대기열 만들기").
const CANDIDATES = 30;

const highlightGlyphs: Record<Highlight["icon"], IconName> = {
  cost: "won",
  period: "calendar",
  team: "network",
  mentor: "bubbles",
  award: "trophy",
  place: "map-pin",
  talk: "megaphone",
  ticket: "ticket",
};

type Scene = { kind: "hook" } | { kind: "highlights"; lines: Highlight[] } | { kind: "deadline" };

/**
 * One activity's scenes (contract "맞춤 대기열(스토리형 쇼츠)" › 콘텐츠): ① `hook` (else the activity name alone), ② `highlights`
 * (else 기간·장소·성격 from the public fields — 비용 is not public, so it is left out; the scene drops when nothing is left), ③ 마감.
 */
function scenesOf(item: Recommendation): Scene[] {
  const { activity } = item;
  const lines: Highlight[] = activity.highlights?.length
    ? activity.highlights.slice(0, 3)
    : (
        [
          { icon: "period", text: scheduleLabel(activity.activitySchedule, "activity") },
          { icon: "place", text: activity.place?.name ?? activity.location ?? "" },
          { icon: "award", text: traitTagsOf(activity).map((tag) => tag.name).join(" · ") },
        ] satisfies Highlight[]
      ).filter((line) => line.text);
  return [{ kind: "hook" }, ...(lines.length ? [{ kind: "highlights" as const, lines }] : []), { kind: "deadline" }];
}

/** `D-DAY`/`D-12`, or undefined without an end. */
function dDay(item: Recommendation, now: number): string | undefined {
  const days = daysToDeadline(item.activity.application, now);
  if (days === undefined || days < 0) return;
  return days === 0 ? "D-DAY" : `D-${days}`;
}

function deadlineLine(item: Recommendation, now: number): string {
  const { application, eligibility } = item.activity;
  return [recruitmentPhrase(application, now), eligibilityLines(eligibility)[0]].filter(Boolean).join(" · ");
}

/** `부트캠프 · 데이터·AI` above the hook. */
function kicker(item: Recommendation): string {
  const { category } = item.activity;
  return [category ? categoryLabel(category) : "", fieldTagsOfItem(item)[0]?.name ?? ""].filter(Boolean).join(" · ");
}

type Choice = { label: string; toast: string; field: keyof Omit<QueueSignals, "liked">; id: number };

/** The `⋯` sheet (contract "맞춤 대기열" › 화면): 관심 없어요, one 덜 보기 row per 분야, 프로그램 숨기기, 지원 자격이 안 돼요. */
function choicesOf(item: Recommendation): Choice[] {
  return [
    { label: "관심 없어요", toast: "관심 없어요로 표시했어요", field: "dismissed", id: item.activity.id },
    ...fieldTagsOfItem(item).map((tag) => ({
      label: `‘${tag.name}’ 분야는 덜 보기`,
      toast: `‘${tag.name}’ 분야를 덜 보여 드릴게요`,
      field: "lessFields" as const,
      id: tag.id,
    })),
    { label: "이 프로그램 숨기기", toast: "이 프로그램을 숨겼어요", field: "hiddenPrograms", id: item.program.id },
    { label: "지원 자격이 안 돼요", toast: "지원 자격이 안 되는 활동으로 표시했어요", field: "ineligible", id: item.activity.id },
  ];
}

/**
 * 오늘의 맞춤 대기열 for a browser without an account (contract "맞춤 대기열(스토리형 쇼츠)" › 대기열 만들기): the recommendation
 * API's results as full-screen story cards, reactions kept on this device only. A full-screen modal layer over the shell, so
 * focus, Escape and page scroll behave like the app's other sheets.
 */
export function DiscoveryQueue() {
  const router = useRouter();
  const preferences = useRecommendationPreferences();
  const signals = useQueueSignals();
  const [items, setItems] = useState<Recommendation[] | "missing">();
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const [position, setPosition] = useState<QueuePosition>();
  const skipped = !preferences || isEmpty(preferences);
  const path = preferences ? recommendationsPath(preferences, CANDIDATES) : "";
  useEffect(() => {
    if (skipped) return;
    const controller = new AbortController();
    request<{ items: Recommendation[] }>(path, { signal: controller.signal })
      .then((json) => setItems(json.items))
      .catch((error) => {
        if (controller.signal.aborted) return;
        if (error instanceof RequestError && [401, 404].includes(error.status)) setItems("missing");
        else setError(errorMessage(error));
      });
    return () => controller.abort();
  }, [skipped, path, attempt]);
  // The first round once both the candidates and this browser's reactions are known; a saved position of this tab resumes.
  if (Array.isArray(items) && signals && !position) {
    const saved = readPosition();
    const known = saved?.round.every((id) => items.some((item) => item.activity.id === id));
    setPosition(known ? saved : { round: nextRound(items, signals, []), seen: [], index: 0 });
  }
  useEffect(() => {
    if (position) savePosition(position);
  }, [position]);

  const ready = Array.isArray(items) && signals && position;
  return (
    <DialogPrimitive.Root open onOpenChange={(open) => !open && router.push("/")}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Content
          aria-describedby={undefined}
          className={cn("fixed inset-0 z-40 outline-none", ready ? "bg-black text-white" : "bg-background text-label")}
        >
          <DialogPrimitive.Title className="sr-only">오늘의 맞춤 대기열</DialogPrimitive.Title>
          {ready ? (
            <Pager items={items} signals={signals} position={position} setPosition={setPosition} />
          ) : preferences === undefined || (!skipped && !items && !error) ? (
            <p role="status" className="grid h-full place-items-center text-subheadline text-secondary-label">
              대기열을 만들고 있어요
            </p>
          ) : (
            <div className="grid h-full place-items-center px-4">
              {error ? (
                <Failure
                  message={error}
                  retry={() => {
                    setError("");
                    setAttempt((value) => value + 1);
                  }}
                />
              ) : (
                <CenteredMessage
                  icon="sparkles"
                  title="아직 대기열이 없어요"
                  message="둘러보기에서 추천 설정을 마치면 맞춤 대기열을 볼 수 있어요."
                  primary={{ label: "둘러보기로 가기", onClick: () => router.push("/") }}
                />
              )}
            </div>
          )}
          <DialogPrimitive.Close
            aria-label="닫기"
            className={cn(
              "fixed top-[max(12px,env(safe-area-inset-top))] right-3 z-10 grid size-11 place-items-center rounded-full",
              ready ? "bg-black/35 text-white" : "bg-fill",
            )}
          >
            <Icon name="close" />
          </DialogPrimitive.Close>
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
}

function Pager({
  items,
  signals,
  position,
  setPosition,
}: {
  items: Recommendation[];
  signals: QueueSignals;
  position: QueuePosition;
  setPosition: (position: QueuePosition) => void;
}) {
  const [now] = useState(Date.now);
  const shown = position.round
    .map((id) => items.find((item) => item.activity.id === id))
    .filter((item): item is Recommendation => !!item && isShown(item, signals));
  const { index } = position;
  const current = shown[index] as Recommendation | undefined;
  const scenes = current ? scenesOf(current) : [];
  // Scene of the card in view; a different card (scrolled to, or slid in after a 숨기기) starts at ①.
  const [sceneOf, setSceneOf] = useState({ id: 0, scene: 0 });
  const scene = current && sceneOf.id === current.activity.id ? sceneOf.scene : 0;
  const [userPaused, setUserPaused] = useState(false);
  const [sheet, setSheet] = useState(false);
  const [toast, setToast] = useState<Choice>();
  const [burst, setBurst] = useState(0);
  const scroller = useRef<HTMLDivElement | null>(null);
  const restored = useRef(false);
  const pendingTap = useRef<number | undefined>(undefined);
  const paused = userPaused || sheet || !current;
  const step = `${current?.activity.id}:${scene}`;
  const remaining = useRef({ step: "", ms: SCENE_MS });

  function goTo(target: number) {
    const element = scroller.current;
    if (!element) return;
    const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    element.scrollTo({ top: Math.max(0, target) * element.clientHeight, behavior: reduce ? "auto" : "smooth" });
  }
  function stepScene(delta: 1 | -1) {
    // The last scene stays (all segments full) until the viewer moves on with a swipe, ↓ or 다음.
    if (!current || scene + delta >= scenes.length) return;
    setSceneOf({ id: current.activity.id, scene: Math.max(0, scene + delta) });
  }
  function like(item: Recommendation, on: boolean) {
    saveSignals({ ...signals, liked: toggled(signals.liked, item.activity.id, on) });
  }
  // Single taps step a scene after a short wait; a second tap inside it is 관심 있어요 with the heart burst instead.
  function tap(delta: 1 | -1) {
    if (pendingTap.current !== undefined) {
      clearTimeout(pendingTap.current);
      pendingTap.current = undefined;
      if (current) {
        like(current, true);
        setBurst((value) => value + 1);
      }
      return;
    }
    pendingTap.current = window.setTimeout(() => {
      pendingTap.current = undefined;
      stepScene(delta);
    }, DOUBLE_TAP_MS);
  }
  function choose(choice: Choice) {
    saveSignals({ ...signals, [choice.field]: toggled(signals[choice.field], choice.id, true) });
    setSheet(false);
    setToast(choice);
    // 덜 보기 keeps this card (덜, not 안), so move on; the others drop it and the next card slides into place.
    if (choice.field === "lessFields") goTo(index + 1);
  }

  // Auto-advance: 4s per scene, stopping on the last one. Pausing keeps the time left, like the paused bar.
  const sceneEnded = useEffectEvent(() => stepScene(1));
  useEffect(() => {
    if (paused) return;
    if (remaining.current.step !== step) remaining.current = { step, ms: SCENE_MS };
    const started = Date.now();
    const timer = setTimeout(sceneEnded, remaining.current.ms);
    return () => {
      clearTimeout(timer);
      remaining.current.ms -= Date.now() - started;
    };
  }, [paused, step]);
  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => setToast(undefined), TOAST_MS);
    return () => clearTimeout(timer);
  }, [toast]);
  useEffect(() => () => clearTimeout(pendingTap.current), []);

  const roundIds = [...position.seen, ...position.round];
  const more = nextRound(items, signals, roundIds).length > 0;
  return (
    <div
      className="h-full"
      onKeyDown={(event) => {
        if (sheet || event.altKey || event.metaKey || event.ctrlKey) return;
        const action = {
          ArrowLeft: () => stepScene(-1),
          ArrowRight: () => stepScene(1),
          ArrowUp: () => goTo(index - 1),
          ArrowDown: () => goTo(index + 1),
        }[event.key];
        if (!action) return;
        event.preventDefault();
        action();
      }}
    >
      <div
        ref={(element) => {
          scroller.current = element;
          // Resume where this tab left off (back from 자세히) without animating.
          if (element && !restored.current) {
            restored.current = true;
            element.scrollTop = index * element.clientHeight;
          }
        }}
        onScroll={(event) => {
          const element = event.currentTarget;
          const next = Math.round(element.scrollTop / element.clientHeight);
          if (next !== index) setPosition({ ...position, index: next });
        }}
        className="h-full snap-y snap-mandatory overflow-y-auto overscroll-contain"
      >
        {shown.map((item, at) => (
          <Story
            key={item.activity.id}
            item={item}
            at={at}
            total={shown.length}
            now={now}
            active={at === index}
            scene={at === index ? scene : 0}
            scenes={at === index ? scenes : scenesOf(item)}
            step={step}
            paused={paused}
            liked={signals.liked.includes(item.activity.id)}
            burst={at === index ? burst : 0}
            onTap={tap}
            onLike={() => like(item, !signals.liked.includes(item.activity.id))}
            onNext={() => goTo(at + 1)}
            onMore={() => setSheet(true)}
          />
        ))}
        <section aria-label="대기열 끝" className="grid h-full snap-start snap-always place-items-center px-6">
          <div className="flex flex-col items-center gap-4 text-center">
            <Icon name="check-circle" size="state" />
            <h2 className="text-title2 font-bold">오늘 대기열을 다 봤어요</h2>
            <p className="text-subheadline text-white/70 break-keep">
              {more ? "남은 추천 활동으로 대기열을 하나 더 만들 수 있어요." : "지금 추천할 활동은 모두 보여 드렸어요."}
            </p>
            {more && (
              <Button
                onClick={() => {
                  setPosition({ round: nextRound(items, signals, roundIds), seen: roundIds, index: 0 });
                  scroller.current?.scrollTo({ top: 0 });
                }}
              >
                더 받기
              </Button>
            )}
            <Button asChild variant="plain" className="text-white">
              <Link href="/">둘러보기로 돌아가기</Link>
            </Button>
          </div>
        </section>
      </div>
      <Button
        variant="plain"
        size="icon"
        aria-label={userPaused ? "자동 넘김 계속" : "자동 넘김 멈춤"}
        aria-pressed={userPaused}
        onClick={() => setUserPaused((value) => !value)}
        className={cn("fixed top-[max(12px,env(safe-area-inset-top))] right-16 bg-black/35 text-white", !current && "hidden")}
      >
        <Icon name={userPaused ? "play" : "pause"} />
      </Button>
      {current && (
        <Sheet open={sheet} onOpenChange={setSheet}>
          <SheetContent aria-describedby={undefined}>
            <SheetTitle className="mb-2 px-1 break-keep">{current.activity.name}</SheetTitle>
            <ul className="flex flex-col">
              {choicesOf(current).map((choice) => (
                <li key={`${choice.field}:${choice.id}`}>
                  <button
                    type="button"
                    onClick={() => choose(choice)}
                    className="min-h-11 w-full rounded-control px-1 py-2.5 text-left text-body active:bg-fill"
                  >
                    {choice.label}
                  </button>
                </li>
              ))}
            </ul>
          </SheetContent>
        </Sheet>
      )}
      <div
        role="status"
        className="pointer-events-none fixed inset-x-4 bottom-[max(16px,env(safe-area-inset-bottom))] z-10 mx-auto flex max-w-sm justify-center"
      >
        {toast && (
          <div className="pointer-events-auto flex w-full items-center gap-3 rounded-[14px] bg-neutral-800/95 py-1 pr-1 pl-4 text-subheadline text-white shadow-lg">
            <span className="flex-1 break-keep">{toast.toast}</span>
            <Button
              variant="plain"
              size="sm"
              className="text-[#7fe3e3]"
              onClick={() => {
                saveSignals({ ...signals, [toast.field]: toggled(signals[toast.field], toast.id, false) });
                setToast(undefined);
              }}
            >
              되돌리기
            </Button>
          </div>
        )}
      </div>
    </div>
  );
}

/** Ken Burns cover (contract "커버 이미지"): the program cover, else its logo on the teal gradient. */
function Cover({ item, active }: { item: Recommendation; active: boolean }) {
  const cover = programCoverSrc(item.program.coverUrl);
  const logo = programLogoSrc(item.program.logoUrl ?? item.activity.program.logoUrl);
  const [failed, setFailed] = useState<string[]>([]);
  return (
    <div aria-hidden="true" className={cn("absolute -inset-[6%]", active && "queue-kb")}>
      {cover && !failed.includes(cover) ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={cover} alt="" loading="lazy" decoding="async" className="size-full object-cover" onError={() => setFailed([...failed, cover])} />
      ) : (
        <div className="flex size-full justify-center bg-[linear-gradient(160deg,var(--teal-fill),#003a3b_60%,#000)] pt-[30%]">
          {logo && !failed.includes(logo) && (
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={logo}
              alt=""
              loading="lazy"
              decoding="async"
              className="size-32 rounded-[28px] bg-white object-contain p-3"
              onError={() => setFailed([...failed, logo])}
            />
          )}
        </div>
      )}
    </div>
  );
}

function Story({
  item,
  at,
  total,
  now,
  active,
  scene,
  scenes,
  step,
  paused,
  liked,
  burst,
  onTap,
  onLike,
  onNext,
  onMore,
}: {
  item: Recommendation;
  at: number;
  total: number;
  now: number;
  active: boolean;
  scene: number;
  scenes: Scene[];
  step: string;
  paused: boolean;
  liked: boolean;
  burst: number;
  onTap: (delta: 1 | -1) => void;
  onLike: () => void;
  onNext: () => void;
  onMore: () => void;
}) {
  const { activity, program, reasons } = item;
  const logo = programLogoSrc(program.logoUrl);
  const shownScene = scenes[scene];
  const days = dDay(item, now);
  const highlights = scenes.find((value) => value.kind === "highlights");
  return (
    <section
      aria-roledescription="활동 카드"
      aria-label={`${at + 1}/${total} ${activity.name}`}
      className="relative h-full snap-start snap-always overflow-hidden"
    >
      <div className="relative mx-auto h-full max-w-[480px] overflow-hidden">
        <Cover item={item} active={active} />
        <div
          aria-hidden="true"
          className="absolute inset-0 bg-[linear-gradient(to_bottom,rgb(0_0_0/0.45),rgb(0_0_0/0.05)_30%,rgb(0_0_0/0.1)_55%,rgb(0_0_0/0.85))]"
        />
        {/* Screen readers get the whole card at once instead of the timed scenes. */}
        <div className="sr-only">
          <h2>{activity.name}</h2>
          <p>{program.name}</p>
          {activity.hook && <p>{activity.hook}</p>}
          {highlights?.kind === "highlights" && (
            <ul>
              {highlights.lines.map((line) => (
                <li key={line.text}>{line.text}</li>
              ))}
            </ul>
          )}
          <p>{[days && `마감까지 ${days}`, deadlineLine(item, now)].filter(Boolean).join(", ")}</p>
          {reasons.length > 0 && <p>추천 이유: {reasons.join(", ")}</p>}
        </div>
        <div aria-hidden="true" className="absolute inset-x-3 top-[max(12px,env(safe-area-inset-top))] flex gap-1">
          {scenes.map((value, key) => (
            <span key={key} className="h-[3px] flex-1 overflow-hidden rounded-full bg-white/35">
              {key < scene ? (
                <span className="block size-full bg-white" />
              ) : (
                key === scene &&
                active && (
                  <span
                    key={step}
                    className="queue-fill block size-full bg-white"
                    style={{ animationPlayState: paused ? "paused" : "running" }}
                  />
                )
              )}
            </span>
          ))}
        </div>
        <header
          aria-hidden="true"
          className="absolute top-[calc(max(12px,env(safe-area-inset-top))+12px)] right-28 left-3.5 flex items-center gap-2 text-subheadline"
        >
          <ProgramAvatar key={logo ?? ""} name={program.name} src={logo} />
          <span className="min-w-0 truncate">
            <span className="font-semibold">{program.organization.name}</span>
            <span className="opacity-75">
              {" "}
              · 오늘의 맞춤 대기열 {at + 1}/{total}
            </span>
          </span>
        </header>
        <button
          type="button"
          aria-label="이전 장면"
          onClick={() => onTap(-1)}
          className="absolute top-28 bottom-52 left-0 w-[30%] touch-manipulation"
        />
        <button
          type="button"
          aria-label="다음 장면 (두 번 누르면 관심 있어요)"
          onClick={() => onTap(1)}
          className="absolute top-28 right-[76px] bottom-52 left-[30%] touch-manipulation"
        />
        {burst > 0 && (
          <span key={burst} aria-hidden="true" className="queue-burst pointer-events-none absolute top-[38%] left-1/2 -ml-[60px] text-[#ff4d6d]">
            <svg width="120" height="120" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 20s-7.5-4.5-7.5-10A4.3 4.3 0 0 1 12 7.5a4.3 4.3 0 0 1 7.5 2.5c0 5.5-7.5 10-7.5 10Z" />
            </svg>
          </span>
        )}
        <div
          aria-hidden="true"
          key={active ? step : "idle"}
          className={cn("pointer-events-none absolute right-[84px] bottom-12 left-[18px] flex flex-col gap-2.5", active && "queue-rise")}
        >
          {shownScene?.kind === "hook" && (
            <>
              {kicker(item) && <span className="text-subheadline font-semibold text-[#7fe3e3]">{kicker(item)}</span>}
              <span className="text-[30px] leading-tight font-extrabold tracking-[-0.5px] break-keep">
                {activity.hook || activity.name}
              </span>
              <span className="text-subheadline opacity-90 break-keep">{activity.hook ? activity.name : program.name}</span>
            </>
          )}
          {shownScene?.kind === "highlights" && (
            <>
              <span className="text-subheadline font-semibold text-[#7fe3e3]">이런 걸 해요</span>
              {shownScene.lines.map((line) => (
                <span key={line.text} className="flex items-center gap-2.5 text-[19px] leading-snug font-bold break-keep">
                  <span className="grid size-9 shrink-0 place-items-center rounded-full bg-white/20">
                    <Icon name={highlightGlyphs[line.icon]} size="inline" />
                  </span>
                  {line.text}
                </span>
              ))}
            </>
          )}
          {shownScene?.kind === "deadline" && (
            <>
              <span className="text-subheadline font-semibold text-[#7fe3e3]">마감까지</span>
              <span className="text-[64px] leading-none font-extrabold">{days ?? "상시"}</span>
              <span className="text-body opacity-90 break-keep">{deadlineLine(item, now)}</span>
              {reasons.length > 0 && (
                <span className="flex flex-wrap gap-1.5">
                  {reasons.map((reason) => (
                    <span key={reason} className="rounded-full bg-white/20 px-2.5 py-1 text-footnote font-semibold">
                      {reason}
                    </span>
                  ))}
                </span>
              )}
            </>
          )}
        </div>
        <nav aria-label="이 활동" className="absolute right-2.5 bottom-14 flex flex-col items-center gap-4 text-caption">
          <RailButton label="관심" pressed={liked} onClick={onLike} ariaLabel="관심 있어요">
            <span className={cn("flex", liked && "text-[#ff4d6d] [&_path]:fill-current")}>
              <Icon name="heart" />
            </span>
          </RailButton>
          <Link
            href={`/activities/${activity.id}`}
            aria-label="자세히 보기"
            className="flex min-w-14 flex-col items-center gap-1 active:opacity-70"
          >
            <RailCircle>
              <Icon name="info" />
            </RailCircle>
            자세히
          </Link>
          <RailButton label="다음" onClick={onNext} ariaLabel="다음 활동">
            <Icon name="chevron-up" />
          </RailButton>
          <RailButton label="" onClick={onMore} ariaLabel="더 보기">
            <Icon name="more" />
          </RailButton>
        </nav>
      </div>
    </section>
  );
}

function RailCircle({ children }: { children: React.ReactNode }) {
  return <span className="grid size-12 place-items-center rounded-full bg-black/35">{children}</span>;
}

function RailButton({
  label,
  ariaLabel,
  pressed,
  onClick,
  children,
}: {
  label: string;
  ariaLabel: string;
  pressed?: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      aria-label={ariaLabel}
      aria-pressed={pressed}
      onClick={onClick}
      className="flex min-w-14 flex-col items-center gap-1 active:opacity-70"
    >
      <RailCircle>{children}</RailCircle>
      {label}
    </button>
  );
}

