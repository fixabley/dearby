"use client";
import Image from "next/image";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Icon } from "./icon";
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
            <Image
              src="/logo-teal.png"
              alt="dearby"
              width={2172}
              height={724}
              style={{ height: "auto" }}
              priority
            />
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
              <Icon name="compass" />
              탐색
            </Link>
            <Link
              href="/saved"
              aria-current={path === "/saved" ? "page" : undefined}
            >
              <Icon name="bookmark" />
              저장한 명함
            </Link>
          </nav>
        </div>
      </header>
      <main id="content" className="main">
        {children}
      </main>
      <footer className="site-footer">
        dearby · 새로운 활동에서, 새로운 연결로.
      </footer>
    </>
  );
}
