"use client";
import Link from "next/link";
import { useState } from "react";
import type { Activity } from "@/lib/models";
import { cn } from "@/shared/lib/utils";
import { Icon } from "@/shared/ui/icon";
import { useAuth } from "@/features/auth/model/auth-context";
import { useMine } from "../model/mine-context";

/**
 * `+` beside an activity's badges (contract "내 활동 서버 저장" › 활동 상세 내 활동 행동 정리, web 2단계 B): adds the activity to 내 활동
 * (`PUT /me/activities/{id}`); signed out, the sign-in sheet comes first (`requireAccount`). Once added it is a check that leads to the
 * activity's progress page. `source` is the 사용 통계 `myactivity_add` source: `discovery` from a card, `link` from the detail page.
 */
export function MineToggle({ activity, source, className }: { activity: Activity; source: "discovery" | "link"; className?: string }) {
  const auth = useAuth();
  const mine = useMine();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(false);
  const base = cn("grid size-9 shrink-0 place-items-center rounded-full transition-opacity active:opacity-70", className);
  if (mine.has(activity.id))
    return (
      <Link href={`/mine/${activity.id}`} aria-label="내 활동으로" title="내 활동으로" className={cn(base, "bg-mint text-teal")}>
        <Icon name="check-circle" size="inline" />
      </Link>
    );
  return (
    <button
      type="button"
      aria-label={error ? "내 활동에 추가하지 못했어요. 다시 시도" : "내 활동에 추가"}
      title="내 활동에 추가"
      disabled={busy || mine.status === "loading"}
      className={cn(base, "bg-fill text-teal disabled:opacity-50", error && "text-danger")}
      onClick={() =>
        auth.requireAccount(() => {
          setBusy(true);
          setError(false);
          mine
            .add(activity, source)
            .catch(() => setError(true))
            .finally(() => setBusy(false));
        })
      }
    >
      <Icon name="plus" size="inline" />
    </button>
  );
}
