"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { type GuestItem, expiryLabel } from "@/lib/models";
import { InstallOffer } from "./install-offer";
import { errorMessage, mutateGuest, request, RequestError } from "@/lib/client";
import { Failure, Loading } from "@/shared/ui/states";
import { CenteredMessage } from "@/shared/ui/centered-message";
import { Icon } from "@/shared/ui/icon";
export function SavedCards() {
  const [cards, setCards] = useState<GuestItem[]>();
  const [error, setError] = useState("");
  const [invalid, setInvalid] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState("");
  const [notice, setNotice] = useState("");
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
    if (
      !window.confirm(
        "이 브라우저의 저장 세션을 삭제할까요? 기존 목록에 다시 접근할 수 없어요.",
      )
    )
      return;
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
  // 프로그램 칩과 회수 배지(contract "명함 교환"): 웹 저장은 맞교환이 없어 항상 사라지는 날이 있다.
  function row({ card, context, expiresAt }: GuestItem) {
    const expiry = expiryLabel(expiresAt);
    return (
      <li key={card.id} className="saved-card">
        <Link
          href={`/cards/${card.id}`}
          aria-label={[card.profileName || "이름 미등록", card.job, context.programName, expiry]
            .filter(Boolean)
            .join(", ")}
        >
          <span className="avatar small" aria-hidden="true">
            {Array.from(card.profileName)[0] || <Icon name="card" />}
          </span>
          <div>
            <h2>{card.profileName || "이름 미등록"}</h2>
            <p>{card.job}</p>
            <p className="muted">{card.name}</p>
            <p className="saved-meta">
              {context.programName && (
                <span className="badge neutral">{context.programName}</span>
              )}
              {expiry && <span className="badge">{expiry}</span>}
            </p>
          </div>
        </Link>
        <button
          className="remove-button"
          aria-label={`${card.profileName || "명함"} 저장 삭제`}
          disabled={busy}
          onClick={() => remove(card.id)}
        >
          저장 삭제
        </button>
      </li>
    );
  }
  return (
    <>
      <div className="page-heading">
        <p className="eyebrow">기억하고 싶은 연결</p>
        <h1>저장한 명함</h1>
        <p className="muted">
          받은 공개 명함과 이력서를 모아 보세요.
        </p>
      </div>
      <div className="cookie-info">
        <Icon name="info" tone="secondary" />
        <div>
          <strong>이 브라우저에서 저장한 명함이에요.</strong>
          <p>
            저장한 명함은 30일 뒤 사라져요. 쿠키 삭제·브라우저 보관 제한·시크릿
            모드 종료로 쿠키가 사라지면 기존 목록에 다시 접근할 수 없어요. 다른
            기기·브라우저와는 연동되지 않아요.
          </p>
        </div>
      </div>
      {actionError && (
        <p className="error-message" role="alert">
          {actionError}
        </p>
      )}
      <p className="status-line" role="status">
        {notice}
      </p>
      {invalid ? (
        <CenteredMessage
          icon="warning"
          tone="danger"
          title="저장 세션을 사용할 수 없어요"
          message="삭제되었거나 유효하지 않은 세션이에요. 자동으로 새 목록을 만들지 않았어요."
          primary={{ label: "저장 세션 초기화", onClick: reset, disabled: busy }}
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
        <Loading />
      ) : !cards.length ? (
        <CenteredMessage
          icon="inbox"
          title="아직 저장한 명함이 없어요"
          message="받은 명함 링크를 열고 ‘브라우저에 저장’을 눌러 보세요."
          primary={{ label: "활동 둘러보기", href: "/" }}
        />
      ) : (
        <>
          <p className="list-count">명함 {cards.length}개</p>
          <ul className="saved-list">{cards.map((item) => row(item))}</ul>
          <p className="cookie-note">
            공개가 취소된 명함은 목록에서 표시되지 않아요. 브라우저에 저장한
            명함은 저장한 날부터 30일 뒤 사라져요.
          </p>
          <InstallOffer />
        </>
      )}
      {!invalid && cards && (
        <button className="reset-button" disabled={busy} onClick={reset}>
          이 브라우저의 저장 세션 삭제
        </button>
      )}
    </>
  );
}
