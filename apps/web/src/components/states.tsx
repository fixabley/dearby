import Link from "next/link";
import { Icon } from "./icon";
export function Loading() {
  return (
    <div className="state" role="status">
      <span className="loader" />
      불러오는 중이에요.
    </div>
  );
}
export function Failure({
  message,
  retry,
}: {
  message: string;
  retry: () => void;
}) {
  return (
    <div className="state" role="alert">
      <h2>다시 확인해 주세요</h2>
      <p>{message}</p>
      <button className="button secondary" onClick={retry}>
        다시 시도
      </button>
    </div>
  );
}
export function MissingCard() {
  return (
    <div className="state">
      <Icon name="card" size={40} />
      <h1>명함을 볼 수 없어요</h1>
      <p>
        공개가 취소되었거나 존재하지 않는 명함이에요.
        <br />
        공유한 분에게 새 링크를 요청해 주세요.
      </p>
      <Link className="button secondary" href="/saved">
        저장한 명함 보기
      </Link>
    </div>
  );
}
export function Empty({
  title,
  children,
  kind = "compass",
}: {
  title: string;
  children: React.ReactNode;
  kind?: "compass" | "bookmark";
}) {
  return (
    <div className="state empty">
      <span className="state-icon">
        <Icon name={kind} size={32} />
      </span>
      <h2>{title}</h2>
      <div className="muted">{children}</div>
    </div>
  );
}
