"use client";
import * as DialogPrimitive from "@radix-ui/react-dialog";
import { cn } from "@/shared/lib/utils";

/**
 * shadcn/ui Dialog drawn as an iOS alert: 270px centred panel, centred title and message, a row of text buttons above hairlines.
 * Callers put `DialogClose`/buttons inside `DialogActions`.
 */
const Dialog = DialogPrimitive.Root;
const DialogClose = DialogPrimitive.Close;

function DialogContent({ className, children, ...props }: React.ComponentProps<typeof DialogPrimitive.Content>) {
  return (
    <DialogPrimitive.Portal>
      <DialogPrimitive.Overlay className="fixed inset-0 z-50 bg-black/30 data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:animate-in data-[state=open]:fade-in-0" />
      <DialogPrimitive.Content
        className={cn(
          "fixed top-1/2 left-1/2 z-50 w-[270px] -translate-x-1/2 -translate-y-1/2 overflow-hidden rounded-[14px] bg-card text-center shadow-2xl duration-200 data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:animate-in data-[state=open]:fade-in-0 data-[state=open]:zoom-in-110",
          className,
        )}
        {...props}
      >
        {children}
      </DialogPrimitive.Content>
    </DialogPrimitive.Portal>
  );
}

function DialogTitle({ className, ...props }: React.ComponentProps<typeof DialogPrimitive.Title>) {
  return <DialogPrimitive.Title className={cn("px-4 pt-5 text-headline font-semibold", className)} {...props} />;
}

function DialogDescription({ className, ...props }: React.ComponentProps<typeof DialogPrimitive.Description>) {
  return <DialogPrimitive.Description className={cn("px-4 pt-1 pb-5 text-footnote", className)} {...props} />;
}

/** Side-by-side alert buttons split by hairlines. */
function DialogActions({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      className={cn("flex border-t border-separator [&>*]:min-h-11 [&>*]:flex-1 [&>*+*]:border-l [&>*+*]:border-separator", className)}
      {...props}
    />
  );
}

export { Dialog, DialogActions, DialogClose, DialogContent, DialogDescription, DialogTitle };
