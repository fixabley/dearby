import { AccountGate } from "@/features/auth/ui/account-gate";
import { ProfilePage } from "@/features/profile/ui/profile-page";
import { LargeTitle } from "@/shared/ui/nav-title";
export const metadata = {
  title: "프로필",
  robots: { index: false, follow: false },
};
export default function Page() {
  return (
    <>
      <LargeTitle>프로필</LargeTitle>
      <AccountGate description="로그인하면 프로필을 보고 고칠 수 있어요.">
        <ProfilePage />
      </AccountGate>
    </>
  );
}
