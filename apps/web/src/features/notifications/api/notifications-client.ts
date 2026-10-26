// 알림 목록 (contract native-v1.md "알림 목록(발견 우상단 종)") through the account branch of `/api/*`.
import { account, accountSend } from "@/lib/client";
import type { NotificationPage } from "@/lib/account-models";

export const notificationPage = (cursor?: number) => account<NotificationPage>(`/me/notifications${cursor === undefined ? "" : `?cursor=${cursor}`}`);
export const unreadCount = () => account<{ count: number }>("/me/notifications/unread-count").then((result) => result.count);
export const markRead = (id: number) => accountSend<void>(`/me/notifications/${id}/read`, "POST");
export const markAllRead = () => accountSend<void>("/me/notifications/read-all", "POST");
