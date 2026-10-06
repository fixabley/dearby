import Link from "next/link";
import { Icon } from "@/shared/ui/icon";
import styles from "./activity-card.module.css";

/**
 * 활동 목록의 카드 한 장. 화면이 조직 이름·참여 방식 문구를 계산해 넘기고, 누르면 활동 상세로 간다.
 * `applyUrl`을 주면 카드 아래에 공식 신청 바로가기와 마감일 한 줄을 붙인다. 보일지 여부는 화면이 정한다.
 */
export function ActivityCard({
  id,
  title,
  summary,
  organizationName,
  participationLabel,
  dateLabel,
  location,
  applyUrl,
  recruitmentEndAt,
}: {
  id: string;
  title: string;
  summary: string;
  organizationName: string;
  participationLabel: string;
  dateLabel: string;
  location: string | null;
  applyUrl?: string;
  recruitmentEndAt?: string | null;
}) {
  const card = (
    <Link className="activity-card" href={`/activities/${id}`}>
      <span className="activity-art" aria-hidden="true">
        <Icon name="calendar" size={34} />
      </span>
      <div className="activity-copy">
        <p className="eyebrow">
          {organizationName} <span className="dot">·</span> {participationLabel}
        </p>
        <h2>{title}</h2>
        <p className="summary">{summary}</p>
        <div className="activity-meta">
          <span>
            <Icon name="calendar" size={17} />
            {dateLabel || "일정 미확인"}
          </span>
          <span>
            <Icon name="pin" size={17} />
            {location || "장소 미확인"}
          </span>
        </div>
      </div>
      <span className="card-chevron" aria-hidden="true">
        ›
      </span>
    </Link>
  );
  if (!applyUrl) return card;
  const deadline = recruitmentEndAt
    ? new Date(recruitmentEndAt).toLocaleDateString("ko-KR", {
        timeZone: "Asia/Seoul",
        month: "long",
        day: "numeric",
        weekday: "short",
      }) + " 마감"
    : "마감일 미확인";
  return (
    <div className={styles.withApply}>
      {card}
      <div className={styles.apply}>
        <span className={styles.deadline}>{deadline}</span>
        <a
          className="button secondary"
          href={applyUrl}
          target="_blank"
          rel="noopener noreferrer"
          aria-label={`${title} 공식 사이트에서 신청 (새 창)`}
        >
          공식 사이트에서 신청 <Icon name="external" size={18} />
        </a>
      </div>
    </div>
  );
}
