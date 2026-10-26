"use client";
import { useState } from "react";
import { RequestError } from "@/lib/client";
import type { ProfileConsent } from "@/lib/models";
import { Button } from "@/shared/ui/button";
import { Card } from "@/shared/ui/card";
import { CheckRow } from "@/shared/ui/check-row";
import { Dialog, DialogActions, DialogClose, DialogContent, DialogDescription, DialogTitle } from "@/shared/ui/dialog";
import { Icon } from "@/shared/ui/icon";
import { Sheet, SheetClose, SheetContent, SheetDescription, SheetTitle } from "@/shared/ui/sheet";
import { Spinner } from "@/shared/ui/apple-button";
import { deleteAccount, giveConsent, logout } from "../api/auth-client";
import { CONSENT_VERSIONS } from "../model/consent-versions";

/**
 * 동의 화면 (contract "개인정보 동의", 2026-10-19 → 10-25 → 10-26) as a closable bottom sheet, like the iOS root sheet: the check glyph,
 * the title (`개인정보 처리방침이 바뀌었어요` when an earlier version was agreed), the explanation, two required check rows with 보기 links,
 * `동의하고 계속` once both are on. Under it: `가입 취소` for an account with nothing saved yet (profile version 0 — deletes the account),
 * `로그아웃` otherwise. Closing keeps the session and the question comes back on the next load; account features stay off until agreed.
 */
type Props = {
  consent: ProfileConsent | null | undefined;
  onConsented: () => void;
  /** After 가입 취소 or 로그아웃: the session is gone. */
  onLeft: (notice?: string) => void;
};
export function ConsentSheet({ open, onOpenChange, ...props }: Props & { open: boolean; onOpenChange: (open: boolean) => void }) {
  const [busy, setBusy] = useState(false);
  return (
    <Sheet
      open={open}
      onOpenChange={(next) => {
        if (busy) return;
        if (!next) setBusy(false);
        onOpenChange(next);
      }}
    >
      <SheetContent className="max-h-[92dvh] overflow-y-auto">
        <div className="grid grid-cols-[1fr_auto_1fr] items-center">
          <span />
          <span aria-hidden="true" />
          <SheetClose className="-mr-2 inline-flex min-h-11 items-center justify-self-end px-2 text-body text-teal" disabled={busy}>
            닫기
          </SheetClose>
        </div>
        <ConsentBody {...props} onBusy={setBusy} />
      </SheetContent>
    </Sheet>
  );
}

/** Mounts with the sheet, so the checks and messages start clean each time it opens. */
function ConsentBody({ consent, onConsented, onLeft, onBusy }: Props & { onBusy: (busy: boolean) => void }) {
  const [privacy, setPrivacy] = useState(false);
  const [terms, setTerms] = useState(false);
  const [busy, setBusyState] = useState(false);
  const [error, setError] = useState("");
  const [confirming, setConfirming] = useState<"cancel" | "signOut">();
  const fresh = consent?.version === 0;
  const renewal = consent?.consent != null;
  const setBusy = (value: boolean) => {
    setBusyState(value);
    onBusy(value);
  };
  async function agree() {
    setBusy(true);
    setError("");
    try {
      await giveConsent(CONSENT_VERSIONS.privacy, CONSENT_VERSIONS.terms);
      onConsented();
    } catch (caught) {
      if (caught instanceof RequestError && caught.status === 401) onLeft("로그인이 만료됐어요. 다시 로그인해 주세요.");
      else if (caught instanceof RequestError && caught.code === "CONSENT_VERSION")
        setError(caught.detail ?? "동의 문서 버전이 바뀌었어요. 페이지를 새로 고친 뒤 다시 동의해 주세요.");
      else setError("동의를 저장하지 못했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setBusy(false);
    }
  }
  async function leave(kind: "cancel" | "signOut") {
    setConfirming(undefined);
    setBusy(true);
    setError("");
    try {
      if (kind === "cancel") await deleteAccount();
      else await logout();
      onLeft(kind === "cancel" ? "가입을 취소했어요." : undefined);
    } catch (caught) {
      if (caught instanceof RequestError && caught.status === 401) onLeft();
      else setError(kind === "cancel" ? "가입을 취소하지 못했어요. 잠시 후 다시 시도해 주세요." : "로그아웃하지 못했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
        <div className="flex flex-col items-center gap-3 px-2 pt-2 text-center">
          <span className="mb-1 grid size-16 place-items-center rounded-full bg-teal-fill text-on-teal">
            <Icon name="check-circle" size="state" />
          </span>
          <SheetTitle className="text-title2 font-bold break-keep">{renewal ? "개인정보 처리방침이 바뀌었어요" : "시작하기 전에 동의가 필요해요"}</SheetTitle>
          <SheetDescription className="text-body break-keep">
            프로필(이름·연락처·이력, 고른 경우 사진·소개 링크)과 내 활동·즐겨찾기 같은 계정 기록을 저장하는 데 필요해요. 광고 목적 정보 제공은 여기
            포함되지 않고 따로 묻는 선택 항목이에요. 동의 내용과 버전은 계정에 기록돼요.
          </SheetDescription>
        </div>
        <Card className="mt-5 divide-y divide-separator">
          <CheckRow checked={privacy} onChange={setPrivacy} disabled={busy}>
            개인정보 수집·이용에 동의합니다 <span className="text-secondary-label">(필수)</span>
          </CheckRow>
          <PolicyLink href="/privacy">개인정보 처리방침 보기</PolicyLink>
          <CheckRow checked={terms} onChange={setTerms} disabled={busy}>
            이용약관에 동의합니다 <span className="text-secondary-label">(필수)</span>
          </CheckRow>
          <PolicyLink href="/terms">이용약관 보기</PolicyLink>
        </Card>
        <p className="mt-1.5 px-4 text-footnote text-secondary-label">두 항목 모두 필수예요.</p>
        <p role="alert" className="px-4 pt-1 text-footnote text-danger [&:empty]:hidden">
          {error}
        </p>
        <div className="mt-4 flex flex-col gap-1">
          <Button size="lg" disabled={!privacy || !terms || busy} onClick={agree}>
            {busy && !confirming ? <Spinner /> : "동의하고 계속"}
          </Button>
          {fresh ? (
            <Button size="lg" variant="destructive" disabled={busy} onClick={() => setConfirming("cancel")}>
              가입 취소
            </Button>
          ) : (
            <Button size="lg" variant="plain" disabled={busy} onClick={() => setConfirming("signOut")}>
              로그아웃
            </Button>
          )}
        </div>
        <Dialog open={confirming !== undefined} onOpenChange={(next) => !next && setConfirming(undefined)}>
          <DialogContent>
            <DialogTitle>{confirming === "cancel" ? "가입을 취소할까요?" : "로그아웃할까요?"}</DialogTitle>
            <DialogDescription>
              {confirming === "cancel" ? "가입을 취소하면 방금 만든 계정이 삭제돼요." : "이 기기에서만 로그아웃돼요. 계정은 그대로 남아요."}
            </DialogDescription>
            <DialogActions>
              <DialogClose className="text-body text-teal">{confirming === "cancel" ? "계속 가입" : "취소"}</DialogClose>
              <button type="button" className="text-body font-semibold text-danger" onClick={() => confirming && leave(confirming)}>
                {confirming === "cancel" ? "가입 취소" : "로그아웃"}
              </button>
            </DialogActions>
          </DialogContent>
        </Dialog>
    </>
  );
}

/** `…보기` opens the policy page in a new tab (the in-app browser's counterpart) without leaving the sheet. */
function PolicyLink({ href, children }: { href: string; children: string }) {
  return (
    <a href={href} target="_blank" rel="noopener" className="flex min-h-11 items-center gap-1.5 px-4 text-subheadline text-teal">
      <Icon name="external-link" size="inline" />
      {children}
    </a>
  );
}
