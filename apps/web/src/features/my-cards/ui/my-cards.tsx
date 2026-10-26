"use client";
import { useCallback, useEffect, useState } from "react";
import { RequestError, errorMessage } from "@/lib/client";
import { cardPhotoSrc } from "@/lib/models";
import type { MyCard } from "@/lib/account-models";
import { useAuth } from "@/features/auth/model/auth-context";
import { cn } from "@/shared/lib/utils";
import { Button } from "@/shared/ui/button";
import { CenteredMessage } from "@/shared/ui/centered-message";
import { Dialog, DialogActions, DialogClose, DialogContent, DialogDescription, DialogTitle } from "@/shared/ui/dialog";
import { Icon } from "@/shared/ui/icon";
import { Skeleton, SkeletonGroup } from "@/shared/ui/skeleton";
import { Failure } from "@/shared/ui/states";
import { PublicCardBody, PublicCardBodySkeleton } from "@/widgets/card/public-card-body";
import { deleteCard, listCards } from "../api/cards-client";
import { CardEditor } from "./card-editor";
import { QrScreen } from "./qr-screen";

/**
 * 내 명함 (contract "명함 표현" › 내 명함 탭, "명함 교환" › 주인): a two-column grid — `+ 새 명함` first, then one tile per card — the
 * selected card's face under it, and `QR 보이기` · `이 명함 고치기` · `이 명함 지우기`. The selection lives in memory only. `openNew`
 * (from `?new=1`) opens the editor at once.
 */
export function MyCards({ openNew = false, openQr }: { openNew?: boolean; /** `?qr=<activityId>` (오늘 내 행사 카드의 `명함 QR`): open the QR of the selected card with that activity chip. */ openQr?: number }) {
  const { signedOutLocally } = useAuth();
  const [cards, setCards] = useState<MyCard[]>();
  const [selectedId, setSelectedId] = useState<string>();
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const [editor, setEditor] = useState<{ card?: MyCard } | undefined>(openNew ? {} : undefined);
  const [qr, setQr] = useState(openQr !== undefined);
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState("");
  const fail = useCallback(
    (caught: unknown, message: string) => {
      if (caught instanceof RequestError && caught.status === 401) signedOutLocally("로그인이 만료됐어요. 다시 로그인해 주세요.");
      else setActionError(caught instanceof RequestError && caught.detail ? caught.detail : message);
    },
    [signedOutLocally],
  );
  useEffect(() => {
    let cancelled = false;
    listCards().then(
      (items) => {
        if (cancelled) return;
        setCards(items);
        // 가장 새 명함(만든 순 목록의 마지막)을 고른다.
        setSelectedId((current) => (current && items.some((card) => card.id === current) ? current : items.at(-1)?.id));
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
  const selected = cards?.find((card) => card.id === selectedId);

  /** 만들기·고치기 결과: 같은 id는 바꾸고 새 명함은 끝에 붙인 뒤 그것을 고른다. */
  function saved(card: MyCard) {
    setCards((items) => (items?.some((item) => item.id === card.id) ? items.map((item) => (item.id === card.id ? card : item)) : [...(items ?? []), card]));
    setSelectedId(card.id);
    setEditor(undefined);
  }
  async function remove() {
    if (!selected) return;
    setConfirming(false);
    setBusy(true);
    setActionError("");
    try {
      await deleteCard(selected.id);
      setCards((items) => {
        const rest = items?.filter((item) => item.id !== selected.id) ?? [];
        setSelectedId(rest.at(-1)?.id);
        return rest;
      });
    } catch (caught) {
      fail(caught, "명함을 지우지 못했어요. 잠시 후 다시 시도해 주세요.");
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
  if (!cards)
    return (
      <SkeletonGroup className="mt-4 flex flex-col gap-5">
        <div className="grid grid-cols-2 gap-3">
          {[0, 1, 2, 3].map((key) => (
            <Skeleton key={key} className="h-24 rounded-card" />
          ))}
        </div>
        <PublicCardBodySkeleton />
      </SkeletonGroup>
    );
  return (
    <>
      {actionError && (
        <p role="alert" className="mt-3 px-4 text-footnote text-danger">
          {actionError}
        </p>
      )}
      {cards.length === 0 && !editor ? (
        <CenteredMessage
          icon="card"
          title="아직 명함이 없어요"
          message="프로필의 연락처와 활동 이력을 골라 첫 명함을 만들어 보세요"
          primary={{ label: "새 명함 만들기", onClick: () => setEditor({}) }}
        />
      ) : (
        <>
          <ul className="mt-4 grid grid-cols-2 gap-3" aria-label="내 명함">
            <li>
              <button
                type="button"
                className="flex min-h-24 w-full flex-col items-center justify-center gap-1 rounded-card border border-dashed border-separator text-body font-semibold text-teal active:opacity-70"
                onClick={() => setEditor({})}
              >
                <Icon name="plus" size="inline" />새 명함
              </button>
            </li>
            {cards.map((card) => (
              <li key={card.id}>
                <button
                  type="button"
                  aria-pressed={card.id === selectedId}
                  className={cn(
                    "flex min-h-24 w-full flex-col gap-0.5 rounded-card border px-4 py-3 text-left active:opacity-70",
                    card.id === selectedId ? "border-teal bg-mint" : "border-transparent bg-card",
                  )}
                  onClick={() => setSelectedId(card.id)}
                >
                  <span className="truncate text-headline font-semibold">{card.name}</span>
                  <span className="truncate text-subheadline">{card.profileName || "이름 미등록"}</span>
                  {card.job && <span className="truncate text-footnote text-secondary-label">{card.job}</span>}
                </button>
              </li>
            ))}
          </ul>
          {selected && (
            <section className="mx-auto mt-6 flex max-w-[480px] flex-col gap-3" aria-label={`${selected.name} 명함`}>
              <PublicCardBody card={{ ...selected, photo: cardPhotoSrc(selected.photoUrl) }} />
              <Button size="lg" disabled={busy} onClick={() => setQr(true)}>
                <Icon name="qr" size="inline" />
                QR 보이기
              </Button>
              <Button size="lg" variant="tinted" disabled={busy} onClick={() => setEditor({ card: selected })}>
                <Icon name="pencil" size="inline" />이 명함 고치기
              </Button>
              <Button variant="destructive" className="mx-auto" disabled={busy} onClick={() => setConfirming(true)}>
                <Icon name="trash" size="inline" />이 명함 지우기
              </Button>
            </section>
          )}
        </>
      )}
      {editor && <CardEditor card={editor.card} onClose={() => setEditor(undefined)} onSaved={saved} />}
      {qr && selected && <QrScreen card={selected} presetActivityId={openQr} onClose={() => setQr(false)} />}
      <Dialog open={confirming} onOpenChange={setConfirming}>
        <DialogContent>
          <DialogTitle>이 명함을 지울까요?</DialogTitle>
          <DialogDescription>공개 링크도 더 열리지 않아요. 되돌릴 수 없어요.</DialogDescription>
          <DialogActions>
            <DialogClose className="text-body text-teal">취소</DialogClose>
            <button type="button" className="text-body font-semibold text-danger" onClick={remove}>
              지우기
            </button>
          </DialogActions>
        </DialogContent>
      </Dialog>
    </>
  );
}
