import { cn } from "@/shared/lib/utils";

/**
 * `Apple로 계속하기` (contract "애플 로그인" › 웹: Apple JS, popup). Apple's button guidelines for a custom button: the Apple logo,
 * black with white text (white with black text in dark mode), the "Continue with" wording, at least 44pt tall. Matches the iOS
 * system button's 50pt height and 11pt corners. `busy` shows a spinner in place of the label.
 */
export function AppleButton({ onClick, disabled, busy }: { onClick: () => void; disabled?: boolean; busy?: boolean }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label="Apple로 계속하기"
      className={cn(
        "inline-flex min-h-[50px] w-full items-center justify-center gap-2 rounded-[11px] bg-black text-headline font-semibold text-white select-none active:opacity-70 disabled:opacity-50",
        "dark:bg-white dark:text-black",
      )}
    >
      {busy ? (
        <Spinner />
      ) : (
        <>
          <svg width="18" height="22" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
            <path d="M12.152 6.896c-.948 0-2.415-1.078-3.96-1.04-2.04.027-3.91 1.183-4.961 3.014-2.117 3.675-.546 9.103 1.519 12.09 1.013 1.454 2.208 3.09 3.792 3.039 1.52-.065 2.09-.987 3.935-.987 1.831 0 2.35.987 3.96.948 1.637-.026 2.676-1.48 3.676-2.948 1.156-1.688 1.636-3.325 1.662-3.415-.039-.013-3.182-1.221-3.22-4.857-.026-3.04 2.48-4.494 2.597-4.559-1.429-2.09-3.623-2.324-4.39-2.376-2-.156-3.675 1.09-4.61 1.09zM15.53 3.83c.843-1.012 1.4-2.427 1.245-3.83-1.207.052-2.662.805-3.532 1.818-.78.896-1.454 2.338-1.273 3.714 1.338.104 2.715-.688 3.559-1.701" />
          </svg>
          Apple로 계속하기
        </>
      )}
    </button>
  );
}

/** Small ring spinner on the current text colour, for a button whose action is running. */
export function Spinner({ className }: { className?: string }) {
  return (
    <span
      role="progressbar"
      aria-label="진행 중"
      className={cn("inline-block size-5 animate-spin rounded-full border-2 border-current border-t-transparent motion-reduce:animate-none", className)}
    />
  );
}
