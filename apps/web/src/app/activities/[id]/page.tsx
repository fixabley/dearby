import { notFound } from "next/navigation";
import { uuid } from "@/lib/models";
import { ActivityDetail } from "@/components/activity-detail";
export const metadata = { title: "활동 상세" };
export default async function Page({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  if (!uuid.safeParse(id).success) notFound();
  return <ActivityDetail id={id.toLowerCase()} />;
}
