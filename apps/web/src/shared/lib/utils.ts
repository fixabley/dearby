import { clsx, type ClassValue } from "clsx";
import { extendTailwindMerge } from "tailwind-merge";

// The iOS text styles and radii from globals.css `@theme`; without them `text-body` would be merged away as a colour.
const twMerge = extendTailwindMerge({
  extend: {
    theme: {
      text: ["large-title", "title1", "title2", "headline", "body", "subheadline", "footnote", "caption", "caption2"],
      radius: ["card", "control"],
    },
  },
});

/** shadcn/ui class joiner: conditional classes, later Tailwind utilities win. */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}
