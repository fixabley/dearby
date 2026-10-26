"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { cn } from "@/shared/lib/utils";
import { BrandSymbol } from "@/shared/ui/brand-symbol";
import { Icon } from "@/shared/ui/icon";
import { NavTitleProvider, useNavTitle } from "@/shared/ui/nav-title";
import { useAuth } from "@/features/auth/model/auth-context";

/**
 * App shell (contract "웹 리디자인 — iOS 앱과 같은 표현" › 셸): an iOS navigation bar — sticky, translucent with a background blur,
 * a hairline once the page scrolls — whose centre shows the page's large title after it scrolls under the bar. Home-level pages
 * carry the brand on the left; the activity detail and a public card get a back link instead. On the right: 저장한 명함 and the
 * sign-in state (contract "웹 로그인": `로그인` opens the sheet; signed in, the account glyph leads to `/settings`). Content is 720px
 * max, 16px gutters.
 */
export function Shell({ children }: { children: React.ReactNode }) {
  return (
    <NavTitleProvider>
      <a
        href="#content"
        className="sr-only focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:z-50 focus:rounded-control focus:bg-card focus:px-4 focus:py-2"
      >
        본문으로 바로가기
      </a>
      <NavBar />
      <main id="content" className="mx-auto w-full max-w-[720px] px-4 pb-10">
        {children}
      </main>
      <footer className="mx-auto flex max-w-[720px] flex-col items-center gap-1 px-4 pt-4 pb-[max(24px,env(safe-area-inset-bottom))] text-footnote text-secondary-label">
        <span>dearby · 새로운 활동에서, 새로운 연결로.</span>
        <nav aria-label="약관" className="flex gap-4">
          <Link className="inline-flex min-h-11 items-center underline" href="/privacy">
            개인정보 처리방침
          </Link>
          <Link className="inline-flex min-h-11 items-center underline" href="/terms">
            이용약관
          </Link>
          <Link className="inline-flex min-h-11 items-center underline" href="/support">
            지원
          </Link>
        </nav>
      </footer>
    </NavTitleProvider>
  );
}

/** Where the bar's back link goes on a detail page; home-level pages show the brand. */
function backFor(path: string): { href: string; label: string } | undefined {
  if (path.startsWith("/activities/")) return { href: "/", label: "둘러보기" };
  if (path.startsWith("/cards/")) return { href: "/saved", label: "저장한 명함" };
}

function NavBar() {
  const path = usePathname();
  const { title, collapsed } = useNavTitle();
  const sentinel = useRef<HTMLDivElement>(null);
  const [scrolled, setScrolled] = useState(false);
  useEffect(() => {
    const element = sentinel.current;
    if (!element) return;
    const observer = new IntersectionObserver(([entry]) => setScrolled(!entry.isIntersecting));
    observer.observe(element);
    return () => observer.disconnect();
  }, []);
  const back = backFor(path);
  const savedHere = path === "/saved";
  const auth = useAuth();
  return (
    <>
      <div ref={sentinel} aria-hidden="true" className="absolute top-0 h-px w-px" />
      <header
        className={cn(
          "sticky top-0 z-40 border-b bg-bar pt-[env(safe-area-inset-top)] backdrop-blur-xl backdrop-saturate-200 transition-colors",
          scrolled ? "border-separator" : "border-transparent",
        )}
      >
        <div className="mx-auto grid h-11 max-w-[720px] grid-cols-[1fr_auto_1fr] items-center gap-2 px-4">
          <div className="flex min-w-0 justify-start">
            {back ? (
              <Link href={back.href} className="-ml-2 inline-flex min-h-11 items-center gap-0.5 pr-2 text-body text-teal">
                <Icon name="chevron-left" />
                <span className="truncate">{back.label}</span>
              </Link>
            ) : (
              <Link href="/" aria-label="Dearby 활동 둘러보기" className="inline-flex min-h-11 items-center gap-1.5 text-teal">
                <BrandSymbol size={26} />
                <span className="text-headline font-bold">Dearby</span>
              </Link>
            )}
          </div>
          <span
            aria-hidden="true"
            className={cn(
              "max-w-[52vw] truncate text-headline font-semibold transition-opacity duration-200 sm:max-w-[360px]",
              collapsed ? "opacity-100" : "opacity-0",
            )}
          >
            {title}
          </span>
          <div className="flex min-w-0 items-center justify-end gap-1">
            {!back?.href.startsWith("/saved") && (
              <Link
                href="/saved"
                aria-current={savedHere ? "page" : undefined}
                className="inline-flex min-h-11 items-center gap-1 px-2 text-body text-teal aria-[current=page]:font-semibold"
              >
                <Icon name="inbox" size="inline" />
                <span className="truncate">저장한 명함</span>
              </Link>
            )}
            {auth.signedIn ? (
              <>
                {/* 즐겨찾기 (contract "웹 화면 — 2단계 A"): the account's hearts, next to the account glyph. */}
                <Link
                  href="/favorites"
                  aria-label="즐겨찾기"
                  aria-current={path === "/favorites" ? "page" : undefined}
                  className="inline-flex size-11 items-center justify-center text-teal"
                >
                  <Icon name={path === "/favorites" ? "heart-fill" : "heart"} />
                </Link>
                <Link
                  href="/settings"
                  aria-label="설정"
                  aria-current={path === "/settings" ? "page" : undefined}
                  className="-mr-2 inline-flex size-11 items-center justify-center text-teal aria-[current=page]:font-semibold"
                >
                  <Icon name="person" />
                </Link>
              </>
            ) : (
              <button
                type="button"
                onClick={auth.openSignIn}
                className={cn("-mr-2 inline-flex min-h-11 items-center px-2 text-body font-semibold text-teal", auth.status === "loading" && "invisible")}
              >
                로그인
              </button>
            )}
          </div>
        </div>
      </header>
    </>
  );
}
