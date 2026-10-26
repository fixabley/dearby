"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { type GuestItem, expiryLabel, sessionContextLines } from "@/lib/models";
import { InstallOffer } from "./install-offer";
import { useIosHomeScreenApp } from "@/lib/install";
import { errorMessage, mutateGuest, request, RequestError } from "@/lib/client";
import { Failure } from "@/shared/ui/states";
import { CenteredMessage } from "@/shared/ui/centered-message";
import { Icon } from "@/shared/ui/icon";
import { Badge } from "@/shared/ui/badge";
import { Button } from "@/shared/ui/button";
import { Card } from "@/shared/ui/card";
import { Dialog, DialogActions, DialogClose, DialogContent, DialogDescription, DialogTitle } from "@/shared/ui/dialog";
import { InsetGroup } from "@/shared/ui/inset-group";
import { LargeTitle } from "@/shared/ui/nav-title";
import { Skeleton, SkeletonGroup } from "@/shared/ui/skeleton";

/** Saved cards (contract "웹 리디자인" › `/saved`): large title, cookie note, the cards as an inset-grouped list, reset behind an alert. */
export function SavedCards() {
  const [cards, setCards] = useState<GuestItem[]>();
  const [error, setError] = useState("");
  const [invalid, setInvalid] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState("");
  const [notice, setNotice] = useState("");
  const [confirming, setConfirming] = useState(false);
  const homeScreenApp = useIosHomeScreenApp();
  useEffect(() => {
    const controller = new AbortController();
    request<{ items: GuestItem[] }>("/guest/cards", {
      signal: controller.signal,
    })
      .then((result) => setCards(result.items))
      .catch((error) => {
        if (controller.signal.aborted) return;
        if (error instanceof RequestError && error.status === 401)
          setInvalid(true);
        else setError(errorMessage(error));
      });
    return () => controller.abort();
  }, [attempt]);
  async function remove(id: string) {
    setBusy(true);
    setActionError("");
    setNotice("");
    try {
      await mutateGuest(`/guest/cards/${id}`, "DELETE");
      setCards((items) => items?.filter((item) => item.card.id !== id));
      setNotice("명함을 저장 목록에서 삭제했어요.");
    } catch (error) {
      if (error instanceof RequestError && error.status === 401) {
        setInvalid(true);
        setCards(undefined);
      } else setActionError(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }
  async function reset() {
    setConfirming(false);
    setBusy(true);
    setActionError("");
    try {
      await mutateGuest("/guest/session", "DELETE");
      setCards([]);
      setInvalid(false);
      setError("");
      setNotice("저장 세션을 삭제했어요. 명함을 저장하면 새 목록이 시작돼요.");
    } catch (error) {
      setActionError(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }
  // 프로그램 칩과 회수 배지(contract "명함 교환"): 웹 저장은 맞교환이 없어 항상 사라지는 날이 있다. A receipt with session
  // fields (교환 세션 맥락) shows `프로그램 · 세션 시간표 · 트랙 · 장소` and the session title instead of the chip.
  function row({ card, context, expiresAt }: GuestItem) {
    const expiry = expiryLabel(expiresAt);
    const session = sessionContextLines(context);
    const chip = !session.length && context.programName;
    return (
      <li key={card.id} className="flex items-center">
        <Link
          href={`/cards/${card.id}`}
          className="flex min-w-0 flex-1 items-center gap-3 py-3 pl-4 active:opacity-70"
          aria-label={[card.profileName || "이름 미등록", card.job, ...(session.length ? session : [context.programName]), expiry]
            .filter(Boolean)
            .join(", ")}
        >
          <span
            aria-hidden="true"
            className="grid size-10 shrink-0 place-items-center rounded-full bg-mint text-headline font-bold text-teal"
          >
            {Array.from(card.profileName)[0] || <Icon name="card" />}
          </span>
          <span className="flex min-w-0 flex-col gap-0.5">
            <span className="text-headline font-semibold">{card.profileName || "이름 미등록"}</span>
            {card.job && <span className="text-subheadline">{card.job}</span>}
            <span className="truncate text-footnote text-secondary-label">{card.name}</span>
            {session.length > 0 && (
              <span className="mt-1 flex flex-col gap-0.5 text-footnote break-keep text-secondary-label">
                {session.map((line) => (
                  <span key={line}>{line}</span>
                ))}
              </span>
            )}
            {(chip || expiry) && (
              <span className="mt-1 flex flex-wrap gap-1.5">
                {chip && <Badge>{chip}</Badge>}
                {expiry && <Badge variant="tinted">{expiry}</Badge>}
              </span>
            )}
          </span>
        </Link>
        <Button
          variant="destructive"
          size="sm"
          aria-label={`${card.profileName || "명함"} 저장 삭제`}
          disabled={busy}
          onClick={() => remove(card.id)}
        >
          저장 삭제
        </Button>
      </li>
    );
  }
  return (
    <>
      <LargeTitle>저장한 명함</LargeTitle>
      <Card className="mt-2 flex gap-3 px-4 py-3">
        <span className="flex shrink-0">
          <Icon name="info" tone="teal" />
        </span>
        <div className="flex flex-col gap-1">
          <strong className="text-subheadline font-semibold">이 브라우저에서 저장한 명함이에요.</strong>
          <p className="text-footnote text-secondary-label">
            저장한 명함은 30일 뒤 사라져요. 쿠키 삭제·브라우저 보관 제한·시크릿 모드 종료로 쿠키가 사라지면 기존 목록에 다시 접근할 수
            없어요. 다른 기기·브라우저와는 연동되지 않아요.
          </p>
        </div>
      </Card>
      {actionError && (
        <p role="alert" className="mt-3 px-4 text-footnote text-danger">
          {actionError}
        </p>
      )}
      <p role="status" className="px-4 text-footnote text-teal [&:not(:empty)]:pt-2">
        {notice}
      </p>
      {invalid ? (
        <CenteredMessage
          icon="warning"
          tone="danger"
          title="저장 세션을 사용할 수 없어요"
          message="삭제되었거나 유효하지 않은 세션이에요. 자동으로 새 목록을 만들지 않았어요."
          primary={{ label: "저장 세션 초기화", onClick: () => setConfirming(true), disabled: busy }}
          role="alert"
        />
      ) : error ? (
        <Failure
          message={error}
          retry={() => {
            setError("");
            setAttempt((value) => value + 1);
          }}
        />
      ) : !cards ? (
        // The saved list's shape: three avatar rows on one inset-grouped card.
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
      ) : !cards.length && homeScreenApp ? (
        <HomeScreenEmpty />
      ) : !cards.length ? (
        <CenteredMessage
          icon="inbox"
          title="아직 저장한 명함이 없어요"
          message="받은 명함 링크를 열고 ‘브라우저에 저장’을 눌러 보세요."
          primary={{ label: "활동 둘러보기", href: "/" }}
        />
      ) : (
        <>
          <InsetGroup
            title={`명함 ${cards.length}개`}
            className="mt-5"
            footer="공개가 취소된 명함은 목록에서 표시되지 않아요. 브라우저에 저장한 명함은 저장한 날부터 30일 뒤 사라져요."
          >
            {cards.map((item) => row(item))}
          </InsetGroup>
          <InstallOffer place="saved" />
        </>
      )}
      {!invalid && cards && (
        <Button variant="destructive" className="mx-auto mt-6 flex" disabled={busy} onClick={() => setConfirming(true)}>
          이 브라우저의 저장 세션 삭제
        </Button>
      )}
      {/* The former `window.confirm`, as an iOS alert. */}
      <Dialog open={confirming} onOpenChange={setConfirming}>
        <DialogContent>
          <DialogTitle>저장 세션을 삭제할까요?</DialogTitle>
          <DialogDescription>이 브라우저의 저장 세션을 삭제하면 기존 목록에 다시 접근할 수 없어요.</DialogDescription>
          <DialogActions>
            <DialogClose className="text-body text-teal">취소</DialogClose>
            <button type="button" className="text-body font-semibold text-danger" onClick={reset}>
              삭제
            </button>
          </DialogActions>
        </DialogContent>
      </Dialog>
    </>
  );
}

/**
 * Empty list in the iOS home-screen web app (contract "홈 화면 세션 잇기"): the session code is single-use and short-lived on
 * purpose, and only the manifest read while adding the shortcut carries one, so this app cannot fetch a new one. The way back is
 * Safari's `/saved`, then adding the shortcut again; 주소 복사 helps since a link here would stay inside the web app.
 */
function HomeScreenEmpty() {
  const [copied, setCopied] = useState(false);
  const address = `${location.host}/saved`;
  return (
    <>
      <CenteredMessage
        icon="inbox"
        title="브라우저에서 저장한 명함을 불러오지 못했어요"
        message={`Safari에서 ${address}를 열면 다시 이어져요.`}
        primary={{
          label: copied ? "주소를 복사했어요" : "주소 복사",
          onClick: () =>
            navigator.clipboard?.writeText(`https://${address}`).then(
              () => setCopied(true),
              () => {},
            ),
        }}
        secondary={{ label: "활동 둘러보기", href: "/" }}
      />
      <p className="-mt-4 px-6 text-center text-footnote break-keep text-secondary-label">
        Safari에서 연 뒤 공유 › 홈 화면에 추가로 바로가기를 다시 추가하면, 새 바로가기에서 목록이 보여요. 지금 바로가기는 지워도
        돼요.
      </p>
    </>
  );
}
