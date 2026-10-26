import { MyActivities } from "@/components/my-activities";
export const metadata = {
  title: "내 활동",
  robots: { index: false, follow: false },
};
export default function Page() {
  return <MyActivities />;
}
