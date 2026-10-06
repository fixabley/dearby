import Link from "next/link";
import styles from "./received-card-row.module.css";
import { TogetherActivityLabel } from "./together-activity";

/** 받은 명함 목록 한 줄. 화면이 명함을 이름·직무·첫 활동 값으로 바꿔 넘기고, 누르면 `href`의 상세로 간다. */
export function ReceivedCardRow({
  href,
  name,
  job,
  activity,
}: {
  href: string;
  name: string;
  job: string;
  activity?: { title: string; otherCount: number };
}) {
  return (
    <Link className={styles.row} href={href}>
      <span className={styles.avatar} aria-hidden="true">
        {Array.from(name)[0]}
      </span>
      <span className={styles.copy}>
        <strong className={styles.name}>{name}</strong>
        {job && <span className={styles.job}>{job}</span>}
        {activity && (
          <TogetherActivityLabel
            title={activity.title}
            otherCount={activity.otherCount}
          />
        )}
      </span>
      <span className={styles.chevron} aria-hidden="true">
        ›
      </span>
    </Link>
  );
}
