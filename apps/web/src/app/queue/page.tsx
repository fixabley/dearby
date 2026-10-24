import { DiscoveryQueue } from "@/components/discovery-queue";
export const metadata = {
  title: "오늘의 맞춤 대기열",
  robots: { index: false, follow: false },
};
export default function Page() {
  return <DiscoveryQueue />;
}
