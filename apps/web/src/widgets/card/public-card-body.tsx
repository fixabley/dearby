import { type Card, contactHref } from "@/lib/models";
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
        <p className="lead">{card.introduction}</p>
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
          <span className="muted">직접 작성</span>
        </div>
        {!card.histories.length ? (
          <p className="muted">공개된 활동 이력이 없어요.</p>
        ) : (
          <ol className="timeline">
            {card.histories.map((history) => (
              <li key={history.id}>
                <p className="muted">
                  {history.startDate} — {history.endDate ?? "현재"}
                </p>
                <h3>{history.title}</h3>
                <p>{history.role}</p>
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
