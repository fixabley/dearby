"use client";
import { CenteredMessage } from "@/shared/ui/centered-message";
import { Card } from "@/shared/ui/card";
import { Skeleton, SkeletonGroup } from "@/shared/ui/skeleton";
import { useAuth } from "../model/auth-context";

/**
 * The common front of every account page (contract "웹 화면 — 2단계 C", same rules as `/settings`): while the session is unknown a
 * skeleton; signed out, a 로그인 CTA that opens the sheet; signed in without the current consent, the 동의하기 notice; otherwise the
 * page. `skeleton` lets a page show its own content shape while loading.
 */
export function AccountGate({ children, skeleton, description }: { children: React.ReactNode; skeleton?: React.ReactNode; description: string }) {
  const auth = useAuth();
  if (auth.status === "loading")
    return (
      skeleton ?? (
        <SkeletonGroup className="mt-5">
          <Card className="divide-y divide-separator">
            {[0, 1, 2].map((key) => (
              <div key={key} className="flex min-h-11 items-center px-4">
                <Skeleton className="h-4 w-1/3" />
              </div>
            ))}
          </Card>
        </SkeletonGroup>
      )
    );
  if (!auth.signedIn)
    return <CenteredMessage icon="person" title="로그인이 필요해요" message={description} primary={{ label: "로그인", onClick: auth.openSignIn }} />;
  if (auth.consentRequired)
    return (
      <CenteredMessage
        icon="info"
        title="동의가 필요해요"
        message="동의하기 전에는 계정 기능을 쓸 수 없어요. 동의하지 않으면 로그인은 유지되고 다음에 다시 물어요."
        primary={{ label: "동의하기", onClick: auth.openConsent }}
      />
    );
  return <>{children}</>;
}
