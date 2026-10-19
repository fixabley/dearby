import type { Metadata, Viewport } from "next";
import { Shell } from "@/widgets/shell/shell";
import "./globals.css";
export const metadata: Metadata = {
  title: { default: "dearby · 탐색", template: "%s · dearby" },
  description: "새로운 활동을 발견하고, 받은 공개 명함을 저장하세요.",
  manifest: "/manifest.webmanifest",
  appleWebApp: { capable: true, title: "Dearby", statusBarStyle: "default" },
};
// Browser chrome tint = the header surface (`--card`) in each scheme (contract "다크 모드"); the manifest keeps its static colours.
export const viewport: Viewport = {
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#ffffff" },
    { media: "(prefers-color-scheme: dark)", color: "#181f21" },
  ],
};
export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="ko">
      <body>
        <Shell>{children}</Shell>
      </body>
    </html>
  );
}
