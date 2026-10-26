import { AccountGate } from "@/features/auth/ui/account-gate";
import { MyCards } from "@/features/my-cards/ui/my-cards";
import { LargeTitle } from "@/shared/ui/nav-title";
export const metadata = {
  title: "명함",
  robots: { index: false, follow: false },
};
/** 내 명함 (contract "웹 화면 — 2단계 C"); `?new=1` opens the editor right away (the profile's `이 내용으로 명함 만들기`). */
export default async function Page({ searchParams }: { searchParams: Promise<{ new?: string | string[] }> }) {
  const { new: openNew } = await searchParams;
  return (
    <>
      <LargeTitle>명함</LargeTitle>
      <AccountGate description="로그인하면 내 명함을 만들고 QR로 건넬 수 있어요.">
        <MyCards openNew={openNew === "1"} />
      </AccountGate>
    </>
  );
}
