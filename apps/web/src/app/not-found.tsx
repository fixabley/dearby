import { CenteredMessage } from "@/shared/ui/centered-message";
export default function NotFound() {
  return (
    <CenteredMessage
      icon="info"
      level={1}
      title="페이지를 찾을 수 없어요"
      message="링크를 다시 확인해 주세요."
      primary={{ label: "탐색으로 돌아가기", href: "/" }}
    />
  );
}
