"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { cn } from "@/shared/lib/utils";
import { BrandSymbol } from "@/shared/ui/brand-symbol";
import { Icon } from "@/shared/ui/icon";
import { NavTitleProvider, useNavTitle } from "@/shared/ui/nav-title";
import { DropdownMenu, type MenuItem } from "@/shared/ui/dropdown-menu";
import { useAuth } from "@/features/auth/model/auth-context";
import { useUnread } from "@/features/notifications/model/unread";

/**
 * App shell (contract "웹 리디자인 — iOS 앱과 같은 표현" › 셸): an iOS navigation bar — sticky, translucent with a background blur,
 * a hairline once the page scrolls — whose centre shows the page's large title after it scrolls under the bar. Home-level pages
 * carry the brand on the left; the activity detail and a public card get a back link instead. On the right, signed out: 저장한 명함
 * and `로그인` (contract "웹 로그인"); signed in (contract "웹 화면 — 2단계 C"): the 알림 bell with its unread badge and the 메뉴 button
 * whose dropdown lists 내 활동 · 즐겨찾기 · 명함 · 받은 명함 · 프로필 · 알림 · 설정 with icons. Content is 720px max, 16px gutters.
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

/** Where the bar's back link goes on a detail page; home-level pages show the brand. A public card leads back to the wallet when signed in. */
function backFor(path: string, signedIn: boolean): { href: string; label: string } | undefined {
  if (path.startsWith("/activities/")) return { href: "/", label: "둘러보기" };
  if (path.startsWith("/cards/") && path !== "/cards/mine") return signedIn ? { href: "/wallet", label: "받은 명함" } : { href: "/saved", label: "저장한 명함" };
  if (path.startsWith("/wallet/")) return { href: "/wallet", label: "받은 명함" };
}
/** The signed-in menu (contract "웹 화면 — 2단계 C" › 상단 바): 내 활동·즐겨찾기 come from the sibling PRs' routes. */
const MENU: MenuItem[] = [
  { href: "/my-activities", label: "내 활동", icon: "calendar" },
  { href: "/favorites", label: "즐겨찾기", icon: "heart" },
  { href: "/cards/mine", label: "명함", icon: "card" },
  { href: "/wallet", label: "받은 명함", icon: "inbox" },
  { href: "/profile", label: "프로필", icon: "person" },
  { href: "/notifications", label: "알림", icon: "bell" },
  { href: "/settings", label: "설정", icon: "gear" },
];

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
  const auth = useAuth();
  const back = backFor(path, auth.signedIn);
  const savedHere = path === "/saved";
  const unread = useUnread().count;
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
            {!auth.signedIn && !back?.href.startsWith("/saved") && (
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
                <Link
                  href="/notifications"
                  aria-label={unread ? `알림, 읽지 않은 알림 ${unread}개` : "알림"}
                  aria-current={path === "/notifications" ? "page" : undefined}
                  className="relative inline-flex size-11 items-center justify-center text-teal"
                >
                  <Icon name="bell" />
                  {unread > 0 && (
                    <span aria-hidden="true" className="absolute top-1 right-0.5 min-w-4 rounded-full bg-teal-fill px-1 text-center text-caption2 font-bold leading-4 text-on-teal">
                      {unread > 9 ? "9+" : unread}
                    </span>
                  )}
                </Link>
                <DropdownMenu label="메뉴" icon="menu" items={MENU} current={path} className="-mr-2" />
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
