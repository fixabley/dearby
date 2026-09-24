import type { Metadata } from "next";
import { Suspense } from "react";
import { SavedProvider } from "@/features/saved/provider";
import { Shell } from "@/components/shell";
import "./globals.css";
export const metadata: Metadata = {
  title: "Dearby · 나의 다음 경험",
  description:
    "공식 출처를 확인한 IT 컨퍼런스 · 연합동아리. 분야와 경험으로 탐색하고 프로그램과 조직을 스크랩하세요.",
};
export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="ko">
      <body>
        <SavedProvider>
          <Suspense fallback={<p>Dearby를 불러오는 중입니다.</p>}>
            <Shell>{children}</Shell>
          </Suspense>
        </SavedProvider>
      </body>
    </html>
  );
}
