"use client";
import { useEffect, useRef, useState } from "react";
import { type Recommendation, type Tag, recommendationListSchema, tagListSchema } from "@/lib/models";
import { request } from "@/lib/client";
import {
  type RecommendationPreferences,
  funnelCategories,
  recommendationsPath,
  savePreferences,
} from "@/lib/recommendation";
import { cn } from "@/shared/lib/utils";
import { Button } from "@/shared/ui/button";
import { Card } from "@/shared/ui/card";
import { Icon } from "@/shared/ui/icon";
import { Skeleton, SkeletonGroup } from "@/shared/ui/skeleton";
import { RecommendationRow } from "@/widgets/recommendation/recommendation-row";

const order = ["intro", "fields", "categories", "traits", "result"] as const;
type Step = (typeof order)[number];
type Choice = { id: number | string; label: string; name?: string };
type Group = { title: string; choices: Choice[] };
const none: RecommendationPreferences = { fieldTagIds: [], categories: [], traitTagIds: [], completedAt: null };

/** 분야 tags grouped under their 상위 분야 (the parent itself as `전체`); 분야 without a parent or children go last as `그 밖에`. */
function fieldGroups(tags: Tag[]): Group[] {
  const fields = tags.filter((tag) => tag.kind === "FIELD");
  const parents = fields.filter((tag) => !tag.parentId && fields.some((child) => child.parentId === tag.id));
  const loose = fields.filter((tag) => !parents.includes(tag) && !parents.some((parent) => parent.id === tag.parentId));
  return [
    ...parents.map((parent) => ({
      title: parent.name,
      choices: [
        { id: parent.id, label: "전체", name: `${parent.name} 전체` },
        ...fields.filter((tag) => tag.parentId === parent.id).map((tag) => ({ id: tag.id, label: tag.name })),
      ],
    })),
    ...(loose.length ? [{ title: parents.length ? "그 밖에" : "분야", choices: loose.map((tag) => ({ id: tag.id, label: tag.name })) }] : []),
  ];
}

/**
 * 추천 설정 퍼널 (contract "추천 설정 퍼널(발견 탭 첫 진입)", 시안 F): one question per screen with a progress bar, `건너뛰기`, and a
 * large button at the bottom. No personal information is asked. Reaching the result (also by skipping) or `나중에` records it as
 * completed in this browser. `initial` (re-opened from 설정) starts at the first question with the saved picks.
 */
export function RecommendationFunnel({
  initial,
  onClose,
}: {
  initial: RecommendationPreferences | null;
  onClose: () => void;
}) {
  const [step, setStep] = useState<Step>(initial ? "fields" : "intro");
  const [picks, setPicks] = useState(initial ?? none);
  const [tags, setTags] = useState<Tag[]>();
  const [result, setResult] = useState<Recommendation[] | "failed">();
  const heading = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    const controller = new AbortController();
    request<unknown>("/tags", { signal: controller.signal })
      .then((json) => setTags(tagListSchema.parse(json).items))
      // Without the dictionary the 분야·성격 questions are left out; the funnel still works.
      .catch(() => !controller.signal.aborted && setTags([]));
    return () => controller.abort();
  }, []);
  useEffect(() => {
    if (step !== "result") return;
    const controller = new AbortController();
    request<unknown>(recommendationsPath(picks, 100), { signal: controller.signal })
      .then((json) => setResult(recommendationListSchema.parse(json).items))
      .catch(() => !controller.signal.aborted && setResult("failed"));
    return () => controller.abort();
  }, [step, picks]);
  // A new question moves focus to its title so screen readers read it.
  useEffect(() => heading.current?.focus(), [step]);

  const groups = tags ? fieldGroups(tags) : undefined;
  const traits = tags?.filter((tag) => tag.kind !== "FIELD");
  const steps = order.filter(
    (name) =>
      (name !== "intro" || !initial) &&
      (name !== "fields" || !groups || groups.length > 0) &&
      (name !== "traits" || !traits || traits.length > 0),
  );
  const index = steps.indexOf(step);
  // The dictionary arrived without 분야 or 성격 tags while that question was showing: move on to the next one that exists.
  if (index < 0) setStep(steps.find((name) => order.indexOf(name) > order.indexOf(step)) ?? "result");
  function go(to: number) {
    const next = steps[to];
    if (!next) return onClose();
    if (next === "result") {
      setResult(undefined);
      savePreferences({ ...picks, completedAt: new Date().toISOString() });
    }
    window.scrollTo(0, 0);
    setStep(next);
  }
  function later() {
    savePreferences({ ...none, completedAt: new Date().toISOString() });
    onClose();
  }
  function toggle(key: "fieldTagIds" | "categories" | "traitTagIds", id: number | string) {
    setPicks((current) => {
      const list = current[key] as (number | string)[];
      return { ...current, [key]: list.includes(id) ? list.filter((value) => value !== id) : [...list, id] };
    });
  }
  const labels = [
    ...(tags ?? []).filter((tag) => picks.fieldTagIds.includes(tag.id) || picks.traitTagIds.includes(tag.id)).map((tag) => tag.name),
    ...funnelCategories.filter(([code]) => picks.categories.includes(code)).map(([, label]) => label),
  ];
  const found = Array.isArray(result) ? result : undefined;
  const copy: Record<Step, { title: string; sub: string; cta: string }> = {
    intro: { title: "나에게 맞는 활동을\n찾아 드릴게요", sub: "질문 3개면 끝나요.\n답하지 않은 건 그냥 넘어가도 돼요.", cta: "시작하기" },
    fields: { title: "어떤 분야에\n관심 있어요?", sub: "여러 개 골라도 돼요.", cta: "다음" },
    categories: { title: "어떤 활동을\n찾고 있어요?", sub: "여러 개 골라도 돼요.", cta: "다음" },
    traits: { title: "활동하면서\n무엇을 얻고 싶어요?", sub: "고르지 않아도 괜찮아요.", cta: "다음" },
    result: {
      title: !result
        ? "활동을 찾고 있어요"
        : result === "failed"
          ? "설정을 마쳤어요"
          : result.length
            ? `지금 지원할 수 있는\n활동 ${result.length}개를 찾았어요`
            : "지금 지원할 수 있는\n활동을 찾지 못했어요",
      sub: found && !found.length ? "설정을 바꾸거나 전체 목록에서 찾아보세요." : labels.join(" · "),
      cta: "추천 보러 가기",
    },
  };
  const { title, sub, cta } = copy[step];
  const showBack = index > 0 || Boolean(initial);
  return (
    <section aria-labelledby="funnel-title" className="flex min-h-[calc(100dvh-44px-env(safe-area-inset-top))] flex-col">
      <div className="-mx-2 flex min-h-11 items-center justify-between">
        <button
          type="button"
          aria-label={index > 0 ? "이전 질문" : "닫기"}
          onClick={() => go(index - 1)}
          className={cn("grid size-11 place-items-center text-label", !showBack && "invisible")}
        >
          <Icon name="chevron-left" />
        </button>
        {step !== "result" && (
          <button
            type="button"
            onClick={step === "intro" ? later : () => go(index + 1)}
            className="min-h-11 px-3 text-subheadline text-secondary-label"
          >
            {step === "intro" ? "나중에" : "건너뛰기"}
          </button>
        )}
      </div>
      <div
        role="progressbar"
        aria-label="추천 설정 진행"
        aria-valuemin={0}
        aria-valuemax={steps.length - 1}
        aria-valuenow={index}
        className="mt-1 h-1 overflow-hidden rounded-full bg-fill"
      >
        <div
          className="h-full rounded-full bg-teal-fill transition-[width] duration-300"
          style={{ width: `${(index / (steps.length - 1)) * 100}%` }}
        />
      </div>
      <div className="flex flex-1 flex-col gap-2 pt-7 pb-4">
        <h1
          id="funnel-title"
          ref={heading}
          tabIndex={-1}
          className="text-[26px] leading-[1.35] font-bold tracking-tight break-keep whitespace-pre-line outline-none"
        >
          {title}
        </h1>
        {sub && <p className="mb-4 text-subheadline whitespace-pre-line text-secondary-label">{sub}</p>}
        {step === "fields" &&
          (groups ? (
            groups.map((group) => (
              <Choices key={group.title} group={group} picked={picks.fieldTagIds} toggle={(id) => toggle("fieldTagIds", id)} />
            ))
          ) : (
            <SkeletonGroup className="flex flex-wrap gap-2">
              {[0, 1, 2, 3, 4].map((key) => (
                <Skeleton key={key} className="h-10 w-20 rounded-full" />
              ))}
            </SkeletonGroup>
          ))}
        {step === "categories" && (
          <Choices
            group={{ title: "활동 종류", choices: funnelCategories.map(([id, label]) => ({ id, label })) }}
            picked={picks.categories}
            toggle={(id) => toggle("categories", id)}
          />
        )}
        {step === "traits" && traits && (
          <Choices
            group={{ title: "얻을 수 있는 것", choices: traits.map((tag) => ({ id: tag.id, label: tag.name })) }}
            picked={picks.traitTagIds}
            toggle={(id) => toggle("traitTagIds", id)}
          />
        )}
        {step === "result" && (
          <>
            {!result ? (
              <SkeletonGroup>
                <Card className="flex flex-col gap-3 p-4">
                  {[0, 1, 2].map((key) => (
                    <div key={key} className="flex items-center gap-3">
                      <Skeleton className="size-11 rounded-[14px]" />
                      <Skeleton className="h-4 flex-1" />
                    </div>
                  ))}
                </Card>
              </SkeletonGroup>
            ) : (
              found &&
              found.length > 0 && (
                <Card>
                  <ul className="divide-y divide-separator">
                    {found.slice(0, 3).map((item) => (
                      <li key={item.activity.id}>
                        <RecommendationRow item={item} />
                      </li>
                    ))}
                  </ul>
                </Card>
              )
            )}
            {/* The web has no sign-in or profile, so 지원 자격 정보 is entered in the app (contract "지원 자격 정보"). */}
            <p className="mt-3 text-footnote text-secondary-label break-keep">
              지원 자격까지 맞추려면 Dearby 앱의 프로필 › 지원 자격 정보를 입력해요.
            </p>
          </>
        )}
      </div>
      <div className="sticky bottom-0 -mx-4 bg-background px-4 pt-2 pb-[max(16px,env(safe-area-inset-bottom))]">
        <Button size="lg" onClick={() => go(index + 1)}>
          {cta}
        </Button>
      </div>
    </section>
  );
}

/** One titled group of multi-select chips; each chip is a toggle button. */
function Choices({
  group,
  picked,
  toggle,
}: {
  group: Group;
  picked: (number | string)[];
  toggle: (id: number | string) => void;
}) {
  return (
    <section className="mb-4 flex flex-col gap-2.5">
      <h2 className="text-footnote font-semibold text-secondary-label">{group.title}</h2>
      <div className="flex flex-wrap gap-2">
        {group.choices.map((choice) => {
          const on = picked.includes(choice.id);
          return (
            <button
              key={choice.id}
              type="button"
              aria-pressed={on}
              aria-label={choice.name}
              onClick={() => toggle(choice.id)}
              className={cn(
                "min-h-10 rounded-full border-[1.5px] px-3.5 text-subheadline font-medium transition-colors",
                on ? "border-teal bg-mint text-teal" : "border-separator bg-card text-label",
              )}
            >
              {choice.label}
            </button>
          );
        })}
      </div>
    </section>
  );
}
