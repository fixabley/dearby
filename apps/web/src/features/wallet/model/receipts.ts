import type { Receipt } from "@/lib/account-models";
import { type ExchangeContext, expiryLabel } from "@/lib/models";

/**
 * 색인 묶음 (contract "받은 명함 목록 표시"): the name's first letter — 한글 초성 (된소리 → 기본 자음), A–Z, else `#` — in the order
 * ㄱ…ㅎ, A…Z, #; names sorted inside a group.
 */
const CHOSEONG = ["ㄱ", "ㄱ", "ㄴ", "ㄷ", "ㄷ", "ㄹ", "ㅁ", "ㅂ", "ㅂ", "ㅅ", "ㅅ", "ㅇ", "ㅈ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"];
const ORDER = [...new Set(CHOSEONG), ..."ABCDEFGHIJKLMNOPQRSTUVWXYZ", "#"];
function initialOf(name: string): string {
  const first = [...name.trim()][0];
  if (!first) return "#";
  const code = first.codePointAt(0)!;
  if (code >= 0xac00 && code <= 0xd7a3) return CHOSEONG[Math.floor((code - 0xac00) / 588)];
  if (/^[A-Za-z]$/.test(first)) return first.toUpperCase();
  return "#";
}
export function groupReceipts(items: Receipt[], query = ""): { initial: string; items: Receipt[] }[] {
  const needle = query.trim().toLowerCase();
  const matching = needle
    ? items.filter(({ card, context }) => [card.profileName, card.job, card.name, context.programName ?? ""].some((text) => text.toLowerCase().includes(needle)))
    : items;
  const groups = new Map<string, Receipt[]>();
  for (const item of matching) {
    const initial = initialOf(item.card.profileName);
    groups.set(initial, [...(groups.get(initial) ?? []), item]);
  }
  return ORDER.filter((initial) => groups.has(initial)).map((initial) => ({
    initial,
    items: groups.get(initial)!.sort((a, b) => a.card.profileName.localeCompare(b.card.profileName, "ko")),
  }));
}

/** 목록 행 캡션 `프로그램 · 블록 · 트랙 · 방` (contract "교환 세션 맥락" › 화면); empty without session fields. */
export function sessionCaption({ programName, scheduleTitle, trackName, room }: ExchangeContext): string {
  if (!scheduleTitle && !trackName && !room) return "";
  return [programName, scheduleTitle, trackName, room].filter(Boolean).join(" · ");
}
/** 상태 배지: 맞교환 or the recall countdown (`N일 뒤 사라져요`). */
export function receiptBadge(receipt: Receipt): { label: string; reciprocal: boolean } {
  if (receipt.reciprocal) return { label: "맞교환", reciprocal: true };
  return { label: receipt.expiresAt ? expiryLabel(receipt.expiresAt) : "", reciprocal: false };
}
/** `2026.10.28에 받음` in Seoul time. */
function receivedLabel(receivedAt: string): string {
  const time = Date.parse(receivedAt);
  if (Number.isNaN(time)) return "";
  const [year, month, day] = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit", day: "2-digit" })
    .format(new Date(time))
    .split("-");
  return `${year}.${month}.${day}에 받음`;
}
/**
 * 상세 머리 줄 (contract "오늘 내 행사 카드" › 받은 명함의 교환 세션 맥락, iOS #434): with session fields the first line is
 * `프로그램 · 블록 · 트랙 · 방`, the second the session title, the third the received date; without them `<프로그램> <활동>에서 받음 · 날짜`.
 */
export function receiptHeadLines(receipt: Receipt): string[] {
  const { context } = receipt;
  const caption = sessionCaption(context);
  const received = receivedLabel(receipt.receivedAt);
  if (caption) return [caption, context.sessionTitle ?? "", received].filter(Boolean);
  const place = [context.programName, context.activityName].filter(Boolean).join(" ");
  return [place ? `${place}에서 받음 · ${received.replace("에 받음", "")}` : received];
}
