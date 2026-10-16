import { type Card, type History, contactHref } from "@/lib/models";
import { Markdown } from "@/lib/markdown";
import { Icon } from "@/shared/ui/icon";

/** 공개 명함 본문: 이름·직무·소개, 공개 연락처, 활동 이력. `/cards/:id`와 공유 페이지가 함께 쓴다. */
export function PublicCardBody({ card }: { card: Card }) {
  return (
    <>
      <div className="resume-heading">
        <p className="eyebrow">공유받은 공개 명함 · 이력서</p>
        <div className="person">
          <span className="avatar" aria-hidden="true">
            {Array.from(card.profileName)[0] || <Icon name="card" />}
          </span>
          <div>
            <h1>{card.profileName || "이름 미등록"}</h1>
            <p className="job">{card.job}</p>
          </div>
        </div>
        <div className="lead card-body">
          <Markdown source={card.introduction} />
        </div>
        {card.description && <p className="muted">{card.description}</p>}
      </div>
      <section className="detail-section">
        <h2>연락처</h2>
        {!card.contacts.length ? (
          <p className="muted">공개된 연락처가 없어요.</p>
        ) : (
          <ul className="contacts">
            {card.contacts.map((contact) => {
              const href = contactHref(contact);
              const content = (
                <>
                  <span className="contact-label">
                    {contact.label ||
                      {
                        email: "이메일",
                        phone: "전화",
                        kakao: "카카오톡",
                        instagram: "Instagram",
                        github: "GitHub",
                        behance: "Behance",
                      }[contact.kind]}
                  </span>
                  <span className="contact-value">{contact.value}</span>
                  {href && <Icon name="external" size={16} />}
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
      <section className="detail-section">
        <div className="section-heading">
          <h2>활동 이력</h2>
          <span className="muted">본인 입력 · 미확인 표시는 인증자료가 확인되지 않은 것</span>
        </div>
        {!card.histories.length ? (
          <p className="muted">공개된 활동 이력이 없어요.</p>
        ) : (
          <ol className="timeline">
            {card.histories.map((history) => (
              <li key={history.id}>
                <p className="muted">{period(history)}</p>
                <h3>{chain(history).join(" · ")}</h3>
                <p>
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
    </>
  );
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
