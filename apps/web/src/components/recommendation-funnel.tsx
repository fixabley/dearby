"use client";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import type { Recommendation, Tag } from "@/lib/models";
import { request } from "@/lib/client";
import {
  type RecommendationPreferences,
  funnelCategories,
  recommendationsPath,
  savePreferences,
} from "@/lib/recommendation";
import { cn } from "@/shared/lib/utils";
import { Button } from "@/shared/ui/button";
import { Icon } from "@/shared/ui/icon";
import { Skeleton, SkeletonGroup } from "@/shared/ui/skeleton";
import { TagIcon } from "@/shared/ui/tag-icon";

const order = ["intro", "fields", "subfields", "categories", "traits", "result"] as const;
type BaseStep = (typeof order)[number];
/** `sub:<parent id>` is one parent's 하위 분야 question; `subfields` holds them all on one screen (3+ parents picked). */
type Step = BaseStep | `sub:${number}`;
type Choice = { id: number | string; label: string; nameEn?: string | null };
type Group = { title?: string; choices: Choice[] };
const none: RecommendationPreferences = { fieldTagIds: [], categories: [], traitTagIds: [], completedAt: null };

/**
 * 분야 (contract "추천 퍼널 분야 단계(상위 → 하위)"): the first question lists 상위 분야 (fields with children) and fields without a
 * parent; each picked 상위 분야 then gets its children. `fieldTagIds` alone records both levels — a 상위 id means `전부 좋아요`, child
 * ids mean those only — so a saved list restores both questions (a child id implies its parent was picked).
 */
function fieldTree(tags: Tag[]) {
  const fields = tags.filter((tag) => tag.kind === "FIELD");
  const childrenOf = (id: number) => fields.filter((tag) => tag.parentId === id);
  const top = fields.filter((tag) => !tag.parentId || !fields.some((parent) => parent.id === tag.parentId));
  // 상위 분야 first, then the fields without children, each in dictionary order.
  const parent = (tag: Tag) => (childrenOf(tag.id).length ? 0 : 1);
  return { top: top.sort((a, b) => parent(a) - parent(b)), childrenOf };
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
  const router = useRouter();
  const [step, setStep] = useState<Step>(initial ? "fields" : "intro");
  const [picks, setPicks] = useState(initial ?? none);
  const [tags, setTags] = useState<Tag[]>();
  const [result, setResult] = useState<Recommendation[] | "failed">();
  const heading = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    const controller = new AbortController();
    request<{ items: Tag[] }>("/tags", { signal: controller.signal })
      .then((json) => setTags(json.items))
      // Without the dictionary the 분야·성격 questions are left out; the funnel still works.
      .catch(() => !controller.signal.aborted && setTags([]));
    return () => controller.abort();
  }, []);
  useEffect(() => {
    if (step !== "result") return;
    const controller = new AbortController();
    request<{ items: Recommendation[] }>(recommendationsPath(picks, 100), { signal: controller.signal })
      .then((json) => setResult(json.items))
      .catch(() => !controller.signal.aborted && setResult("failed"));
    return () => controller.abort();
  }, [step, picks]);
  // A new question moves focus to its title so screen readers read it.
  useEffect(() => heading.current?.focus(), [step]);

  const tree = tags ? fieldTree(tags) : undefined;
  const traits = tags?.filter((tag) => tag.kind !== "FIELD");
  // Picked 상위 분야 with children, in dictionary order: chosen as a whole (its id) or through any of its children.
  const pickedParents = (tree?.top ?? []).filter((tag) => {
    const children = tree!.childrenOf(tag.id);
    return children.length > 0 && [tag, ...children].some((each) => picks.fieldTagIds.includes(each.id));
  });
  const subSteps: Step[] = pickedParents.length >= 3 ? ["subfields"] : pickedParents.map((tag) => `sub:${tag.id}` as const);
  const steps = order.flatMap((name): Step[] =>
    name === "subfields"
      ? subSteps
      : (name !== "intro" || !initial) &&
          (name !== "fields" || !tree || tree.top.length > 0) &&
          (name !== "traits" || !traits || traits.length > 0)
        ? [name]
        : [],
  );
  const index = steps.indexOf(step);
  // The dictionary arrived without 분야 or 성격 tags while that question was showing: move on to the next one that exists.
  const rank = (name: Step) => order.indexOf(name.startsWith("sub") ? "subfields" : (name as BaseStep));
  if (index < 0) setStep(steps.find((name) => rank(name) > rank(step)) ?? "result");
  const found = Array.isArray(result) ? result : undefined;
  function go(to: number) {
    const next = steps[to];
    // The result opens 오늘의 맞춤 대기열 (contract "맞춤 대기열", 2026-10-25); with nothing found it returns to the list instead.
    if (!next) return found && !found.length ? onClose() : router.push("/queue");
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
  /** First 분야 question: picking a 상위 분야 starts at `전부 좋아요` (its id); unpicking drops its children too. */
  function toggleField(tag: Tag) {
    const family = [tag.id, ...(tree?.childrenOf(tag.id) ?? []).map((child) => child.id)];
    setPicks((current) => {
      const on = current.fieldTagIds.some((id) => family.includes(id));
      const rest = current.fieldTagIds.filter((id) => !family.includes(id));
      return { ...current, fieldTagIds: on ? rest : [...rest, tag.id] };
    });
  }
  /** 하위 분야 question: `전부 좋아요` (the parent id) and the children exclude each other; unpicking the last child returns to `전부`. */
  function toggleChild(parent: Tag, id: number) {
    const childIds = (tree?.childrenOf(parent.id) ?? []).map((child) => child.id);
    setPicks((current) => {
      const rest = current.fieldTagIds.filter((each) => each !== parent.id && !childIds.includes(each));
      const children = current.fieldTagIds.filter((each) => childIds.includes(each));
      const next = id === parent.id ? [] : children.includes(id) ? children.filter((each) => each !== id) : [...children, id];
      return { ...current, fieldTagIds: [...rest, ...(next.length ? next : [parent.id])] };
    });
  }
  function subGroup(parent: Tag, titled: boolean) {
    return (
      <Choices
        key={parent.id}
        group={{
          title: titled ? parent.name : undefined,
          choices: [
            { id: parent.id, label: `${parent.name} 전부 좋아요` },
            ...tree!.childrenOf(parent.id).map((tag) => ({ id: tag.id, label: tag.name, nameEn: tag.nameEn ?? null })),
          ],
        }}
        picked={picks.fieldTagIds}
        toggle={(id) => toggleChild(parent, id as number)}
      />
    );
  }
  const subParent = step.startsWith("sub:") ? pickedParents.find((tag) => `sub:${tag.id}` === step) : undefined;
  function toggle(key: "categories" | "traitTagIds", id: number | string) {
    setPicks((current) => {
      const list = current[key] as (number | string)[];
      return { ...current, [key]: list.includes(id) ? list.filter((value) => value !== id) : [...list, id] };
    });
  }
  const labels = [
    ...(tags ?? []).filter((tag) => picks.fieldTagIds.includes(tag.id) || picks.traitTagIds.includes(tag.id)).map((tag) => tag.name),
    ...funnelCategories.filter(([code]) => picks.categories.includes(code)).map(([, label]) => label),
  ];
  const copy: Record<BaseStep, { title: string; sub: string; cta: string }> = {
    intro: { title: "나에게 맞는 활동을\n찾아 드릴게요", sub: "몇 가지 질문이면 끝나요.\n답하지 않은 건 그냥 넘어가도 돼요.", cta: "시작하기" },
    fields: { title: "어떤 분야에\n관심 있어요?", sub: "여러 개 골라도 돼요.", cta: "다음" },
    subfields: { title: "분야별로 더 골라 볼까요?", sub: "고르지 않으면 그 분야 전부를 찾아요.", cta: "다음" },
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
      cta: found && !found.length ? "전체 목록 보기" : "맞춤 대기열 보러 가기",
    },
  };
  const { title, sub, cta } = subParent
    ? { title: `${subParent.name} 중에서는\n어떤 분야요?`, sub: copy.subfields.sub, cta: "다음" }
    : copy[step.startsWith("sub:") ? "subfields" : (step as BaseStep)];
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
          (tree ? (
            <Choices
              group={{ choices: tree.top.map((tag) => ({ id: tag.id, label: tag.name, nameEn: tag.nameEn ?? null })) }}
              picked={tree.top
                .filter((tag) => [tag, ...tree.childrenOf(tag.id)].some((each) => picks.fieldTagIds.includes(each.id)))
                .map((tag) => tag.id)}
              toggle={(id) => toggleField(tree.top.find((tag) => tag.id === id)!)}
            />
          ) : (
            <SkeletonGroup className="flex flex-wrap gap-2">
              {[0, 1, 2, 3, 4].map((key) => (
                <Skeleton key={key} className="h-10 w-20 rounded-full" />
              ))}
            </SkeletonGroup>
          ))}
        {subParent && subGroup(subParent, false)}
        {step === "subfields" && pickedParents.map((parent) => subGroup(parent, true))}
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
      {group.title && <h2 className="text-footnote font-semibold text-secondary-label">{group.title}</h2>}
      <div className="flex flex-wrap gap-2">
        {group.choices.map((choice) => {
          const on = picked.includes(choice.id);
          return (
            <button
              key={choice.id}
              type="button"
              aria-pressed={on}
              onClick={() => toggle(choice.id)}
              className={cn(
                "inline-flex min-h-10 items-center gap-1.5 rounded-full border-[1.5px] px-3.5 text-subheadline font-medium transition-colors",
                on ? "border-teal bg-mint text-teal" : "border-separator bg-card text-label",
              )}
            >
              {choice.nameEn !== undefined && (
                <span aria-hidden="true" className="flex">
                  <TagIcon nameEn={choice.nameEn} />
                </span>
              )}
              {choice.label}
            </button>
          );
        })}
      </div>
    </section>
  );
}
