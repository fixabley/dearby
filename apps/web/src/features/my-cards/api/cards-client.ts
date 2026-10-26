// 내 명함 (contract native-v1.md "명함·지갑", "명함 고치기는 같은 명함을 수정", "명함 QR 코드") through the account branch of `/api/*`.
import { account, accountSend } from "@/lib/client";
import type { Activity } from "@/lib/models";
import type { CardPublish, MyCard, Profile, QrCode, UserActivities } from "@/lib/account-models";

export const listCards = () => account<{ items: MyCard[] }>("/cards").then((result) => result.items);
export const publishCard = (body: CardPublish) => accountSend<MyCard>("/cards", "POST", body);
export const updateCard = (id: string, body: CardPublish) => accountSend<MyCard>(`/cards/${id}`, "PUT", body);
export const deleteCard = (id: string) => accountSend<void>(`/cards/${id}`, "DELETE");
/** QR 화면을 열 때 발급 (5분, 여럿이 쓸 수 있다); 닫기·새로고침은 2분 뒤 만료. */
export const issueQrCode = (cardId: string) => accountSend<QrCode>(`/cards/${cardId}/qr-codes`, "POST");
export const closeQrCode = (cardId: string, codeId: string) => accountSend<void>(`/cards/${cardId}/qr-codes/${codeId}`, "DELETE");
/** 명함 만들기가 고르는 연락처·이력·사진·소개 링크의 출처. */
export const myProfile = () => account<Profile>("/profile");
/** 내 활동(QR 활동 칩의 후보). */
export const myActivities = () => account<UserActivities>("/me/activities").then((result) => result.items);
export const activityById = (id: number) => account<Activity>(`/activities/${id}`);
