"use client";
import { CenteredMessage } from "./centered-message";

/** Request failure preset of CenteredMessage: danger circle, fixed title, the error text and a retry CTA. */
export function Failure({ message, retry, compact }: { message: string; retry: () => void; compact?: boolean }) {
  return (
    <CenteredMessage
      icon="warning"
      tone="danger"
      title="다시 확인해 주세요"
      message={message}
      primary={{ label: "다시 시도", onClick: retry }}
      role="alert"
      compact={compact}
    />
  );
}
