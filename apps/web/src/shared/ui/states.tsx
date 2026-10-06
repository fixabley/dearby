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
      <span className="state-icon">
        <Icon name="alert" size={32} />
      </span>
      <h2>다시 확인해 주세요</h2>
      <p>{message}</p>
      <button className="button secondary" onClick={retry}>
        다시 시도
      </button>
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
