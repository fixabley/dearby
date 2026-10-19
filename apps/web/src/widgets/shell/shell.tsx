"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { BrandSymbol } from "@/shared/ui/brand-symbol";
import { Icon } from "@/shared/ui/icon";
export function Shell({ children }: { children: React.ReactNode }) {
  const path = usePathname();
  return (
    <>
      <a className="skip-link" href="#content">
        본문으로 바로가기
      </a>
      <header className="site-header">
        <div className="header-inner">
          <Link className="brand" href="/" aria-label="dearby 탐색">
            <BrandSymbol size={40} />
            <span className="brand-name">Dearby</span>
          </Link>
          <nav aria-label="주 메뉴">
            <Link
              href="/"
              aria-current={
                path === "/" || path.startsWith("/activities")
                  ? "page"
                  : undefined
              }
            >
              <Icon name="explore" size="inline" />
              탐색
            </Link>
            <Link
              href="/saved"
              aria-current={path === "/saved" ? "page" : undefined}
            >
              <Icon name="inbox" size="inline" />
              저장한 명함
            </Link>
          </nav>
        </div>
      </header>
      <main id="content" className="main">
        {children}
      </main>
      <footer className="site-footer">
        <span>dearby · 새로운 활동에서, 새로운 연결로.</span>
        <nav aria-label="약관" className="footer-links">
          <Link href="/privacy">개인정보 처리방침</Link>
          <Link href="/terms">이용약관</Link>
        </nav>
      </footer>
    </>
  );
}
