import styles from "./section-header.module.css";

/** 묶음 머리글. `expanded`를 주면 누를 때 `onToggle`을 부르는 접고 펴는 버튼이 되고, 펼침 상태를 화살표와 aria-expanded로 알린다. */
export function SectionHeader({
  title,
  count,
  expanded,
  onToggle,
  controls,
}: {
  title: string;
  count: number;
  expanded?: boolean;
  onToggle?: () => void;
  /** 접히는 목록의 id. */
  controls?: string;
}) {
  const label = (
    <>
      <span className={styles.title}>{title}</span>
      <span className={styles.count} aria-label={`${count}개`}>
        {count}
      </span>
    </>
  );
  return (
    <h2 className={styles.header}>
      {expanded === undefined ? (
        <span className={styles.row}>{label}</span>
      ) : (
        <button
          type="button"
          className={styles.row}
          aria-expanded={expanded}
          aria-controls={controls}
          onClick={onToggle}
        >
          {label}
          <svg
            className={styles.chevron}
            data-expanded={expanded}
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            <path d="m6 9 6 6 6-6" />
          </svg>
        </button>
      )}
    </h2>
  );
}
