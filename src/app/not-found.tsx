import Link from "next/link";
export default function NotFound() {
  return (
    <div className="empty-state">
      <h1>프로그램을 찾을 수 없어요</h1>
      <p>주소를 확인하거나 전체 프로그램을 둘러보세요.</p>
      <Link href="/" className="primary">
        전체 탐색
      </Link>
    </div>
  );
}
