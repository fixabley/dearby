import Link from "next/link";
import Image from "next/image";
import { organizations } from "./data";
import {
  emptyFilters,
  recruitment,
  matchingNotices,
  type Program,
  type Filters,
} from "./model";
import { SaveButton } from "../saved/provider";
export function ProgramCard({
  program: p,
  filters = emptyFilters,
}: {
  program: Program;
  filters?: Filters;
}) {
  const org = organizations.find((o) => o.id === p.orgId)!;
  const status = recruitment(p, filters);
  const notice = matchingNotices(p, filters)[0] ?? p.notices[0];
  const grouped = Object.entries(
    Object.groupBy(notice.activities, (a) => a.action),
  ).map(([action, items]) => {
    const values = [
      ...new Set(
        items!
          .map((a) => [a.target, a.method].filter(Boolean).join(" · "))
          .filter(Boolean),
      ),
    ];
    return `${action}${values.length ? " | " + values.join(", ") : ""}`;
  });
  return (
    <article className="program-card">
      <Link href={`/programs/${p.id}`} className="cover-link">
        <Image
          src={p.cover}
          alt={`${p.title} 프로그램 포스터`}
          width={800}
          height={450}
          loading="eager"
        />
        <span
          className={`cover-status ${status.includes("종료") ? "ended" : ""}`}
        >
          {status}
        </span>
      </Link>
      <div className="card-body">
        <span
          className="avatar"
          style={{ background: org.color }}
          aria-hidden="true"
        >
          {org.initial}
        </span>
        <div className="card-copy">
          <Link href={`/programs/${p.id}`} className="card-title">
            {p.title}
          </Link>
          <div className="card-org">
            {org.name} <span aria-label="샘플">· 샘플</span>
          </div>
          <div className="card-meta">
            {p.category} · {p.location} · {notice.round}
          </div>
          <div className="card-experience">
            {grouped.slice(0, 2).join(" / ")}
            {grouped.length > 2 ? ` 외 ${grouped.length - 2}개` : ""}
          </div>
        </div>
        <SaveButton kind="programs" id={p.id} compact />
      </div>
    </article>
  );
}
