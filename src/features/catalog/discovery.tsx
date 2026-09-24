"use client";
import { useRef, type ReactNode } from "react";
import Image from "next/image";
import Link from "next/link";
import { programs } from "./data";
import { activeNotices, emptyFilters, searchPrograms, noticeStatus, displayDate } from "./model";
import { ProgramCard } from "./card";
import { SaveButton } from "../saved/save-button";

function Shelf({ title, href, hero = false, children }: { title: string; href: string; hero?: boolean; children: ReactNode }) {
  const rail = useRef<HTMLDivElement>(null);
  const move = (direction: number) => rail.current?.scrollBy({ left: direction * rail.current.clientWidth * 0.85, behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "instant" : "smooth" });
  return <section className="discovery-shelf" aria-label={title}>
    <div className="shelf-heading"><h2>{title}</h2><Link href={href}>모두 보기 <span aria-hidden="true">→</span><span className="sr-only"> · {title}</span></Link>
      <div className="shelf-controls"><button onClick={() => move(-1)} aria-label={`${title} 이전`}>←</button><button onClick={() => move(1)} aria-label={`${title} 다음`}>→</button></div>
    </div>
    <div ref={rail} className={`discovery-rail ${hero ? "hero-rail" : ""}`} tabIndex={0} role="region" aria-label={`${title} 좌우 스크롤`}>{children}</div>
  </section>;
}
export function Discovery() {
  const ordered = searchPrograms(programs, emptyFilters);
  const upcoming = ordered.filter(p => activeNotices(p).length);
  const groups = [
    { title: "동료와 함께 서비스 만들기", href: "/?view=all&experiences=making", data: searchPrograms(programs, { ...emptyFilters, experiences: ["making"] }) },
    { title: "기술과 사람을 만나는 컨퍼런스", href: "/?view=all&category=컨퍼런스", data: ordered.filter(p => p.category === "컨퍼런스") },
    { title: "함께 성장하는 연합동아리", href: "/?view=all&category=연합동아리", data: ordered.filter(p => p.category === "연합동아리") },
  ];
  return <div className="discovery">
    <div className="discovery-intro"><span className="eyebrow">나의 다음 가능성</span><h1>새로운 경험을 발견하세요</h1><p>기술을 듣고, 동료를 만나고, 함께 만들어가는 다음 이야기.</p></div>
    {upcoming.length > 0 && <Shelf title="지금 주목할 모집 · 진행중과 예정" href="/?view=available" hero>
      {upcoming.map((p, i) => { const n = activeNotices(p)[0]; return <article className="feature-program" key={p.id}>
        <div className="feature-copy"><span className={`event-status status-${n.status}`}>{noticeStatus(n)} · {n.round}</span><h3><Link href={`/programs/${p.id}`}>{p.title}</Link></h3><p>{p.subtitle}</p><small>{n.status === "scheduled" ? `접수 시작 ${displayDate(n.start)}` : `신청 마감 ${displayDate(n.deadline)}`}</small><div className="feature-actions"><Link className="primary" href={`/programs/${p.id}`}>프로그램 살펴보기</Link><SaveButton kind="programs" id={p.id} compact /></div></div>
        <Link className="feature-art" href={`/programs/${p.id}`} tabIndex={-1} aria-hidden="true"><Image src={p.cover} alt="" width={1200} height={675} loading={i === 0 ? "eager" : "lazy"} sizes="(max-width: 720px) 85vw, 45vw" /></Link>
      </article>; })}
    </Shelf>}
    {groups.map(group => group.data.length > 0 && <Shelf title={group.title} href={group.href} key={group.title}>{group.data.map(p => <ProgramCard program={p} key={p.id} />)}</Shelf>)}
  </div>;
}
