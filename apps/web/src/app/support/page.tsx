import type { Metadata } from "next";
import Link from "next/link";
import { LargeTitle } from "@/shared/ui/nav-title";
export const metadata: Metadata = {
  title: "지원",
  description: "Dearby 문의, 명함 신고, 계정 삭제 방법을 안내합니다.",
};
// 계약 native-v1.md "명함 신고·차단" › 연락처 공개(2026-10-22). App Store 지원 URL과 Play 계정 삭제 URL(#delete-account)이 이 페이지다.
export default function SupportPage() {
  return (
    <article className="legal pt-0 pb-6">
      <LargeTitle>지원</LargeTitle>
      <h2>운영자와 연락처</h2>
      <p>
        서비스 운영자: <strong>팀 WID</strong> · 이메일: <a href="mailto:fixabley@naver.com">fixabley@naver.com</a> · 전화:{" "}
        <a href="tel:010-7503-5692">010-7503-5692</a>. 이용 문의와 개인정보 요청은 이메일로 보내 주세요.
      </p>
      <h2>명함 신고하기</h2>
      <ul>
        <li>앱: 받은 명함 상세의 더 보기 메뉴에서 신고하기를 누르고 사유를 고릅니다.</li>
        <li>웹: 명함 아래 신고하기를 누르고 사유를 고릅니다.</li>
        <li>신고는 24시간 안에 확인하고, 필요하면 명함을 내립니다.</li>
        <li>앞으로 그 사람의 명함을 받지 않으려면 받은 명함 상세에서 삭제하고 차단하기를 누릅니다.</li>
      </ul>
      <h2 id="delete-account">계정 삭제</h2>
      <ul>
        <li>앱: 설정 → 계정 삭제를 누릅니다.</li>
        <li>앱을 쓸 수 없다면 가입한 로그인 수단(패스키·Apple·Google)을 적어 <a href="mailto:fixabley@naver.com?subject=Dearby%20계정%20삭제%20요청">fixabley@naver.com</a>로 삭제를 요청해 주세요. 본인 확인 뒤 처리합니다.</li>
        <li>
          계정을 삭제하면 계정·프로필·명함·받은 명함·인증자료가 즉시 모두 삭제되고, Apple·Google 연결 정보도 폐기됩니다(
          <Link href="/privacy">개인정보 처리방침</Link> 6항).
        </li>
        <li>브라우저에 저장한 명함은 저장한 명함 화면에서 지우거나 쿠키를 삭제하면 사라집니다.</li>
      </ul>
    </article>
  );
}
