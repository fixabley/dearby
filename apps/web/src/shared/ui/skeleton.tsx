import { cn } from "@/shared/lib/utils";

/** shadcn/ui Skeleton: a pulsing system-fill placeholder. */
function Skeleton({ className, ...props }: React.ComponentProps<"div">) {
  return <div data-slot="skeleton" className={cn("animate-pulse rounded-control bg-fill", className)} {...props} />;
}

export { Skeleton };
