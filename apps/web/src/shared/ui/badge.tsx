import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/shared/lib/utils";

/** shadcn/ui Badge as an iOS capsule: `filled` teal fill + white (모집 중), `tinted` teal 12% + teal (모집 예정), `neutral` system fill. */
const badgeVariants = cva(
  "inline-flex w-fit shrink-0 items-center gap-1 rounded-full px-2 py-0.5 text-caption font-semibold whitespace-nowrap",
  {
    variants: {
      variant: {
        filled: "bg-teal-fill text-on-teal",
        tinted: "bg-mint text-teal",
        neutral: "bg-fill text-secondary-label",
      },
    },
    defaultVariants: { variant: "neutral" },
  },
);

function Badge({ className, variant, ...props }: React.ComponentProps<"span"> & VariantProps<typeof badgeVariants>) {
  return <span data-slot="badge" className={cn(badgeVariants({ variant }), className)} {...props} />;
}

export { Badge };
