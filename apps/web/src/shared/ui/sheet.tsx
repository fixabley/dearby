"use client";
import * as DialogPrimitive from "@radix-ui/react-dialog";
import { cn } from "@/shared/lib/utils";

/**
 * shadcn/ui Sheet as an iOS bottom sheet: grabber, 20px top corners, safe-area bottom padding, slides up. Only the bottom side
 * is used here, so the side variants are left out.
 */
const Sheet = DialogPrimitive.Root;

function SheetContent({
  className,
  children,
  overlay = true,
  ...props
}: React.ComponentProps<typeof DialogPrimitive.Content> & { overlay?: boolean }) {
  return (
    <DialogPrimitive.Portal>
      {overlay && (
        <DialogPrimitive.Overlay className="fixed inset-0 z-50 bg-black/30 data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:animate-in data-[state=open]:fade-in-0" />
      )}
      <DialogPrimitive.Content
        className={cn(
          "fixed inset-x-0 bottom-0 z-50 mx-auto w-full max-w-[720px] rounded-t-card bg-card px-5 pt-2 pb-[max(16px,env(safe-area-inset-bottom))] shadow-[0_-8px_32px_rgb(0_0_0/0.12)] duration-300 data-[state=closed]:animate-out data-[state=closed]:slide-out-to-bottom data-[state=open]:animate-in data-[state=open]:slide-in-from-bottom",
          className,
        )}
        {...props}
      >
        <div aria-hidden="true" className="mx-auto mb-3 h-[5px] w-9 rounded-full bg-fill" />
        {children}
      </DialogPrimitive.Content>
    </DialogPrimitive.Portal>
  );
}

function SheetTitle({ className, ...props }: React.ComponentProps<typeof DialogPrimitive.Title>) {
  return <DialogPrimitive.Title className={cn("text-headline font-semibold", className)} {...props} />;
}

function SheetDescription({ className, ...props }: React.ComponentProps<typeof DialogPrimitive.Description>) {
  return <DialogPrimitive.Description className={cn("text-subheadline text-secondary-label", className)} {...props} />;
}

export { Sheet, SheetContent, SheetDescription, SheetTitle };
