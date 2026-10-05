import type { Metadata } from "next";
import { Shell } from "@/widgets/shell/shell";
import "./globals.css";
export const metadata: Metadata = {
  title: { default: "dearby · 탐색", template: "%s · dearby" },
  description: "새로운 활동을 발견하고, 공유받은 공개 명함을 저장하세요.",
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
