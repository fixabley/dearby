import Link from "next/link";
export default function NotFound() {
  return (
    <div className="state">
      <h1>페이지를 찾을 수 없어요</h1>
      <p>링크를 다시 확인해 주세요.</p>
      <Link className="button" href="/">
        탐색으로 돌아가기
      </Link>
    </div>
  );
}
