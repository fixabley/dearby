import type { Metadata } from "next";
import { Suspense } from "react";
import { SavedProvider } from "@/features/saved/provider";
import { Shell } from "@/components/shell";
import "./globals.css";
export const metadata: Metadata = {
  title: "Dearby · 나의 다음 경험",
  description:
    "원하는 직무와 경험으로 찾는 교외 활동. 가상 프로그램을 사용하는 웹 프로토타입.",
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
