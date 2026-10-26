"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import type { Receipt } from "@/lib/account-models";
import { cardPhotoSrc } from "@/lib/models";
import { errorMessage, RequestError } from "@/lib/client";
import { noteExchanges } from "@/shared/lib/analytics";
import { useAuth } from "@/features/auth/model/auth-context";
import { AccountGate } from "@/features/auth/ui/account-gate";
import { Badge } from "@/shared/ui/badge";
import { Card } from "@/shared/ui/card";
import { CenteredMessage } from "@/shared/ui/centered-message";
import { Icon } from "@/shared/ui/icon";
import { Input } from "@/shared/ui/input";
import { LargeTitle } from "@/shared/ui/nav-title";
import { Skeleton, SkeletonGroup } from "@/shared/ui/skeleton";
import { Failure } from "@/shared/ui/states";
import { wallet } from "../api/wallet-client";
import { groupReceipts, receiptBadge, sessionCaption } from "../model/receipts";

/** `/wallet` (contract "웹 화면 — 2단계 C"): the signed-in person's received cards. */
export function WalletPage() {
  return (
    <>
      <LargeTitle>받은 명함</LargeTitle>
      <AccountGate description="로그인하면 받은 명함이 계정에 모여요. 맞교환한 명함은 사라지지 않아요." skeleton={<ListSkeleton />}>
        <WalletList />
      </AccountGate>
    </>
  );
}

/**
 * 받은 명함 목록 (contract "받은 명함 목록 표시", "명함 교환" › 화면, "교환 세션 맥락" › 화면): 색인 묶음 (초성·A–Z·#) with a search
 * field; each row = photo or initial, name, job, card name, the program chip, the `프로그램 · 블록 · 트랙 · 방` caption and one badge —
 * `맞교환` or `N일 뒤 사라져요`. Rows lead to `/wallet/:receiptId`. The index rail is left out (the web list scrolls short).
 */
function WalletList() {
  const { signedOutLocally } = useAuth();
  const [items, setItems] = useState<Receipt[]>();
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const [query, setQuery] = useState("");
  useEffect(() => {
    let cancelled = false;
    wallet().then(
      (list) => {
        if (cancelled) return;
        setItems(list);
        noteExchanges(list.map((receipt) => ({ id: receipt.id, reciprocal: receipt.reciprocal, activityId: receipt.context.activityId })), "wallet");
      },
      (caught) => {
        if (cancelled) return;
        if (caught instanceof RequestError && caught.status === 401) signedOutLocally("로그인이 만료됐어요. 다시 로그인해 주세요.");
        else setError(errorMessage(caught));
      },
    );
    return () => {
      cancelled = true;
    };
  }, [attempt, signedOutLocally]);
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
  if (!items) return <ListSkeleton />;
  if (!items.length)
    return (
      <CenteredMessage
        icon="inbox"
        title="아직 받은 명함이 없어요"
        message="상대의 QR을 카메라로 읽으면 여기에 저장돼요. 맞교환한 명함은 사라지지 않아요."
        primary={{ label: "내 명함 보기", href: "/cards/mine" }}
      />
    );
  const groups = groupReceipts(items, query);
  return (
    <>
      <label className="mt-3 flex items-center gap-2 rounded-control bg-fill px-2">
        <Icon name="search" size="inline" tone="secondary" />
        <span className="sr-only">받은 명함 검색</span>
        <Input type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="이름·직무·프로그램" className="bg-transparent px-0" />
      </label>
      <p className="mt-1.5 px-4 text-footnote text-secondary-label">명함 {items.length}개 · 맞교환이 아닌 명함은 받은 날부터 30일 뒤 사라져요.</p>
      {!groups.length && <p className="px-4 py-8 text-center text-body text-secondary-label">검색 결과가 없어요</p>}
      {groups.map((group) => (
        <section key={group.initial} className="mt-4" aria-labelledby={`wallet-${group.initial}`}>
          <h2 id={`wallet-${group.initial}`} className="mb-1 px-4 text-footnote font-semibold text-secondary-label">
            {group.initial}
          </h2>
          <Card>
            <ul className="divide-y divide-separator">
              {group.items.map((receipt) => (
                <ReceiptRow key={receipt.id} receipt={receipt} />
              ))}
            </ul>
          </Card>
        </section>
      ))}
    </>
  );
}

function ReceiptRow({ receipt }: { receipt: Receipt }) {
  const { card, context } = receipt;
  const badge = receiptBadge(receipt);
  const caption = sessionCaption(context);
  const photo = cardPhotoSrc(card.photoUrl);
  return (
    <li>
      <Link
        href={`/wallet/${receipt.id}`}
        className="flex min-h-11 items-center gap-3 px-4 py-3 active:opacity-70"
        aria-label={[card.profileName || "이름 미등록", card.job, context.programName, caption, badge.label ? (badge.reciprocal ? "맞교환했어요" : badge.label) : ""]
          .filter(Boolean)
          .join(", ")}
      >
        {photo ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={photo} alt="" className="size-10 shrink-0 rounded-full object-cover" />
        ) : (
          <span aria-hidden="true" className="grid size-10 shrink-0 place-items-center rounded-full bg-mint text-headline font-bold text-teal">
            {Array.from(card.profileName)[0] || <Icon name="card" />}
          </span>
        )}
        <span className="flex min-w-0 flex-1 flex-col gap-0.5">
          <span className="text-headline font-semibold">{card.profileName || "이름 미등록"}</span>
          {card.job && <span className="text-subheadline">{card.job}</span>}
          <span className="truncate text-footnote text-secondary-label">{card.name}</span>
          {caption && <span className="text-footnote break-keep text-secondary-label">{caption}</span>}
          {(context.programName || badge.label) && (
            <span className="mt-1 flex flex-wrap gap-1.5">
              {context.programName && <Badge>{context.programName}</Badge>}
              {badge.label && <Badge variant={badge.reciprocal ? "filled" : "tinted"}>{badge.label}</Badge>}
            </span>
          )}
        </span>
        <Icon name="chevron-right" size="inline" tone="secondary" />
      </Link>
    </li>
  );
}

function ListSkeleton() {
  return (
    <SkeletonGroup className="mt-5">
      <Card className="divide-y divide-separator">
        {[0, 1, 2].map((key) => (
          <div key={key} className="flex items-center gap-3 px-4 py-3">
            <Skeleton className="size-10 shrink-0 rounded-full" />
            <div className="flex flex-1 flex-col gap-1.5">
              <Skeleton className="h-4 w-1/3" />
              <Skeleton className="h-3.5 w-1/2" />
            </div>
          </div>
        ))}
      </Card>
    </SkeletonGroup>
  );
}
