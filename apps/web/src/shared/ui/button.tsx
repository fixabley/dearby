import { Slot } from "@radix-ui/react-slot";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/shared/lib/utils";

/**
 * shadcn/ui Button in iOS terms: `filled` = `.borderedProminent` (teal fill, white label), `tinted` = `.bordered` (teal 12% fill,
 * teal label), `plain` = `.borderless` text button, `destructive` = red text button. Every size is at least 44px tall.
 */
const buttonVariants = cva(
  "inline-flex shrink-0 items-center justify-center gap-1.5 rounded-control font-semibold whitespace-nowrap transition-[opacity,background-color] select-none active:opacity-70 disabled:pointer-events-none disabled:opacity-50 [&_svg]:shrink-0",
  {
    variants: {
      variant: {
        filled: "bg-teal-fill text-on-teal",
        tinted: "bg-mint text-teal",
        plain: "text-teal",
        destructive: "text-danger",
      },
      size: {
        default: "min-h-11 px-4 text-body",
        lg: "min-h-[50px] w-full px-5 text-body",
        sm: "min-h-11 px-3 text-subheadline",
        icon: "size-11 rounded-full",
      },
    },
    defaultVariants: { variant: "filled", size: "default" },
  },
);

function Button({
  className,
  variant,
  size,
  asChild = false,
  ...props
}: React.ComponentProps<"button"> & VariantProps<typeof buttonVariants> & { asChild?: boolean }) {
  const Comp = asChild ? Slot : "button";
  return <Comp data-slot="button" className={cn(buttonVariants({ variant, size, className }))} {...props} />;
}

export { Button, buttonVariants };
