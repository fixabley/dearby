import Link from "next/link";
import { Icon } from "@/shared/ui/icon";
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
