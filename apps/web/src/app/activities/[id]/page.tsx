import { notFound } from "next/navigation";
import { activityId } from "@/lib/models";
import { ActivityDetail } from "@/components/activity-detail";
export const metadata = { title: "활동 상세" };
export default async function Page({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const id = activityId((await params).id);
  if (id === undefined) notFound();
  return <ActivityDetail key={id} id={id} />;
}
