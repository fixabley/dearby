import { notFound } from "next/navigation";
import { activityId } from "@/lib/models";
import { ActivityProgress } from "@/components/activity-progress";
export const metadata = {
  title: "진행 단계",
  robots: { index: false, follow: false },
};
export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const id = activityId((await params).id);
  if (id === undefined) notFound();
  return <ActivityProgress key={id} id={id} />;
}
