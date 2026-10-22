"use client";
import * as SeparatorPrimitive from "@radix-ui/react-separator";
import { cn } from "@/shared/lib/utils";

/** shadcn/ui Separator as the iOS hairline (`separator` colour); decorative by default. */
function Separator({ className, decorative = true, ...props }: React.ComponentProps<typeof SeparatorPrimitive.Root>) {
  return (
    <SeparatorPrimitive.Root
      data-slot="separator"
      decorative={decorative}
      className={cn("h-px w-full shrink-0 bg-separator", className)}
      {...props}
    />
  );
}

export { Separator };
