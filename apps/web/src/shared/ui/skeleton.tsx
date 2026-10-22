import { cn } from "@/shared/lib/utils";

/** shadcn/ui Skeleton: a pulsing system-fill placeholder; the pulse stops under reduced motion. */
function Skeleton({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      data-slot="skeleton"
      className={cn("animate-pulse rounded-control bg-fill motion-reduce:animate-none", className)}
      {...props}
    />
  );
}

/**
 * One content-loading skeleton group (contract "내용 로딩은 스켈레톤 — 2026-10-22"): a single busy status announced as `불러오는 중`;
 * the placeholder blocks inside carry no text.
 */
function SkeletonGroup({ className, children }: { className?: string; children: React.ReactNode }) {
  return (
    <div role="status" aria-busy="true" className={className}>
      <span className="sr-only">불러오는 중</span>
      {children}
    </div>
  );
}

export { Skeleton, SkeletonGroup };
