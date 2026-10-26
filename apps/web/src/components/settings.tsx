"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { RequestError } from "@/lib/client";
import { useAuth } from "@/features/auth/model/auth-context";
import { deleteAccount, deletePasskey, me, passkeys as listPasskeys, registerPasskey, registrationOptions, type Me, type Passkey } from "@/features/auth/api/auth-client";
import { createPasskey, passkeyCancelled, passkeyExists, PasskeyUnsupported } from "@/features/auth/lib/webauthn";
import { Button } from "@/shared/ui/button";
import { Card } from "@/shared/ui/card";
import { Dialog, DialogActions, DialogClose, DialogContent, DialogDescription, DialogTitle } from "@/shared/ui/dialog";
import { Icon } from "@/shared/ui/icon";
import { InsetGroup } from "@/shared/ui/inset-group";
import { LargeTitle } from "@/shared/ui/nav-title";
import { Skeleton, SkeletonGroup } from "@/shared/ui/skeleton";
import { Failure } from "@/shared/ui/states";

/**
 * `/settings` (contract "설정" 2026-10-19 밤, web since 2026-10-26 "웹 로그인"): inset groups like the apps' settings list. Signed out it is
 * the 로그인 CTA and 정보; signed in without consent, the 동의하기 notice and 정보; signed in, 계정 (가입일·로그인 수단·로그아웃·계정 삭제),
 * 패스키 (list, 삭제, 패스키 추가) and 정보. 알림·연결된 로그인 come later.
 */
export function Settings() {
  const auth = useAuth();
  const [notice, setNotice] = useState("");
  return (
    <>
      <LargeTitle>설정</LargeTitle>
      <p role="status" className="px-4 text-footnote text-teal [&:not(:empty)]:pt-2">
        {notice}
      </p>
      {auth.status === "loading" ? (
        <SkeletonGroup className="mt-7">
          <Card className="divide-y divide-separator">
            {[0, 1, 2].map((key) => (
              <div key={key} className="flex min-h-11 items-center px-4">
                <Skeleton className="h-4 w-1/3" />
              </div>
            ))}
          </Card>
        </SkeletonGroup>
      ) : !auth.signedIn ? (
        <section className="mt-7 flex flex-col gap-1.5 px-1">
          <Button size="lg" onClick={auth.openSignIn}>
            로그인
          </Button>
          <p className="px-3 text-footnote text-secondary-label">로그인하면 가입일, 로그인 수단, 패스키를 관리할 수 있어요.</p>
        </section>
      ) : auth.consentRequired ? (
        <InsetGroup plain footer="동의하기 전에는 계정 기능을 쓸 수 없어요. 동의하지 않으면 로그인은 유지되고 다음에 다시 물어요.">
          <div className="flex flex-col gap-3 py-1">
            <p className="text-headline font-semibold">동의가 필요해요</p>
            <Button size="lg" onClick={auth.openConsent}>
              동의하기
            </Button>
          </div>
        </InsetGroup>
      ) : (
        <Account onNotice={setNotice} />
      )}
      <InsetGroup title="정보">
        <li>
          <Link href="/privacy" className="flex min-h-11 items-center justify-between px-4 active:opacity-70">
            개인정보 처리방침
            <Icon name="chevron-right" tone="secondary" />
          </Link>
        </li>
        <li>
          <Link href="/terms" className="flex min-h-11 items-center justify-between px-4 active:opacity-70">
            이용약관
            <Icon name="chevron-right" tone="secondary" />
          </Link>
        </li>
      </InsetGroup>
    </>
  );
}

function Account({ onNotice }: { onNotice: (message: string) => void }) {
  const auth = useAuth();
  const { signedOutLocally } = auth;
  const [summary, setSummary] = useState<Me>();
  const [passkeys, setPasskeys] = useState<Passkey[]>();
  const [error, setError] = useState("");
  const [actionError, setActionError] = useState("");
  const [busy, setBusy] = useState(false);
  const [confirming, setConfirming] = useState<"signOut" | "delete">();
  const [attempt, setAttempt] = useState(0);
  const load = () => setAttempt((value) => value + 1);
  useEffect(() => {
    let cancelled = false;
    Promise.all([me(), listPasskeys()]).then(
      ([account, list]) => {
        if (cancelled) return;
        setSummary(account);
        setPasskeys(list.items);
      },
      (caught) => {
        if (cancelled) return;
        if (caught instanceof RequestError && caught.status === 401) signedOutLocally("로그인이 만료됐어요. 다시 로그인해 주세요.");
        else setError("설정을 불러오지 못했어요.");
      },
    );
    return () => {
      cancelled = true;
    };
  }, [attempt, signedOutLocally]);
  const methods = summary?.signInMethods;
  const lastMethod = methods !== undefined && (passkeys?.length ?? methods.passkeys) + (methods.apple ? 1 : 0) + (methods.google ? 1 : 0) <= 1;

  /** Runs one account action; a 401 means the session is over. */
  async function act(work: () => Promise<void>, failure: string) {
    setBusy(true);
    setActionError("");
    try {
      await work();
    } catch (caught) {
      if (passkeyCancelled(caught)) return;
      if (caught instanceof RequestError && caught.status === 401) auth.signedOutLocally("로그인이 만료됐어요. 다시 로그인해 주세요.");
      else if (caught instanceof RequestError && caught.code === "LAST_SIGN_IN_METHOD") setActionError("마지막 로그인 수단이라 지울 수 없어요.");
      else if (caught instanceof PasskeyUnsupported) setActionError("이 브라우저는 패스키를 지원하지 않아요.");
      else if (passkeyExists(caught)) setActionError("이 기기에는 이미 이 계정의 패스키가 있어요.");
      else setActionError(caught instanceof RequestError && caught.detail ? caught.detail : failure);
    } finally {
      setBusy(false);
    }
  }
  const signOut = () => {
    setConfirming(undefined);
    act(() => auth.signOut(), "로그아웃하지 못했어요.");
  };
  const remove = () => {
    setConfirming(undefined);
    const hadPasskeys = (passkeys?.length ?? 0) > 0;
    act(async () => {
      await deleteAccount();
      auth.signedOutLocally(hadPasskeys ? "계정을 삭제했어요. 기기에 남은 패스키는 브라우저나 OS의 비밀번호 관리자에서 지울 수 있어요." : "계정을 삭제했어요.");
    }, "계정을 삭제하지 못했어요. 잠시 후 다시 시도해 주세요.");
  };
  const addPasskey = () =>
    act(async () => {
      const { challengeId, options } = await registrationOptions();
      const credential = await createPasskey(options);
      await registerPasskey(challengeId, credential);
      onNotice("패스키를 추가했어요.");
      load();
    }, "패스키를 추가하지 못했어요. 다시 시도해 주세요.");
  const removePasskey = (passkey: Passkey) =>
    act(async () => {
      await deletePasskey(passkey.id);
      setPasskeys((items) => items?.filter((item) => item.id !== passkey.id));
      onNotice("패스키를 지웠어요.");
    }, "패스키를 지우지 못했어요. 잠시 후 다시 시도해 주세요.");

  if (error)
    return (
      <Failure
        message={error}
        retry={() => {
          setError("");
          load();
        }}
        compact
      />
    );
  if (!summary || !passkeys)
    return (
      <SkeletonGroup className="mt-7">
        <Card className="divide-y divide-separator">
          {[0, 1, 2].map((key) => (
            <div key={key} className="flex min-h-11 items-center justify-between px-4">
              <Skeleton className="h-4 w-1/4" />
              <Skeleton className="h-4 w-1/3" />
            </div>
          ))}
        </Card>
      </SkeletonGroup>
    );
  return (
    <>
      {actionError && (
        <p role="alert" className="mt-3 px-4 text-footnote text-danger">
          {actionError}
        </p>
      )}
      <InsetGroup title="계정">
        <Row label="가입일" value={day(summary.createdAt)} />
        <Row label="로그인 수단" value={methodsLabel(summary.signInMethods, passkeys.length)} />
        <li>
          <button type="button" className="flex min-h-11 w-full items-center px-4 text-left text-teal active:opacity-70" disabled={busy} onClick={() => setConfirming("signOut")}>
            로그아웃
          </button>
        </li>
        <li>
          <button type="button" className="flex min-h-11 w-full items-center px-4 text-left text-danger active:opacity-70" disabled={busy} onClick={() => setConfirming("delete")}>
            계정 삭제
          </button>
        </li>
      </InsetGroup>
      <InsetGroup
        title="패스키"
        footer={
          lastMethod && passkeys.length
            ? "마지막 로그인 수단은 지울 수 없어요. 패스키를 하나 더 만들거나 다른 로그인을 연결해 주세요."
            : "이 계정으로 로그인할 수 있는 패스키예요."
        }
      >
        {passkeys.length === 0 && <li className="flex min-h-11 items-center px-4 text-secondary-label">등록된 패스키가 없어요.</li>}
        {passkeys.map((passkey) => (
          <li key={passkey.id} className="flex items-center gap-3 py-2 pl-4 pr-2">
            <div className="flex min-w-0 flex-1 flex-col gap-0.5">
              <span className="truncate">{passkey.label || "패스키"}</span>
              <span className="text-footnote text-secondary-label">
                {[`만든 날 ${day(passkey.createdAt)}`, passkey.lastUsedAt ? `마지막 사용 ${day(passkey.lastUsedAt)}` : "아직 사용 안 함", passkey.backedUp ? "기기 간 동기화됨" : "이 기기에만"].join(" · ")}
              </span>
            </div>
            <Button variant="destructive" size="sm" disabled={busy || lastMethod} aria-label={`${passkey.label || "패스키"} 삭제`} onClick={() => removePasskey(passkey)}>
              삭제
            </Button>
          </li>
        ))}
        <li>
          <button type="button" className="flex min-h-11 w-full items-center gap-2 px-4 text-left text-teal active:opacity-70" disabled={busy} onClick={addPasskey}>
            <Icon name="plus" size="inline" />
            패스키 추가
          </button>
        </li>
      </InsetGroup>
      <Dialog open={confirming !== undefined} onOpenChange={(next) => !next && setConfirming(undefined)}>
        <DialogContent>
          <DialogTitle>{confirming === "delete" ? "계정을 삭제할까요?" : "로그아웃할까요?"}</DialogTitle>
          <DialogDescription>
            {confirming === "delete" ? "명함과 받은 명함 등 모든 정보가 지워지고 되돌릴 수 없어요." : "이 기기에서만 로그아웃돼요. 패스키와 연결된 계정은 그대로 남아요."}
          </DialogDescription>
          <DialogActions>
            <DialogClose className="text-body text-teal">취소</DialogClose>
            <button type="button" className="text-body font-semibold text-danger" onClick={confirming === "delete" ? remove : signOut}>
              {confirming === "delete" ? "삭제" : "로그아웃"}
            </button>
          </DialogActions>
        </DialogContent>
      </Dialog>
    </>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <li className="flex min-h-11 items-center justify-between gap-3 px-4 py-2">
      <span>{label}</span>
      <span className="text-right text-secondary-label">{value}</span>
    </li>
  );
}
/** `2026년 1월 1일` from a server timestamp; the raw text if it does not parse. */
function day(iso: string): string {
  const time = Date.parse(iso);
  return Number.isNaN(time) ? iso : new Intl.DateTimeFormat("ko-KR", { year: "numeric", month: "long", day: "numeric" }).format(time);
}
function methodsLabel(methods: Me["signInMethods"], passkeyCount: number): string {
  const parts: string[] = [];
  if (passkeyCount > 0) parts.push(`패스키 ${passkeyCount}개`);
  if (methods.apple) parts.push("Apple");
  if (methods.google) parts.push("Google");
  return parts.length ? parts.join(" · ") : "없음";
}
