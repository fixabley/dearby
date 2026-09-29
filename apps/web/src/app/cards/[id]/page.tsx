import { PublicCard } from "@/components/public-card";
import { MissingCard } from "@/components/states";
import { uuid } from "@/lib/models";
export const metadata = {
  title: "공개 명함 · 이력서",
  robots: { index: false, follow: false },
};
export default async function Page({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return uuid.safeParse(id).success ? (
    <PublicCard key={id} id={id.toLowerCase()} />
  ) : (
    <MissingCard />
  );
}
