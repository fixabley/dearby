// 받은 명함 (contract native-v1.md "명함 교환", "명함 신고·차단") through the account branch of `/api/*`.
import { account, accountSend, accountWithStatus } from "@/lib/client";
import type { MyCard, Receipt } from "@/lib/account-models";

export const wallet = () => account<{ items: Receipt[] }>("/wallet").then((result) => result.items);
/** Saves a received card; `status` 201 = newly received (`card_received`), 200 = already in the wallet. */
export const saveToWallet = (cardId: string, body: { activityId?: number; code?: string }) =>
  accountWithStatus<Receipt>(`/wallet/cards/${cardId}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json", "X-Dearby-Request": "1" },
    body: JSON.stringify(body),
  });
export const reciprocate = (cardId: string, myCardId: string) => accountSend<{ reciprocal: boolean }>(`/wallet/cards/${cardId}/reciprocate`, "POST", { myCardId });
export const blockOwner = (cardId: string) => accountSend<{ id: string; name: string; blockedAt: string }>("/wallet/blocks", "POST", { cardId });
/** My cards, to pick one for 내 명함도 건네기. */
export const myCards = () => account<{ items: MyCard[] }>("/cards").then((result) => result.items);
