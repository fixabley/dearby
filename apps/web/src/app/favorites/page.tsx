import { Favorites } from "@/components/favorites";
export const metadata = {
  title: "즐겨찾기",
  robots: { index: false, follow: false },
};
export default function Page() {
  return <Favorites />;
}
