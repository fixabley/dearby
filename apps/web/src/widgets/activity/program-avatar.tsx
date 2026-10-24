"use client";
import { useState } from "react";

/**
 * 44px logo slot (contract "프로그램 로고"): the program logo through the same-origin proxy, else — null, older response,
 * or a failed load — the initial-letter avatar. Key it by `src` so a new `v` retries after a failure.
 */
export function ProgramAvatar({ name, src }: { name: string; src: string | undefined }) {
  const [failed, setFailed] = useState(false);
  return (
    <span
      aria-hidden="true"
      className="grid size-11 shrink-0 place-items-center overflow-hidden rounded-[14px] bg-mint text-[18px] font-bold text-teal"
    >
      {src && !failed ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={src} alt="" loading="lazy" decoding="async" className="size-full object-cover" onError={() => setFailed(true)} />
      ) : (
        ([...name.trim()][0] ?? "?")
      )}
    </span>
  );
}
