import Link from "next/link";
import type { Card } from "@/lib/models";
import { Icon } from "@/shared/ui/icon";
import { Notice } from "@/shared/ui/notice";
import { PublicCardBody } from "./public-card-body";
import styles from "./shared-card.module.css";

export type SharedCardSaveState = "idle" | "saving" | "saved" | "error";

/**
 * `/s/:shareId` 공유 명함 본문. 공개 명함, 함께 공유된 활동, 저장 버튼 하나를 보이고,
 * 저장한 뒤에는 이 브라우저 저장 안내와 선택형 홈 화면 추가 안내(`afterSave`)를 보인다.
 * 공유 조회·저장 요청·설치 안내 조건, 저장 뒤 사라진 버튼 대신 초점을 옮기는 일은 화면이 맡는다.
 */
export function SharedCardView({
  card,
  activities,
  saveState,
  errorMessage,
  onSave,
  afterSave,
}: {
  card: Card;
  activities: { id: string; title: string }[];
  saveState: SharedCardSaveState;
  errorMessage?: string;
  onSave: () => void;
  afterSave?: React.ReactNode;
}) {
  return (
    <article className="public-card detail">
      <PublicCardBody card={card} />
      {activities.length > 0 && (
        <section className="detail-section">
          <h2>함께 공유된 활동</h2>
          <p className="muted">
            보낸 사람이 고른 활동이에요. 참가 확인은 아니에요.
          </p>
          <ul className={styles.activities}>
            {activities.map((activity) => (
              <li key={activity.id}>
                <Link className="text-link" href={`/activities/${activity.id}`}>
                  {activity.title}
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}
      <div className="save-area">
        {saveState === "saved" ? (
          <div className={styles.saved}>
            <Notice>
              이 브라우저에만 저장됐어요. 쿠키를 지우거나 다른 브라우저·앱에서
              열면 보이지 않아요.
            </Notice>
            <Link className="button secondary" href="/saved">
              저장한 명함 보기
            </Link>
            {afterSave}
          </div>
        ) : (
          <>
            {saveState === "error" && errorMessage && (
              <p className="error-message" role="alert">
                {errorMessage}
              </p>
            )}
            <button
              type="button"
              className="button"
              disabled={saveState === "saving"}
              onClick={onSave}
            >
              <Icon name="bookmark" size={20} />
              {saveState === "saving" ? "저장 확인 중…" : "명함 저장"}
            </button>
            <p className="cookie-note">로그인 없이 이 브라우저에 저장해요.</p>
          </>
        )}
      </div>
    </article>
  );
}
