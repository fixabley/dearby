import { BrandSymbol } from "./brand-symbol";
import { Icon } from "./icon";
import styles from "./install-prompt.module.css";

/**
 * 홈 화면 웹앱 설치 안내. Android는 설치 이벤트가 있을 때 버튼을, iOS Safari는 공유 메뉴 안내를 보여 준다.
 * 플랫폼 판단·설치 이벤트·이미 설치된 경우 숨김은 화면이 맡는다.
 */
export function InstallPrompt({
  platform,
  onInstall,
  onDismiss,
  separateStorage = false,
}: {
  platform: "android" | "ios";
  onInstall?: () => void;
  onDismiss?: () => void;
  /** iOS 홈 화면 앱이 Safari와 쿠키를 따로 쓰는 것이 확인되면 true. */
  separateStorage?: boolean;
}) {
  return (
    <section className={styles.prompt} aria-label="홈 화면에 추가">
      <BrandSymbol className={styles.logo} size={44} />
      <div className={styles.body}>
        <strong>홈 화면에서 바로 열기</strong>
        {platform === "android" ? (
          <p>저장한 명함을 홈 화면에서 바로 열 수 있어요.</p>
        ) : (
          <p>
            Safari의{" "}
            <span className={styles.share} role="img" aria-label="공유">
              <Icon name="share" size="inline" />
            </span>{" "}
            공유 버튼(보이지 않으면 ⋯ 메뉴 안)을 누른 뒤 <b>홈 화면에 추가</b>를 고르세요.
            {separateStorage && <span className={styles.note}>홈 화면 앱은 Safari와 따로 저장돼요.</span>}
          </p>
        )}
        <div className={styles.actions}>
          {platform === "android" && onInstall && (
            <button type="button" className={styles.primary} onClick={onInstall}>
              홈 화면에 추가
            </button>
          )}
          {onDismiss && (
            <button type="button" className={styles.secondary} onClick={onDismiss}>
              나중에
            </button>
          )}
        </div>
      </div>
    </section>
  );
}
