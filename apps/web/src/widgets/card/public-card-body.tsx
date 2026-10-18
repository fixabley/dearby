import { type Card, type History, contactHref } from "@/lib/models";
import { Markdown } from "@/lib/markdown";
import { Icon } from "@/shared/ui/icon";

type ContactKind = Card["contacts"][number]["kind"];

/** Kind names double as the label fallback and the icon name (contract "명함 표현 — 2026-10-18"). */
const CONTACT_KIND_NAME: Record<ContactKind, string> = {
  phone: "전화",
  email: "이메일",
  kakao: "카카오톡",
  instagram: "Instagram",
  github: "GitHub",
  behance: "Behance",
};

/** 공개 명함 카드 면: 머리(아바타·이름·직무 칩·소속), 소개, 연락처 행, 활동 이력 행. `/cards/:id`가 쓴다. */
export function PublicCardBody({ card }: { card: Card }) {
  const jobs = card.job
    .split(" · ")
    .map((job) => job.trim())
    .filter(Boolean);
  const organization = latestOrganization(card.histories);
  return (
    <div className="card-sheet">
      <header className="card-head">
        <span className="avatar" aria-hidden="true">
          {Array.from(card.profileName)[0] || <Icon name="card" />}
        </span>
        <h1>{card.profileName || "이름 미등록"}</h1>
        {jobs.length > 0 && (
          <ul className="job-chips" aria-label="직무">
            {jobs.map((job, index) => (
              <li key={`${index}-${job}`}>{job}</li>
            ))}
          </ul>
        )}
        {organization && <p className="card-org">{organization}</p>}
      </header>
      {card.introduction.trim() && (
        <div className="card-intro card-body">
          <Markdown source={card.introduction} />
        </div>
      )}
      <section className="card-section">
        <h2>연락처</h2>
        {!card.contacts.length ? (
          <p className="muted">공개된 연락처가 없어요</p>
        ) : (
          <ul className="contact-rows">
            {card.contacts.map((contact) => {
              const href = contactHref(contact);
              const content = (
                <>
                  <span className="contact-icon">
                    <Icon name={contact.kind} size={20} />
                  </span>
                  <span className="contact-text">
                    <span className="contact-label">
                      {contact.label || CONTACT_KIND_NAME[contact.kind]}
                    </span>
                    <span className="contact-value">{contact.value}</span>
                  </span>
                  {href && (
                    <span className="contact-external">
                      <Icon name="external" size={16} />
                    </span>
                  )}
                </>
              );
              return (
                <li key={contact.id}>
                  {href ? (
                    <a
                      href={href}
                      target={href.startsWith("http") ? "_blank" : undefined}
                      rel="noopener noreferrer"
                    >
                      {content}
                    </a>
                  ) : (
                    <div>{content}</div>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </section>
      <section className="card-section">
        <h2>활동 이력</h2>
        {!card.histories.length ? (
          <p className="muted">공개된 활동 이력이 없어요</p>
        ) : (
          <ol className="history-rows">
            {card.histories.map((history) => (
              <li key={history.id}>
                <p className="history-period">{period(history)}</p>
                <h3>{chain(history).join(" · ")}</h3>
                <p className="history-role">
                  {history.role?.name ?? history.roleName}
                  {history.verification === "verified" ? (
                    <span className="badge badge-verified" title="관리자가 인증자료를 확인했어요">✓ 확인됨</span>
                  ) : (
                    <span className="badge badge-unverified" title="인증자료가 확인되지 않았어요">미확인</span>
                  )}
                </p>
                {history.description && (
                  <p className="history-description">{history.description}</p>
                )}
              </li>
            ))}
          </ol>
        )}
      </section>
    </div>
  );
}

/** 소속 줄: 승인된 이력 중 진행 중인 것, 그다음 가장 최근에 시작한 것의 조직 이름. */
function latestOrganization(histories: History[]): string | undefined {
  const latest = histories
    .filter((history) => history.approval === "approved")
    .sort((a, b) => {
      if (!a.endDate !== !b.endDate) return a.endDate ? 1 : -1;
      return b.startDate.localeCompare(a.startDate);
    })[0];
  return latest ? (latest.organization?.name ?? latest.organizationName) ?? undefined : undefined;
}

/** 조직 → 프로그램 → 활동 순서로 이름을 모은다(카탈로그 이름 또는 직접 입력). */
function chain(history: History): string[] {
  return [
    history.organization?.name ?? history.organizationName,
    history.program?.name ?? history.programName,
    history.activity?.name ?? history.activityName,
  ].filter((name): name is string => !!name);
}

/** `2026.03 – 2026.06`, 진행 중이면 `2026.03 – 진행 중`. */
function period(history: History): string {
  const month = (date: string) => date.slice(0, 7).replace("-", ".");
  return `${month(history.startDate)} – ${history.endDate ? month(history.endDate) : "진행 중"}`;
}
