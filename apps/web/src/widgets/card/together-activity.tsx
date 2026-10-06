import styles from "./together-activity.module.css";

/** 받은 명함의 "함께한 활동 · ○○ 외 N개" 라벨. 확인 아이콘 없이 긴 활동 이름만 말줄임한다. 첫 활동(일정이 가장 이른 것) 선택은 호출하는 쪽이 정한다. */
export function TogetherActivityLabel({
  title,
  otherCount = 0,
}: {
  title: string;
  otherCount?: number;
}) {
  const suffix = otherCount > 0 ? ` 외 ${otherCount}개` : "";
  return (
    <span className={styles.label} title={`함께한 활동 · ${title}${suffix}`}>
      <span className={styles.fixed}>함께한 활동 · </span>
      <span className={styles.title}>{title}</span>
      {suffix && <span className={styles.fixed}>{suffix}</span>}
    </span>
  );
}
