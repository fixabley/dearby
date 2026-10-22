import { CenteredMessage } from "@/shared/ui/centered-message";
export function MissingCard() {
  return (
    <CenteredMessage
      icon="card"
      level={1}
      title="명함을 볼 수 없어요"
      message="공개가 취소되었거나 존재하지 않는 명함이에요. 명함 주인에게 새 링크를 요청해 주세요."
      primary={{ label: "저장한 명함 보기", href: "/saved" }}
    />
  );
}
/** 403 from the API (contract "명함 QR 코드"): no valid QR code and no saved copy in this browser. */
export function CardCodeRequired() {
  return (
    <CenteredMessage
      icon="card"
      level={1}
      title="명함을 볼 수 없어요"
      message="명함 주인에게 QR을 다시 보여 달라고 하세요"
      primary={{ label: "저장한 명함 보기", href: "/saved" }}
    />
  );
}
