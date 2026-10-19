/**
 * Dearby brand symbol (contract "다크 모드": the mark is single-colour teal, drawn in the lighter teal on dark). Inline copy of
 * `shared/assets/brand/symbol.svg` (2026-10-19 final, 시안 B) with the stroke on `currentColor`, so `color: var(--teal)` on the
 * parent follows the theme. Decorative: callers carry the name (link label or text).
 */
export function BrandSymbol({ size = 40, className }: { size?: number; className?: string }) {
  return (
    <svg
      className={className}
      viewBox="0 0 512 512"
      width={size}
      height={size}
      fill="none"
      stroke="currentColor"
      strokeWidth="58"
      strokeLinecap="round"
      aria-hidden="true"
    >
      <path d="M187 355 A100 100 0 1 1 296 278" />
      <path d="M325 157 A100 100 0 1 1 216 235" />
    </svg>
  );
}
