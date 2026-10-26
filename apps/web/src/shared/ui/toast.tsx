"use client";
import { useEffect, useState } from "react";

/** A notice to show once; a new `id` shows again even with the same text. */
export type Notice = { id: number; text: string };

/**
 * One transient notice at the bottom of the screen (iOS-style pill over the content), announced politely. Shows the notice for a
 * few seconds, then hides until the next one. Nothing renders without a notice, but the live region stays in place.
 */
export function Toast({ notice, duration = 5000 }: { notice?: Notice; duration?: number }) {
  const [hidden, setHidden] = useState<number>();
  useEffect(() => {
    if (!notice) return;
    const timer = setTimeout(() => setHidden(notice.id), duration);
    return () => clearTimeout(timer);
  }, [notice, duration]);
  const visible = notice && hidden !== notice.id ? notice.text : "";
  return (
    <div
      role="status"
      aria-live="polite"
      className="pointer-events-none fixed inset-x-0 bottom-[max(24px,env(safe-area-inset-bottom))] z-50 flex justify-center px-4"
    >
      {visible && (
        <span className="max-w-[420px] rounded-full bg-label px-4 py-2.5 text-center text-subheadline font-semibold text-background shadow-lg animate-in fade-in-0 slide-in-from-bottom-2">
          {visible}
        </span>
      )}
    </div>
  );
}
