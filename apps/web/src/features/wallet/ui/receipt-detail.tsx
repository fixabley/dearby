"use client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import type { MyCard, Receipt } from "@/lib/account-models";
import { cardPhotoSrc } from "@/lib/models";
import { errorMessage, RequestError } from "@/lib/client";
import { noteExchanges } from "@/shared/lib/analytics";
import { useAuth } from "@/features/auth/model/auth-context";
import { AccountGate } from "@/features/auth/ui/account-gate";
import { Badge } from "@/shared/ui/badge";
import { Button } from "@/shared/ui/button";
import { CenteredMessage } from "@/shared/ui/centered-message";
import { Icon } from "@/shared/ui/icon";
import { Sheet, SheetClose, SheetContent, SheetDescription, SheetTitle } from "@/shared/ui/sheet";
import { SkeletonGroup } from "@/shared/ui/skeleton";
import { Failure } from "@/shared/ui/states";
import { PublicCardBody, PublicCardBodySkeleton } from "@/widgets/card/public-card-body";
import { BlockCard, ReportCard } from "@/widgets/card/report-dialog";
import { blockOwner, myCards, reciprocate, wallet } from "../api/wallet-client";
import { receiptHeadLines } from "../model/receipts";

/** `/wallet/:id` (contract "웹 화면 — 2단계 C"): one received card. */
export function ReceiptPage({ id }: { id: string }) {
  return (
    <AccountGate
      description="로그인하면 받은 명함을 볼 수 있어요."
      skeleton={
        <SkeletonGroup className="mx-auto max-w-[480px] pt-3">
          <PublicCardBodySkeleton />
        </SkeletonGroup>
      }
    >
      <ReceiptDetail id={id} />
    </AccountGate>
  );
}

/**
 * 받은 명함 상세 (contract "명함 교환" › 화면, "교환 세션 맥락" › 화면, "명함 신고·차단"): above the card the `프로그램 · 블록 · 트랙 · 방`
 * line, the session title and the received date (or `<프로그램> <활동>에서 받음 · 날짜`); the card face from the Receipt itself
 * (no second fetch — the wallet is the view right); below it `내 명함도 건네기` (one card → straight away, several → a picker sheet),
 * replaced by the `맞교환했어요` badge once reciprocal, then 신고하기 and 삭제하고 차단하기.
 */
function ReceiptDetail({ id }: { id: string }) {
  const router = useRouter();
  const { signedOutLocally } = useAuth();
  const [receipt, setReceipt] = useState<Receipt | null>();
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const [cards, setCards] = useState<MyCard[]>();
  const [picking, setPicking] = useState(false);
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState("");
  const [notice, setNotice] = useState("");
  const expired = useCallback(
    (caught: unknown) => {
      if (caught instanceof RequestError && caught.status === 401) {
        signedOutLocally("로그인이 만료됐어요. 다시 로그인해 주세요.");
        return true;
      }
      return false;
    },
    [signedOutLocally],
  );
  useEffect(() => {
    let cancelled = false;
    wallet().then(
      (list) => {
        if (cancelled) return;
        setReceipt(list.find((item) => item.id.toLowerCase() === id) ?? null);
      },
      (caught) => {
        if (cancelled) return;
        if (!expired(caught)) setError(errorMessage(caught));
      },
    );
    return () => {
      cancelled = true;
    };
  }, [id, attempt, expired]);

  /** Sends my card `myCardId` to the owner; afterwards the wallet is read again so the badge follows the server's judgment. */
  async function give(myCardId: string) {
    if (!receipt) return;
    setBusy(true);
    setActionError("");
    setPicking(false);
    try {
      await reciprocate(receipt.card.id, myCardId);
      const list = await wallet();
      noteExchanges(list.map((item) => ({ id: item.id, reciprocal: item.reciprocal, activityId: item.context.activityId })), "reciprocate");
      setReceipt(list.find((item) => item.id === receipt.id) ?? receipt);
      setNotice("내 명함을 건넸어요.");
    } catch (caught) {
      if (expired(caught)) return;
      if (caught instanceof RequestError && caught.status === 403) setActionError("이 명함을 받은 기록이 없어 건넬 수 없어요.");
      else if (caught instanceof RequestError && caught.status === 404) setActionError("고른 명함을 찾을 수 없어요. 내 명함을 다시 확인해 주세요.");
      else setActionError(errorMessage(caught));
    } finally {
      setBusy(false);
    }
  }
  /** 내 명함도 건네기: load my cards once, then give straight away or let the person pick. */
  async function startGive() {
    setActionError("");
    setBusy(true);
    try {
      const mine = cards ?? (await myCards());
      setCards(mine);
      if (mine.length === 0) setActionError("먼저 내 명함을 만들어 주세요.");
      else if (mine.length === 1) await give(mine[0].id);
      else setPicking(true);
    } catch (caught) {
      if (!expired(caught)) setActionError(errorMessage(caught));
    } finally {
      setBusy(false);
    }
  }
  if (error)
    return (
      <Failure
        message={error}
        retry={() => {
          setError("");
          setAttempt((value) => value + 1);
        }}
      />
    );
  if (receipt === undefined)
    return (
      <SkeletonGroup className="mx-auto max-w-[480px] pt-3">
        <PublicCardBodySkeleton />
      </SkeletonGroup>
    );
  if (receipt === null)
    return (
      <CenteredMessage
        icon="card"
        level={1}
        title="받은 명함을 찾을 수 없어요"
        message="회수됐거나 주인이 지운 명함이에요."
        primary={{ label: "받은 명함 보기", href: "/wallet" }}
      />
    );
  const { card } = receipt;
  const lines = receiptHeadLines(receipt);
  return (
    <article className="mx-auto max-w-[480px] pt-3">
      <h1 className="sr-only">{card.profileName || "받은 명함"}</h1>
      <div className="mb-3 flex flex-col items-center gap-0.5 text-center text-footnote break-keep text-secondary-label">
        {lines.map((line) => (
          <p key={line}>{line}</p>
        ))}
      </div>
      <PublicCardBody card={{ ...card, photo: cardPhotoSrc(card.photoUrl) }} />
      <div className="mt-4 flex flex-col items-center gap-2">
        {notice && (
          <p role="status" className="text-footnote text-teal">
            {notice}
          </p>
        )}
        {actionError && (
          <p role="alert" className="text-center text-footnote text-danger">
            {actionError}{" "}
            {actionError.startsWith("먼저 내 명함") && (
              <Link href="/cards/mine" className="underline">
                내 명함 만들기
              </Link>
            )}
          </p>
        )}
        {receipt.reciprocal ? (
          <Badge variant="filled" className="px-3 py-1 text-subheadline">
            <Icon name="check-circle" size="inline" />
            맞교환했어요
          </Badge>
        ) : (
          <Button size="lg" disabled={busy} onClick={startGive}>
            <Icon name="card" size="inline" />
            {busy ? "건네는 중…" : "내 명함도 건네기"}
          </Button>
        )}
        {!receipt.reciprocal && receipt.expiresAt && (
          <p className="text-center text-footnote break-keep text-secondary-label">맞교환하지 않으면 받은 날부터 30일 뒤 이 명함이 사라져요.</p>
        )}
        <div className="flex justify-center gap-2">
          <ReportCard id={card.id} />
          <BlockCard id={card.id} name={card.profileName || "이 사람"} blocked={() => router.push("/wallet")} perform={() => blockOwner(card.id).then(() => undefined)} />
        </div>
      </div>
      {/* 내 명함 고르기: several cards → the person picks which one to give (contract "명함 교환" › 받는 사람(앱)). */}
      <Sheet open={picking} onOpenChange={(open) => !busy && setPicking(open)}>
        <SheetContent>
          <div className="grid grid-cols-[1fr_auto_1fr] items-center">
            <span />
            <SheetTitle className="text-center">건넬 명함 고르기</SheetTitle>
            <SheetClose className="-mr-2 inline-flex min-h-11 items-center justify-self-end px-2 text-body text-teal">닫기</SheetClose>
          </div>
          <SheetDescription className="mt-1 text-center">{card.profileName || "상대"}님의 받은 명함에 들어갈 내 명함이에요.</SheetDescription>
          <ul className="mt-3 divide-y divide-separator">
            {(cards ?? []).map((mine) => (
              <li key={mine.id}>
                <button type="button" className="flex min-h-11 w-full items-center gap-3 py-2 text-left active:opacity-70" disabled={busy} onClick={() => give(mine.id)}>
                  <Icon name="card" tone="teal" />
                  <span className="flex min-w-0 flex-1 flex-col">
                    <span className="text-body font-semibold">{mine.name}</span>
                    <span className="truncate text-footnote text-secondary-label">{[mine.profileName, mine.job].filter(Boolean).join(" · ")}</span>
                  </span>
                  <Icon name="chevron-right" size="inline" tone="secondary" />
                </button>
              </li>
            ))}
          </ul>
        </SheetContent>
      </Sheet>
    </article>
  );
}
