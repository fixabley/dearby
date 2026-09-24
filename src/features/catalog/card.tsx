import { OrganizationAvatar } from "./organization-avatar";
import Link from "next/link";
import Image from "next/image";
import { organizations } from "./data";
import { emptyFilters, representativeNotice, activeNotices, noticeStatus, displayDate, type Program, type Filters } from "./model";
import { SaveButton } from "../saved/save-button";
export function ProgramCard({ program: p, filters = emptyFilters, eager = false }: { program: Program; filters?: Filters; eager?: boolean }) {
  const org = organizations.find(o => o.id === p.orgId)!;
  const matching = representativeNotice(p, filters);
  if (!matching) return null;
  const notice = activeNotices(p).find(n => n.id === matching.id);
  return (
    <article className="program-card" data-notice-id={notice?.id}>
      <Link href={`/programs/${p.id}`} className="cover-link">
        <Image src={p.cover} alt={`${p.title} ${p.coverSource.kind === "og" ? "공식 공유이미지" : "공식 페이지 캡처"}`} width={800} height={450} loading={eager ? "eager" : "lazy"} sizes="(max-width: 720px) 90vw, 33vw" />
      </Link>
      <div className="card-body">
        <OrganizationAvatar organization={org} />
        <div className="card-copy">
          <div className="card-kind">{p.category}</div>
          <Link href={`/programs/${p.id}`} className="card-title">{p.title}</Link>
          <div className="card-org">{org.name}</div>
          <p className="program-summary">{p.subtitle}</p>
          <div className={`card-meta event-status ${notice ? `status-${notice.status}` : ""}`}>{notice ? `${noticeStatus(notice)} · ${notice.round}` : "현재 확인된 모집 공고 없음"}</div>
          {notice && <div className="card-meta">{notice.status === "scheduled" ? `접수 시작 ${displayDate(notice.start)}` : `마감 ${displayDate(notice.deadline)}`}</div>}
        </div>
        <SaveButton kind="programs" id={p.id} compact />
      </div>
    </article>
  );
}
