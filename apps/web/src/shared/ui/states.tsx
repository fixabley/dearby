"use client";
import { CenteredMessage } from "./centered-message";
export function Loading() {
  return (
    <div className="state" role="status">
      <span className="loader" />
      불러오는 중이에요.
    </div>
  );
}
/** Request failure preset of CenteredMessage: danger circle, fixed title, the error text and a retry CTA. */
export function Failure({ message, retry }: { message: string; retry: () => void }) {
  return (
    <CenteredMessage
      icon="warning"
      tone="danger"
      title="다시 확인해 주세요"
      message={message}
      primary={{ label: "다시 시도", onClick: retry }}
      role="alert"
    />
  );
}
