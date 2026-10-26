"use client";
import { createContext, useCallback, useContext, useEffect, useState } from "react";
import { useAuth } from "@/features/auth/model/auth-context";
import { unreadCount } from "../api/notifications-client";

type Unread = { count: number; refresh: () => void; setCount: (count: number) => void };
const UnreadContext = createContext<Unread>({ count: 0, refresh: () => {}, setCount: () => {} });

/**
 * 읽지 않은 알림 수 (contract "알림 목록" › 종 배지): asked once when the signed-in, consented session is known and again when a page
 * marks notifications read (`refresh`). No polling, no web push (contract "웹 화면 — 2단계 C"). Signed out it is 0.
 */
export function UnreadProvider({ children }: { children: React.ReactNode }) {
  const auth = useAuth();
  const active = auth.signedIn && !auth.consentRequired;
  const [count, setCount] = useState(0);
  const [attempt, setAttempt] = useState(0);
  const refresh = useCallback(() => setAttempt((value) => value + 1), []);
  useEffect(() => {
    if (!active) return;
    let cancelled = false;
    unreadCount().then(
      (value) => {
        if (!cancelled) setCount(value);
      },
      () => {},
    );
    return () => {
      cancelled = true;
    };
  }, [active, attempt]);
  // Signed out (or before consent) the badge is 0 whatever was counted before.
  return <UnreadContext value={{ count: active ? count : 0, refresh, setCount }}>{children}</UnreadContext>;
}

export function useUnread(): Unread {
  return useContext(UnreadContext);
}
