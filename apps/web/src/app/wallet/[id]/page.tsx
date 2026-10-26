import { ReceiptPage } from "@/features/wallet/ui/receipt-detail";
import { CenteredMessage } from "@/shared/ui/centered-message";
import { uuid } from "@/lib/models";
export const metadata = {
  title: "받은 명함",
  robots: { index: false, follow: false },
};
/** `/wallet/:id` — the receipt id (a UUID); anything else never reaches the API. */
export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return uuid.safeParse(id).success ? (
    <ReceiptPage key={id} id={id.toLowerCase()} />
  ) : (
    <CenteredMessage icon="card" level={1} title="받은 명함을 찾을 수 없어요" primary={{ label: "받은 명함 보기", href: "/wallet" }} />
  );
}
