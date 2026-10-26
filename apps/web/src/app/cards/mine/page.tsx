import { AccountGate } from "@/features/auth/ui/account-gate";
import { MyCards } from "@/features/my-cards/ui/my-cards";
import { LargeTitle } from "@/shared/ui/nav-title";
import { activityId } from "@/lib/models";
export const metadata = {
  title: "명함",
  robots: { index: false, follow: false },
};
/**
 * 내 명함 (contract "웹 화면 — 2단계 C"); `?new=1` opens the editor right away (the profile's `이 내용으로 명함 만들기`) and `?qr=<activityId>`
 * opens the QR of the selected card with that activity chip (the 오늘 내 행사 card's `명함 QR`, contract "오늘 내 행사 카드").
 */
export default async function Page({ searchParams }: { searchParams: Promise<{ new?: string | string[]; qr?: string | string[] }> }) {
  const { new: openNew, qr } = await searchParams;
  return (
    <>
      <LargeTitle>명함</LargeTitle>
      <AccountGate description="로그인하면 내 명함을 만들고 QR로 건넬 수 있어요.">
        <MyCards openNew={openNew === "1"} openQr={typeof qr === "string" ? activityId(qr) : undefined} />
      </AccountGate>
    </>
  );
}
