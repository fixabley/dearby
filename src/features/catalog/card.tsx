import Link from "next/link";
import Image from "next/image";
import { organizations } from "./data";
import {
  emptyFilters,
  representativeNotice,
  recruitment,
  type Program,
  type Filters,
} from "./model";
import { SaveButton } from "../saved/save-button";
export function ProgramCard({
  program: p,
  filters = emptyFilters,
}: {
  program: Program;
  filters?: Filters;
}) {
  const org = organizations.find((o) => o.id === p.orgId)!;
  const notice = representativeNotice(p, filters);
  if (!notice) return null;
  const status = `${notice.round} · ${notice.open ? "모집 중" : "모집 종료"}`;
  const groups = new Map<string, Set<string>>();
  for (const activity of notice.activities) {
    const values = groups.get(activity.action) ?? new Set<string>();
    const value = [activity.target, activity.method]
      .filter(Boolean)
      .join(" · ");
    if (value) values.add(value);
    groups.set(activity.action, values);
  }
  const grouped = Array.from(
    groups,
    ([action, values]) =>
      `${action}${values.size ? " | " + [...values].join(", ") : ""}`,
  );
  return (
    <article className="program-card" data-notice-id={notice.id}>
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
            <strong>모집</strong> {notice.roles.join(" · ")}
          </div>
          <div className="card-meta">
            <strong>대상</strong> {notice.audience.join(" · ")}
          </div>
          <div className="card-meta card-deadline">
            <strong>{notice.deadline.slice(5).replace("-", ".")} 마감</strong> · {p.location}
          </div>
          {!notice.open && recruitment(p, filters) === "선택 직무 종료 · 다른 직무 모집 중" && (
            <div className="card-meta">선택 직무 종료 · 다른 직무 모집 중</div>
          )}
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
