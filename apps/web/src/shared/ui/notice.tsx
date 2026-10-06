import styles from "./notice.module.css";

/** 화면 안 안내 상자. `info`는 알림(status), `warning`은 행동이 필요한 안내(alert)로 읽힌다. */
export function Notice({
  tone = "info",
  title,
  children,
}: {
  tone?: "info" | "warning";
  title?: string;
  children: React.ReactNode;
}) {
  return (
    <div className={styles.notice} data-tone={tone} role={tone === "warning" ? "alert" : "status"}>
      <svg className={styles.icon} width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" aria-hidden="true">
        <circle cx="12" cy="12" r="9" />
        <path d={tone === "warning" ? "M12 7v6m0 4h.01" : "M12 11v6m0-10h.01"} />
      </svg>
      <div className={styles.body}>
        {title && <strong className={styles.title}>{title}</strong>}
        <div>{children}</div>
      </div>
    </div>
  );
}

/** 카카오톡 등 앱 안 브라우저로 열렸을 때 외부 브라우저로 열도록 안내한다. 감지와 링크 생성은 화면이 맡는다. */
export function ExternalBrowserNotice({
  appName,
  externalHref,
  onCopyLink,
}: {
  appName?: string;
  externalHref?: string;
  onCopyLink?: () => void;
}) {
  return (
    <Notice tone="warning" title={`${appName ?? "앱"} 안의 브라우저로 열렸어요`}>
      <p>저장한 명함을 다시 보려면 Safari나 Chrome 같은 기본 브라우저에서 열어 주세요. 여기서 저장해도 기본 브라우저에서는 보이지 않아요.</p>
      {(externalHref || onCopyLink) && (
        <p className={styles.actions}>
          {externalHref && (
            <a className={styles.action} href={externalHref}>
              기본 브라우저로 열기
            </a>
          )}
          {onCopyLink && (
            <button type="button" className={styles.action} onClick={onCopyLink}>
              링크 복사
            </button>
          )}
        </p>
      )}
    </Notice>
  );
}
