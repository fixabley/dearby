import type { Metadata, Viewport } from "next";
import { Shell } from "@/widgets/shell/shell";
import "./globals.css";
export const metadata: Metadata = {
  title: { default: "dearby · 탐색", template: "%s · dearby" },
  description: "새로운 활동을 발견하고, 받은 공개 명함을 저장하세요.",
  manifest: "/manifest.webmanifest",
  appleWebApp: { capable: true, title: "Dearby", statusBarStyle: "default" },
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
