"use client";
import { useState } from "react";
import { errorMessage, mutateGuest, RequestError } from "@/lib/client";
import { Button } from "@/shared/ui/button";
import { Dialog, DialogActions, DialogClose, DialogContent, DialogDescription, DialogTitle } from "@/shared/ui/dialog";

const REASONS = [
  ["SPAM", "스팸·광고"],
  ["IMPERSONATION", "다른 사람 사칭"],
  ["INAPPROPRIATE", "부적절한 내용"],
  ["OTHER", "기타"],
] as const;
type Reason = (typeof REASONS)[number][0];

/** 신고하기 under the card (contract "명함 신고·차단"): reason, optional note (required for 기타), then POST cards/:id/reports. */
export function ReportCard({ id, code }: { id: string; code?: string }) {
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState<Reason>();
  const [detail, setDetail] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [done, setDone] = useState(false);
  const note = detail.trim();
  const ready = reason !== undefined && (reason !== "OTHER" || note.length > 0);
  async function send() {
    setBusy(true);
    setError("");
    try {
      await mutateGuest(`/cards/${id}/reports`, "POST", { reason, ...(note && { detail: note }), ...(code && { code }) });
      setDone(true);
    } catch (error) {
      setError(
        error instanceof RequestError && error.status === 403
          ? "명함 주인에게 QR을 다시 보여 달라고 하세요"
          : errorMessage(error),
      );
    } finally {
      setBusy(false);
    }
  }
  function change(next: boolean) {
    setOpen(next);
    if (!next) {
      setReason(undefined);
      setDetail("");
      setError("");
      setDone(false);
    }
  }
  return (
    <Dialog open={open} onOpenChange={change}>
      <Button variant="plain" size="sm" className="text-secondary-label" onClick={() => setOpen(true)}>
        신고하기
      </Button>
      <DialogContent className="w-[min(340px,calc(100vw-32px))]">
        {done ? (
          <>
            <DialogTitle>신고를 받았어요</DialogTitle>
            <DialogDescription>24시간 안에 확인할게요.</DialogDescription>
            <DialogActions>
              <DialogClose className="font-semibold text-teal">확인</DialogClose>
            </DialogActions>
          </>
        ) : (
          <>
            <DialogTitle>명함 신고하기</DialogTitle>
            <DialogDescription>신고 사유를 골라 주세요.</DialogDescription>
            <fieldset className="px-4 pb-4 text-left">
              <legend className="sr-only">신고 사유</legend>
              {REASONS.map(([value, label]) => (
                <label key={value} className="flex min-h-11 items-center gap-3 text-body">
                  <input
                    type="radio"
                    name="report-reason"
                    value={value}
                    checked={reason === value}
                    onChange={() => setReason(value)}
                    className="size-5 accent-teal"
                  />
                  {label}
                </label>
              ))}
              <label className="mt-2 block text-footnote text-secondary-label">
                설명{reason === "OTHER" ? <span className="text-danger"> *</span> : " (선택)"}
                <textarea
                  value={detail}
                  onChange={(event) => setDetail(event.target.value)}
                  maxLength={500}
                  rows={3}
                  className="mt-1 block w-full resize-none rounded-control bg-fill px-2 py-2 text-body text-label outline-none focus-visible:outline-2 focus-visible:outline-teal"
                />
              </label>
              {error && (
                <p role="alert" className="mt-2 text-footnote text-danger">
                  {error}
                </p>
              )}
            </fieldset>
            <DialogActions>
              <DialogClose className="text-teal">취소</DialogClose>
              <button type="button" className="font-semibold text-danger disabled:opacity-50" disabled={!ready || busy} onClick={send}>
                {busy ? "보내는 중…" : "보내기"}
              </button>
            </DialogActions>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}

/**
 * 삭제하고 차단하기: for a card saved in this browser POST guest/blocks recalls the save and refuses this owner's cards later;
 * a signed-in wallet passes its own `perform` (POST wallet/blocks, contract "명함 신고·차단" › 차단).
 */
export function BlockCard({ id, name, blocked, perform }: { id: string; name: string; blocked: () => void; perform?: () => Promise<void> }) {
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function block() {
    setBusy(true);
    setError("");
    try {
      if (perform) await perform();
      else await mutateGuest("/guest/blocks", "POST", { cardId: id });
      blocked();
    } catch (error) {
      setError(errorMessage(error));
      setBusy(false);
    }
  }
  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next);
        if (!next) setError("");
      }}
    >
      <Button variant="destructive" size="sm" onClick={() => setOpen(true)}>
        삭제하고 차단하기
      </Button>
      <DialogContent>
        <DialogTitle>삭제하고 차단할까요?</DialogTitle>
        <DialogDescription>
          {name}님의 명함을 지우고, 앞으로 이 사람의 명함을 받지 않아요.
          {error && (
            <span role="alert" className="mt-2 block text-danger">
              {error}
            </span>
          )}
        </DialogDescription>
        <DialogActions>
          <DialogClose className="text-teal">취소</DialogClose>
          <button type="button" className="font-semibold text-danger disabled:opacity-50" disabled={busy} onClick={block}>
            삭제하고 차단하기
          </button>
        </DialogActions>
      </DialogContent>
    </Dialog>
  );
}
