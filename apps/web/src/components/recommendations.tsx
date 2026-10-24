"use client";
import { useEffect, useState } from "react";
import { type Recommendation, recommendationListSchema } from "@/lib/models";
import { RequestError, errorMessage, request } from "@/lib/client";
import { type RecommendationPreferences, isEmpty, recommendationsPath } from "@/lib/recommendation";
import { Button } from "@/shared/ui/button";
import { Card } from "@/shared/ui/card";
import { Icon } from "@/shared/ui/icon";
import { Skeleton, SkeletonGroup } from "@/shared/ui/skeleton";
import { Failure } from "@/shared/ui/states";
import { RecommendationRow } from "@/widgets/recommendation/recommendation-row";

/**
 * 발견 탭 `추천` 묶음 (contract "발견 탭 노출"): the top three recommendations stacked, `더 보기` for the rest of the ten, and `설정`
 * to re-open the funnel. A skipped funnel shows one `나에게 맞는 활동 찾기` row instead. Hidden while the API has no
 * recommendations route yet (401/404).
 */
export function RecommendationSection({
  preferences,
  onEdit,
}: {
  preferences: RecommendationPreferences;
  onEdit: () => void;
}) {
  const skipped = isEmpty(preferences);
  const [items, setItems] = useState<Recommendation[] | "missing">();
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const [more, setMore] = useState(false);
  const path = recommendationsPath(preferences, 10);
  useEffect(() => {
    if (skipped) return;
    const controller = new AbortController();
    request<unknown>(path, { signal: controller.signal })
      .then((json) => setItems(recommendationListSchema.parse(json).items))
      .catch((error) => {
        if (controller.signal.aborted) return;
        // Until the API ships the route it answers 401 (unknown paths need a token) or 404: hide the section.
        if (error instanceof RequestError && [401, 404].includes(error.status)) setItems("missing");
        else setError(errorMessage(error));
      });
    return () => controller.abort();
  }, [skipped, path, attempt]);
  if (skipped)
    return (
      <button
        type="button"
        onClick={onEdit}
        className="mb-2.5 flex min-h-11 w-full items-center gap-3 rounded-[14px] bg-card px-4 py-3 text-left active:opacity-70"
      >
        <Icon name="sparkles" tone="teal" />
        <span className="flex-1 text-body">나에게 맞는 활동 찾기</span>
        <span aria-hidden="true" className="text-secondary-label">
          ›
        </span>
      </button>
    );
  if (items === "missing") return null;
  const shown = items && (more ? items : items.slice(0, 3));
  return (
    <section aria-labelledby="recommendations-title" className="mb-4 flex flex-col gap-1.5">
      <div className="flex items-center justify-between pl-1">
        <h2 id="recommendations-title" className="text-title2 font-bold">
          추천
        </h2>
        <Button variant="plain" size="sm" onClick={onEdit} aria-label="추천 설정">
          설정
        </Button>
      </div>
      {error ? (
        <Failure
          compact
          message={error}
          retry={() => {
            setError("");
            setAttempt((value) => value + 1);
          }}
        />
      ) : !shown ? (
        <SkeletonGroup>
          <Card className="flex flex-col gap-4 p-4">
            {[0, 1, 2].map((key) => (
              <div key={key} className="flex items-start gap-3">
                <Skeleton className="size-11 rounded-[14px]" />
                <div className="flex flex-1 flex-col gap-2 pt-1">
                  <Skeleton className="h-4 w-2/3" />
                  <Skeleton className="h-3.5 w-1/3" />
                </div>
              </div>
            ))}
          </Card>
        </SkeletonGroup>
      ) : !shown.length ? (
        <Card className="px-4 py-3 text-subheadline text-secondary-label break-keep">
          지금 설정에 맞는 활동이 없어요. 설정을 바꾸거나 아래 목록에서 찾아보세요.
        </Card>
      ) : (
        <Card>
          <ul className="divide-y divide-separator">
            {shown.map((item) => (
              <li key={item.activity.id}>
                <RecommendationRow item={item} />
              </li>
            ))}
          </ul>
          {!more && (items?.length ?? 0) > 3 && (
            <Button variant="plain" className="w-full border-t border-separator" onClick={() => setMore(true)}>
              더 보기
            </Button>
          )}
        </Card>
      )}
    </section>
  );
}
