import { PublicCard } from "@/components/public-card";
import { MissingCard } from "@/widgets/card/missing-card";
import { activityId, uuid } from "@/lib/models";
export const metadata = {
  title: "공개 명함 · 이력서",
  robots: { index: false, follow: false },
};
export default async function Page({
  params,
  searchParams,
}: {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ activity?: string | string[] }>;
}) {
  const { id } = await params;
  // `?activity=<n>` from the owner's QR (contract "명함 교환"): which activity the card was exchanged at.
  const { activity } = await searchParams;
  const activityRef =
    typeof activity === "string" ? activityId(activity) : undefined;
  return uuid.safeParse(id).success ? (
    <PublicCard key={id} id={id.toLowerCase()} activity={activityRef} />
  ) : (
    <MissingCard />
  );
}
