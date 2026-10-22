import { PublicCard } from "@/components/public-card";
import { MissingCard } from "@/widgets/card/missing-card";
import { activityId, uuid } from "@/lib/models";
import { TOKEN } from "@/lib/guest-upstream";
import type { Metadata } from "next";
import { siteOpenGraph } from "@/lib/preview";
// No personal data in the preview (contract "유니버설 링크·App Links·활동 공유·오픈그래프"): link previews are cached and spread
// without the QR code. Setting `openGraph` here drops the root image file, so the default image is named again.
const preview = {
  title: "Dearby 명함",
  description: "Dearby에서 명함을 확인하고 저장하세요.",
  images: "/opengraph-image",
};
export const metadata: Metadata = {
  title: "공개 명함 · 이력서",
  description: preview.description,
  openGraph: { ...siteOpenGraph, ...preview },
  twitter: { card: "summary_large_image", ...preview },
  robots: { index: false, follow: false },
};
export default async function Page({
  params,
  searchParams,
}: {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ activity?: string | string[]; code?: string | string[] }>;
}) {
  const { id } = await params;
  // `?activity=<n>` from the owner's QR (contract "명함 교환"): which activity the card was exchanged at.
  // `?code=` is the QR code (contract "명함 QR 코드"); same 43-char base64url shape as the guest token.
  const { activity, code } = await searchParams;
  const qrCode = typeof code === "string" && TOKEN.test(code) ? code : undefined;
  const activityRef =
    typeof activity === "string" ? activityId(activity) : undefined;
  return uuid.safeParse(id).success ? (
    <PublicCard key={id} id={id.toLowerCase()} activity={activityRef} code={qrCode} />
  ) : (
    <MissingCard />
  );
}
