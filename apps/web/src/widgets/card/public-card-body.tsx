import { type Card, type History, contactHref } from "@/lib/models";
import { Markdown } from "@/lib/markdown";
import { Badge } from "@/shared/ui/badge";
import { Card as Surface } from "@/shared/ui/card";
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

/**
 * 공개 명함 카드 면 (contract "명함 표현", iOS `CardView`): one grouped surface — centred head (avatar, name, job capsules, organization),
 * introduction, 연락처 rows (kind icon on the left, label for screen readers only, whole row is the link) and 활동 이력 rows.
 */
export function PublicCardBody({ card }: { card: Card }) {
  const jobs = card.job
    .split(" · ")
    .map((job) => job.trim())
    .filter(Boolean);
  const organization = latestOrganization(card.histories);
  return (
    <Surface className="flex flex-col gap-6 p-5">
      <header className="flex flex-col items-center gap-2.5 text-center">
        <span
          aria-hidden="true"
          className="grid size-14 place-items-center rounded-full bg-mint text-title2 font-bold text-teal"
        >
          {Array.from(card.profileName)[0] || <Icon name="card" />}
        </span>
        <h1 className="text-title2 font-bold break-keep">{card.profileName || "이름 미등록"}</h1>
        {jobs.length > 0 && (
          <ul className="flex flex-wrap justify-center gap-1.5" aria-label="직무">
            {jobs.map((job, index) => (
              <li key={`${index}-${job}`} className="rounded-full bg-fill px-2.5 py-1 text-caption font-semibold">
                {job}
              </li>
            ))}
          </ul>
        )}
        {organization && <p className="text-footnote text-secondary-label">{organization}</p>}
      </header>
      {card.introduction.trim() && (
        <div className="markdown">
          <Markdown source={card.introduction} />
        </div>
      )}
      <section className="flex flex-col gap-1">
        <h2 className="text-subheadline font-semibold text-secondary-label">연락처</h2>
        {!card.contacts.length ? (
          <p className="py-1.5 text-subheadline text-secondary-label">공개된 연락처가 없어요</p>
        ) : (
          <ul className="divide-y divide-separator">
            {card.contacts.map((contact) => {
              const href = contactHref(contact);
              const content = (
                <>
                  <Icon name={contact.kind === "email" ? "mail" : contact.kind} tone="teal" />
                  <span className="sr-only">{contact.label || CONTACT_KIND_NAME[contact.kind]}: </span>
                  <span className="flex-1 text-subheadline break-all">{contact.value}</span>
                  {href && <Icon name="external-link" size="inline" tone="secondary" />}
                </>
              );
              const row = "flex min-h-11 items-center gap-3 py-2";
              return (
                <li key={contact.id}>
                  {href ? (
                    <a
                      href={href}
                      target={href.startsWith("http") ? "_blank" : undefined}
                      rel="noopener noreferrer"
                      className={`${row} active:opacity-70`}
                    >
                      {content}
                    </a>
                  ) : (
                    <div className={row}>{content}</div>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </section>
      <section className="flex flex-col gap-1">
        <h2 className="text-subheadline font-semibold text-secondary-label">활동 이력</h2>
        {!card.histories.length ? (
          <p className="py-1.5 text-subheadline text-secondary-label">공개된 활동 이력이 없어요</p>
        ) : (
          <ol className="divide-y divide-separator">
            {card.histories.map((history) => (
              <li key={history.id} className="flex flex-col gap-1 py-2">
                <p className="text-caption text-secondary-label">{period(history)}</p>
                <h3 className="text-subheadline font-bold break-keep">{chain(history).join(" · ")}</h3>
                <p className="flex flex-wrap items-center gap-2 text-subheadline text-secondary-label">
                  {history.role?.name ?? history.roleName}
                  {history.verification === "verified" ? (
                    <Badge variant="tinted" title="관리자가 인증자료를 확인했어요">
                      ✓ 확인됨
                    </Badge>
                  ) : (
                    <Badge title="인증자료가 확인되지 않았어요">미확인</Badge>
                  )}
                </p>
                {history.description && <p className="text-footnote break-keep">{history.description}</p>}
              </li>
            ))}
          </ol>
        )}
      </section>
    </Surface>
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
