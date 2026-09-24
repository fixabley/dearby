import { OrganizationAvatar } from "@/features/catalog/organization-avatar";
import { notFound } from "next/navigation";
import Image from "next/image";
import Link from "next/link";
import { programs, organizations, snapshotDate } from "@/features/catalog/data";
import { noticeStatus, eventDates, displayDate } from "@/features/catalog/model";
import { SaveButton } from "@/features/saved/save-button";
export function generateStaticParams() { return programs.map(p => ({ id: p.id })); }
export default async function ProgramPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const p = programs.find(p => p.id === id);
  if (!p) notFound();
  const club = p.category === "연합동아리";
  const org = organizations.find(o => o.id === p.orgId)!;
  return <>
    <Link href="/" className="back-link">← 전체 탐색</Link>
    <div className="watch-layout">
      <section className="watch-main">
        <Image className="detail-cover" src={p.cover} alt={`${p.title} 공식 ${p.coverSource.kind === "og" ? "공유이미지" : "페이지 캡처"}`} width={1200} height={675} sizes="(max-width: 720px) 100vw, 65vw" preload />
        <div className="detail-heading"><span className="eyebrow">{p.category}</span><h1>{p.title}</h1><p>{p.subtitle}</p></div>
        <div className="channel-row">
          <OrganizationAvatar organization={org} large />
          <div><strong>{org.name}</strong><p>등록된 프로그램 {programs.filter(x => x.orgId === org.id).length}개</p></div>
          <SaveButton kind="organizations" id={org.id} /><SaveButton kind="programs" id={p.id} />
        </div>
      </section>
      <aside className="notice-list" aria-labelledby="notices-title">
        <h2 id="notices-title">{club ? "기수별 지원 정보" : "회차별 참가 정보"} <span>{p.notices.length}</span></h2>
        <p className="muted">확인된 대표 회차의 정보로 검색합니다. 이전 모집과 지난 행사도 볼 수 있어요.</p>
        {p.notices.map(n => <article className={`notice ${n.status === "ended" ? "past" : ""}`} key={n.id} data-notice-id={n.id}>
          <div className="notice-top"><span>{n.round} · {n.current ? "확인된 대표 회차" : "이전 회차"}</span><b className={`event-status status-${n.status}`}>{noticeStatus(n)}</b></div>
          <h3>{p.title} {n.round}</h3>
          {n.status === "ended" && <div className="past-note">지난 행사입니다. 남아 있는 신청 링크나 다시보기 등록은 현재 행사 모집을 뜻하지 않습니다.</div>}
          {club && <section className="selection-process" aria-label="선발 절차"><h4>선발 절차</h4><ol>{n.selectionProcess?.map(step => <li key={step}>{step}</li>)}</ol></section>}
          <dl className="event-facts">
            <div><dt>{club ? "활동 일정" : "개최일"}</dt><dd>{club ? n.activitySchedule ?? "미확인" : eventDates(n)}</dd></div>
            <div><dt>장소</dt><dd>{n.location ?? "미확인"}</dd></div>
            <div><dt>신청 시작</dt><dd>{displayDate(n.start)}</dd></div>
            <div><dt>신청 마감</dt><dd>{displayDate(n.deadline)}</dd></div>
            <div><dt>{club ? "회비" : "비용"}</dt><dd>{n.cost ?? "미확인"}</dd></div>
            <div><dt>{club ? "지원 대상" : "참가 대상"}</dt><dd>{n.audience?.join(" · ") ?? "미확인 · 제한 없음을 뜻하지 않습니다"}</dd></div>
            <div><dt>{club ? "지원 조건" : "참가 조건"}</dt><dd>{n.qualification ?? "미확인 · 공식 안내를 확인해 주세요"}</dd></div>
          </dl>
          <div className="official-links">
            <a className="primary" href={n.officialUrl} target="_blank" rel="noreferrer">공식 사이트 ↗<span className="sr-only"> (새 창)</span></a>
            {n.registrationUrl && <a href={n.registrationUrl} target="_blank" rel="noreferrer">{club ? "공식 모집 안내" : n.status === "ended" ? "당시 등록 안내" : n.status === "open" ? "공식 참가 신청" : "등록 안내 확인"} ↗<span className="sr-only"> (새 창)</span></a>}
          </div>
          <p>{club ? "모집 직군·분야" : "주제·분야"}: {n.roles.join(" · ") || "미확인"}</p>
          <details open={n.current}><summary>제공 경험과 근거</summary>
            {n.activities.length ? n.activities.map((a, i) => <div className="activity" key={i}><b>{a.action}{a.target ? ` | ${a.target}` : ""}</b><p>{a.evidence}</p></div>) : <p>이 회차에서 확인한 제공 경험이 없습니다.</p>}
          </details>
          <details className="source-details"><summary>공식 출처 · 확인일 {displayDate(n.sources[0].checkedAt)}</summary>
            {n.sources.map(source => <div className="source-item" key={source.url}><a href={source.url} target="_blank" rel="noreferrer">{source.label} ↗<span className="sr-only"> (새 창)</span></a><p>{source.evidence}</p><small>확인일 {displayDate(source.checkedAt)}</small></div>)}
          </details>
        </article>)}
      </aside>
      <section className="description">
        <h2>참여 전 확인해 주세요</h2>
        <p>고등학생·대학생·취준생 모두 탐색할 수 있지만, 실제 참가·지원 자격은 프로그램별로 다릅니다. 자격을 자동 판정하거나 탐색 결과에서 제외하지 않습니다. 연사의 발표는 참가자의 발표 경험으로 간주하지 않습니다.</p>
        <p>{displayDate(snapshotDate)}에 공식 출처를 일회성으로 확인한 정보입니다. 실시간 모집·등록 가능 여부와 변경된 조건은 공식 사이트에서 확인하세요. 신청·결제는 주최자의 사이트에서 진행합니다.</p>
        <p className="muted">이미지: {p.coverSource.kind === "og" ? "공식 페이지 공유이미지" : p.coverSource.kind === "capture" ? "공식 페이지 대표영역 캡처" : "기본 썸네일"} · <a href={p.coverSource.pageUrl} target="_blank" rel="noreferrer">출처 ↗ (새 창)</a> · 확인일 {displayDate(p.coverSource.checkedAt)}.</p>
      </section>
    </div>
  </>;
}
