"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import type { Card } from "@/lib/models";
import { errorMessage, mutateGuest, request, RequestError } from "@/lib/client";
import { Empty, Failure, Loading } from "@/shared/ui/states";
import { Icon } from "@/shared/ui/icon";
export function SavedCards() {
  const [cards, setCards] = useState<Card[]>();
  const [error, setError] = useState("");
  const [invalid, setInvalid] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState("");
  const [notice, setNotice] = useState("");
  useEffect(() => {
    const controller = new AbortController();
    request<{ items: Card[] }>("/guest/cards", { signal: controller.signal })
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
      setCards((items) => items?.filter((item) => item.id !== id));
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
  return (
    <>
      <div className="page-heading">
        <p className="eyebrow">기억하고 싶은 연결</p>
        <h1>저장한 명함</h1>
        <p className="muted">
          공유받은 사람의 공개 명함과 이력서를 모아 보세요.
        </p>
      </div>
      <div className="cookie-info">
        <Icon name="bookmark" />
        <div>
          <strong>이 브라우저에서 저장한 명함이에요.</strong>
          <p>
            서버 저장 세션에는 자동 만료가 없어요. 다만 쿠키 삭제·브라우저 보관
            제한·시크릿 모드 종료로 쿠키가 사라지면 기존 목록에 다시 접근할 수
            없어요. 다른 기기·브라우저와는 연동되지 않아요.
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
        <div className="state">
          <h2>저장 세션을 사용할 수 없어요</h2>
          <p>
            삭제되었거나 유효하지 않은 세션이에요.
            <br />
            자동으로 새 목록을 만들지 않았어요.
          </p>
          <button className="button secondary" disabled={busy} onClick={reset}>
            저장 세션 초기화
          </button>
        </div>
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
        <Empty kind="bookmark" title="아직 저장한 명함이 없어요">
          <p>
            공유받은 명함 링크를 열고
            <br />
            ‘명함 저장’을 눌러 보세요.
          </p>
        </Empty>
      ) : (
        <>
          <p className="list-count">명함 {cards.length}개</p>
          <ul className="saved-list">
            {cards.map((card) => (
              <li key={card.id} className="saved-card">
                <Link href={`/cards/${card.id}`}>
                  <span className="avatar small" aria-hidden="true">
                    {Array.from(card.profileName)[0] || <Icon name="card" />}
                  </span>
                  <div>
                    <h2>{card.profileName || "이름 미등록"}</h2>
                    <p>{card.job}</p>
                    <p className="muted">{card.name}</p>
                  </div>
                  <span className="card-chevron" aria-hidden="true">
                    ›
                  </span>
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
            ))}
          </ul>
          <p className="cookie-note">
            공개가 취소된 명함은 목록에서 표시되지 않아요.
          </p>
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
