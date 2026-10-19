import Link from "next/link";
import styles from "./activity-card.module.css";

/** Recruitment state the chip shows (contract "발견 카드 리뉴얼"): 모집 중 teal / 모집 예정 mint / 마감 grey. */
export type StatusTone = "open" | "soon" | "closed";
const statusText: Record<StatusTone, string> = { open: "모집 중", soon: "모집 예정", closed: "마감" };

/**
 * 펼친 프로그램 안의 활동 카드 한 장 (contract "발견 카드 리뉴얼 — 2026-10-19"): 로고 없이 활동명과 우상단 모집 상태 칩,
 * 아래에 `모집`·`활동`·`장소` 라벨+값 줄. 모집이 지나지 않았으면(`recruitmentActive`) `모집` 줄을 청록 굵은 글씨와
 * 옅은 민트 바탕으로 강조한다. 카드 전체가 활동 상세 링크라 칩 글자도 링크 이름에 들어간다.
 */
export function ActivityCard({
  id,
  name,
  statusTone,
  recruitmentLabel,
  recruitmentActive,
  activityLabel,
  placeName,
}: {
  id: number;
  name: string;
  statusTone: StatusTone;
  /** 모집 기간 문구; 없으면 `미정`. */
  recruitmentLabel: string;
  /** 모집 중·모집 예정이고 기간이 있을 때 참 → 강조. */
  recruitmentActive: boolean;
  /** 활동 기간 문구. */
  activityLabel: string;
  /** 장소 이름; 없으면 줄을 생략한다. */
  placeName?: string | null;
}) {
  return (
    <Link className={styles.card} href={`/activities/${id}`}>
      <h2 className={styles.name}>{name}</h2>
      <span className={`${styles.status} ${styles[statusTone]}`}>{statusText[statusTone]}</span>
      <dl className={styles.rows}>
        <div className={recruitmentActive ? styles.active : undefined}>
          <dt>모집</dt>
          <dd>{recruitmentLabel || "미정"}</dd>
        </div>
        <div>
          <dt>활동</dt>
          <dd>{activityLabel || "일정 미확인"}</dd>
        </div>
        {placeName && (
          <div>
            <dt>장소</dt>
            <dd>{placeName}</dd>
          </div>
        )}
      </dl>
    </Link>
  );
}
