import Link from "next/link";
import { Icon } from "@/shared/ui/icon";
import styles from "./activity-card.module.css";

/**
 * 활동 목록의 카드 한 장. 화면이 조직 이름·모집 상태 문구를 계산해 넘기고, 누르면 활동 상세로 간다.
 * `imageUrl`(https)이 있으면 왼쪽 타일에 이미지를, 없으면 달력 아이콘과 조직 이름을 보인다.
 * 타일은 카드 글자와 같은 정보라 접근성에서 숨긴다.
 */
export function ActivityCard({
  id,
  name,
  description,
  organizationName,
  statusLabel,
  dateLabel,
  imageUrl,
  tags = [],
}: {
  id: number;
  name: string;
  description: string;
  organizationName: string;
  statusLabel: string;
  dateLabel: string;
  imageUrl?: string;
  /** 얻을 수 있는 것 태그 (contract "태그"); rendered as neutral chips. */
  tags?: { id: number; name: string }[];
}) {
  return (
    <Link className="activity-card" href={`/activities/${id}`}>
      <span className="activity-art" aria-hidden="true">
        {imageUrl ? (
          // Activity images come from arbitrary official hosts, so next/image optimization is not configured for them.
          // eslint-disable-next-line @next/next/no-img-element
          <img className={styles.image} src={imageUrl} alt="" />
        ) : (
          <span className={styles.tile}>
            <Icon name="calendar" size={34} />
            <span className={styles.organization}>{organizationName}</span>
          </span>
        )}
      </span>
      <div className="activity-copy">
        <p className="eyebrow">
          {organizationName} <span className="dot">·</span> {statusLabel}
        </p>
        <h2>{name}</h2>
        <p className="summary">{description}</p>
        <div className="activity-meta">
          <span>
            <Icon name="calendar" size={17} />
            {dateLabel || "일정 미확인"}
          </span>
        </div>
        {tags.length > 0 && (
          <ul className="tag-chips" aria-label="얻을 수 있는 것">
            {tags.map((tag) => (
              <li key={tag.id} className="badge neutral">
                {tag.name}
              </li>
            ))}
          </ul>
        )}
      </div>
      <span className="card-chevron" aria-hidden="true">
        ›
      </span>
    </Link>
  );
}
