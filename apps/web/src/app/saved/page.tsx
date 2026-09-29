import { SavedCards } from "@/components/saved-cards";
export const metadata = {
  title: "저장한 명함",
  robots: { index: false, follow: false },
};
export default function Page() {
  return <SavedCards />;
}
