import { cn } from "@/shared/lib/utils";

/** shadcn/ui Card as an iOS inset-grouped section: secondary grouped surface, 20px corners, no stroke or shadow. */
function Card({ className, ...props }: React.ComponentProps<"div">) {
  return <div data-slot="card" className={cn("overflow-hidden rounded-card bg-card", className)} {...props} />;
}

export { Card };
