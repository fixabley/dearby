"use client";
import { createContext, useContext, useEffect, useRef, useState } from "react";
import { cn } from "@/shared/lib/utils";

type NavTitle = { title: string; collapsed: boolean };
const NavTitleContext = createContext<{ value: NavTitle; set: (value: NavTitle) => void } | null>(null);

/** Holds the page's large title so the shell's bar can show it small and centred once it scrolls under the bar. */
export function NavTitleProvider({ children }: { children: React.ReactNode }) {
  const [value, set] = useState<NavTitle>({ title: "", collapsed: false });
  return <NavTitleContext value={{ value, set }}>{children}</NavTitleContext>;
}

export function useNavTitle(): NavTitle {
  return useContext(NavTitleContext)?.value ?? { title: "", collapsed: false };
}

/** Bar height the title collapses under (44px bar; the safe-area inset is ignored, it only shifts the switch point). */
const BAR = 44;

/**
 * iOS large title (contract "웹 리디자인" › 셸): an `h1` under the bar that, once scrolled under it, hands its text to the bar's
 * small centred title (IntersectionObserver, no scroll listener). `size="title1"` is the 28pt variant for long names (activity detail).
 */
export function LargeTitle({
  children,
  size = "large",
  className,
}: {
  children: string;
  size?: "large" | "title1";
  className?: string;
}) {
  const ref = useRef<HTMLHeadingElement>(null);
  const set = useContext(NavTitleContext)?.set;
  useEffect(() => {
    const element = ref.current;
    if (!element || !set) return;
    set({ title: children, collapsed: false });
    const observer = new IntersectionObserver(
      ([entry]) => set({ title: children, collapsed: !entry.isIntersecting && entry.boundingClientRect.top < BAR }),
      { rootMargin: `-${BAR}px 0px 0px 0px` },
    );
    observer.observe(element);
    return () => {
      observer.disconnect();
      set({ title: "", collapsed: false });
    };
  }, [children, set]);
  return (
    <h1
      ref={ref}
      className={cn(
        "font-bold tracking-tight break-keep",
        size === "large" ? "pt-1 pb-2 text-large-title" : "text-title1",
        className,
      )}
    >
      {children}
    </h1>
  );
}
