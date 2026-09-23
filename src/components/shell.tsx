"use client";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useState, type ReactNode } from "react";
import { Icon } from "./icon";
import { useSaved } from "@/features/saved/provider";
export function Shell({ children }: { children: ReactNode }) {
  const [collapsed, setCollapsed] = useState(false);
  const router = useRouter(),
    params = useSearchParams(),
    path = usePathname();
  const { message, issue, retry, reset } = useSaved();
  const section = params.get("view") || "all";
  return (
    <div className={collapsed ? "app collapsed" : "app"}>
      <a className="skip-link" href="#main">
        본문 바로가기
      </a>
      <header className="header">
        <button
          className="icon-button menu-toggle"
          aria-label="탐색 메뉴 접기 또는 펼치기"
          aria-expanded={!collapsed}
          onClick={() => setCollapsed(!collapsed)}
        >
          <Icon name="menu" />
        </button>
        <Link href="/" className="brand">
          <span className="brand-symbol">d</span>dearby
          <span className="brand-kr">KR</span>
        </Link>
        <form
          className="search"
          action="/"
          onSubmit={(e) => {
            e.preventDefault();
            const value = new FormData(e.currentTarget).get("q") as string;
            router.push("/?q=" + encodeURIComponent(value));
          }}
        >
          <input
            key={params.get("q") || ""}
            name="q"
            defaultValue={params.get("q") || ""}
            aria-label="프로그램 검색"
            placeholder="어떤 경험을 찾고 있나요?"
          />
          <button aria-label="검색">
            <Icon name="search" />
          </button>
        </form>
        <span className="header-note">나의 다음 가능성</span>
      </header>
      <aside className="sidebar" aria-label="주 메뉴">
        <nav>
          {(
            [
              { id: "all", label: "전체 탐색", icon: "home", href: "/" },
              {
                id: "open",
                label: "모집·등록 중",
                icon: "compass",
                href: "/?view=open",
              },
              {
                id: "saved",
                label: "스크랩",
                icon: "bookmark",
                href: "/?view=saved",
              },
            ] as const
          ).map((item) => (
            <Link
              key={item.id}
              className={
                path === "/" && section === item.id
                  ? "nav-link active"
                  : "nav-link"
              }
              href={item.href}
            >
              <Icon name={item.icon} />
              <span>{item.label}</span>
            </Link>
          ))}
        </nav>
        <div className="sidebar-info">
          <b>새로운 경험의 시작</b>
          <p>
            관심 있는 프로그램과 조직을
            <br />
            스크랩하고 다시 만나세요.
          </p>
          <hr />
          <p>
            샘플 데이터로 둘러보는
            <br />
            Dearby 웹 프로토타입
          </p>
          <small>© 2026 Dearby</small>
        </div>
      </aside>
      <main id="main" className="main">
        <div className="sample-notice">
          <span className="sample-dot" />
          샘플 둘러보기{" "}
          <span>가상 조직·프로그램입니다. 모집·등록 상태 기준 2026.09.24</span>
        </div>
        {message && (
          <div className="storage-message" role="status">
            {message}
            {issue && <button onClick={retry}>다시 시도</button>}
            {issue === "corrupt" && (
              <button onClick={reset}>손상된 스크랩 초기화</button>
            )}
          </div>
        )}
        {children}
      </main>
    </div>
  );
}
