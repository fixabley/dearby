import { cn } from "@/shared/lib/utils";

/** shadcn/ui Input as the iOS search/text field: 36px, 10px corners, system fill, no border. */
function Input({ className, type, ...props }: React.ComponentProps<"input">) {
  return (
    <input
      type={type}
      data-slot="input"
      className={cn(
        "h-9 w-full min-w-0 rounded-control bg-fill px-2 text-body text-label outline-none placeholder:text-secondary-label focus-visible:outline-2 focus-visible:outline-teal [&::-webkit-search-cancel-button]:appearance-none",
        className,
      )}
      {...props}
    />
  );
}

export { Input };
