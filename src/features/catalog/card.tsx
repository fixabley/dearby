import Link from "next/link";
import Image from "next/image";
import { organizations } from "./data";
import { emptyFilters, representativeNotice, noticeStatus, eventDates, displayDate, type Program, type Filters } from "./model";
import { SaveButton } from "../saved/save-button";
export function ProgramCard({ program: p, filters = emptyFilters, eager = false }: { program: Program; filters?: Filters; eager?: boolean }) {
  const org = organizations.find(o => o.id === p.orgId)!;
  const notice = representativeNotice(p, filters);
  if (!notice) return null;
  return (
    <article className="program-card" data-notice-id={notice.id}>
      <Link href={`/programs/${p.id}`} className="cover-link">
        <Image src={p.cover} alt={`${p.title} ${notice.round} ${p.coverSource.kind === "og" ? "공식 공유이미지" : p.coverSource.kind === "capture" ? "공식 페이지 캡처" : "기본 썸네일"}`} width={800} height={450} loading={eager ? "eager" : "lazy"} sizes="(max-width: 720px) 100vw, (max-width: 1699px) 33vw, 25vw" />
      </Link>
      <div className="card-body">
        <span className="avatar" style={{ background: org.color }} aria-hidden="true">{org.initial}</span>
        <div className="card-copy">
          <Link href={`/programs/${p.id}`} className="card-title">{p.title} {notice.round}</Link>
          <div className="card-org">{org.name}</div>
          <div className={`card-meta event-status status-${notice.status}`}>{noticeStatus(notice)}{notice.status === "ended" && " · 지난 행사"}</div>
          <div className="card-meta"><strong>개최</strong> {eventDates(notice)}</div>
          <div className="card-meta">{notice.location ?? "장소 미확인"}</div>
          <div className="card-meta"><strong>비용</strong> {notice.cost ?? "미확인"}</div>
          {notice.status !== "ended" && <div className="card-meta"><strong>신청 마감</strong> {displayDate(notice.deadline)}</div>}
          <div className="card-experience">{notice.roles.length ? notice.roles.join(" · ") : "분야 미확인"}</div>
        </div>
        <SaveButton kind="programs" id={p.id} compact />
      </div>
    </article>
  );
}
