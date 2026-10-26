import type { Metadata, Viewport } from "next";
import { Shell } from "@/widgets/shell/shell";
import { AuthProvider } from "@/features/auth/model/auth-context";
import { UnreadProvider } from "@/features/notifications/model/unread";
import { TesterInvite } from "@/features/tester-invite/ui/tester-invite";
import { GoogleAnalytics } from "@/shared/ui/google-analytics";
import { VercelAnalytics } from "@/shared/ui/vercel-analytics";
import { siteOpenGraph, WEB_ORIGIN } from "@/lib/preview";
import "./globals.css";
export const metadata: Metadata = {
  title: { default: "dearby · 탐색", template: "%s · dearby" },
  description: "새로운 활동을 발견하고, 받은 공개 명함을 저장하세요.",
  manifest: "/manifest.webmanifest",
  appleWebApp: { capable: true, title: "Dearby 명함함", statusBarStyle: "default" },
  // 오픈그래프 공통 (contract "유니버설 링크·App Links·활동 공유·오픈그래프"); the image is `opengraph-image.tsx`.
  metadataBase: WEB_ORIGIN,
  openGraph: siteOpenGraph,
  twitter: { card: "summary_large_image" },
};
// Browser chrome tint = the grouped page background under the translucent bar (`--background`); the manifest keeps its static
// colours. `viewport-fit=cover` lets the bar and the bottom CTAs pad for the safe area.
export const viewport: Viewport = {
  viewportFit: "cover",
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#f2f2f7" },
    { media: "(prefers-color-scheme: dark)", color: "#000000" },
  ],
};
// 사용 통계 only on the production deployment (preview builds also run with NODE_ENV=production); GA also needs a measurement id.
const production = process.env.VERCEL_ENV === "production";
const gaId = production ? process.env.NEXT_PUBLIC_GA_MEASUREMENT_ID?.trim() : undefined;
export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="ko">
      <body>
        {/* The sign-in sheet's last line: 비공개 테스트 invite (contract "명함 교환" › 받는 사람(웹)), composed here so auth stays independent. */}
        <AuthProvider signInFooter={<TesterInvite variant="line" />}>
          <UnreadProvider>
            <Shell>{children}</Shell>
          </UnreadProvider>
        </AuthProvider>
        {gaId && <GoogleAnalytics id={gaId} />}
        {production && <VercelAnalytics />}
      </body>
    </html>
  );
}
