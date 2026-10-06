import { SharePage } from "@/components/share-page";
import { MissingCard } from "@/widgets/card/missing-card";
import { uuid } from "@/lib/models";
export const metadata = {
  title: "공유받은 명함",
  robots: { index: false, follow: false },
};
export default async function Page({
  params,
}: {
  params: Promise<{ shareId: string }>;
}) {
  const { shareId } = await params;
  return uuid.safeParse(shareId).success ? (
    <SharePage key={shareId} id={shareId.toLowerCase()} />
  ) : (
    <MissingCard />
  );
}
