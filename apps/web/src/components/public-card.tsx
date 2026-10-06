"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import type { Card } from "@/lib/models";
import { errorMessage, mutateGuest, request, RequestError } from "@/lib/client";
import { Failure, Loading } from "@/shared/ui/states";
import { Icon } from "@/shared/ui/icon";
import { MissingCard } from "@/widgets/card/missing-card";
import { PublicCardBody } from "@/widgets/card/public-card-body";
export function PublicCard({ id }: { id: string }) {
  const [card, setCard] = useState<Card>();
  const [error, setError] = useState("");
  const [missing, setMissing] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState(false);
  const [saveError, setSaveError] = useState("");
  useEffect(() => {
    const controller = new AbortController();
    request<Card>(`/cards/${id}`, { signal: controller.signal })
      .then(setCard)
      .catch((error) => {
        if (controller.signal.aborted) return;
        if (error instanceof RequestError && error.status === 404)
          setMissing(true);
        else setError(errorMessage(error));
      });
    return () => controller.abort();
  }, [id, attempt]);
  async function save() {
    setBusy(true);
    setSaveError("");
    try {
      await mutateGuest(`/guest/cards/${id}`, "PUT");
      // A successful write alone is not enough if the browser rejected the cookie.
      const result = await request<{ items: Card[] }>("/guest/cards");
      if (!result.items.some((item) => item.id.toLowerCase() === id))
        throw new Error("COOKIE_UNAVAILABLE");
      setSaved(true);
    } catch (error) {
      if (error instanceof RequestError && error.status === 404) {
        setMissing(true);
        setCard(undefined);
      } else if (error instanceof RequestError && error.status === 401)
        setSaveError(
          "저장 세션을 사용할 수 없어요. 저장한 명함 화면에서 세션을 초기화해 주세요.",
        );
      else if (error instanceof RequestError && error.status === 409)
        setSaveError(
          "저장 가능한 한도에 도달했어요. 저장한 명함을 정리하거나 잠시 후 다시 시도해 주세요.",
        );
      else if (error instanceof Error && error.message === "COOKIE_UNAVAILABLE")
        setSaveError(
          "저장을 확인하지 못했어요. 브라우저에서 쿠키를 허용한 뒤 다시 시도해 주세요.",
        );
      else if (
        error instanceof Error &&
        error.message === "WEB_LOCKS_UNAVAILABLE"
      )
        setSaveError(
          "이 브라우저에서는 안전한 저장을 지원하지 않아요. 최신 브라우저에서 열어 주세요.",
        );
      else setSaveError(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }
  if (missing) return <MissingCard />;
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
  if (!card) return <Loading />;
  return (
    <article className="public-card detail">
      <Link className="back-link" href="/saved">
        <Icon name="arrow" size={20} />
        저장한 명함
      </Link>
      <PublicCardBody card={card} />
      <div className="save-area">
        <p className="muted">로그인 없이 명함을 저장할 수 있어요.</p>
        {saveError && (
          <p className="error-message" role="alert">
            {saveError} <Link href="/saved">저장한 명함 보기</Link>
          </p>
        )}
        {saved ? (
          <>
            <p className="success" role="status">
              <Icon name="check" size={20} />
              명함을 저장했어요.
            </p>
            <Link className="button" href="/saved">
              저장한 명함 보기
            </Link>
          </>
        ) : (
          <button className="button" disabled={busy} onClick={save}>
            <Icon name="bookmark" size={20} />
            {busy ? "저장 확인 중…" : "명함 저장"}
          </button>
        )}
        <p className="cookie-note">
          이 브라우저의 쿠키로 저장 목록을 구분해요.
          <br />
          쿠키를 삭제하거나 다른 브라우저를 사용하면 기존 목록에 접근할 수
          없어요.
        </p>
      </div>
    </article>
  );
}
