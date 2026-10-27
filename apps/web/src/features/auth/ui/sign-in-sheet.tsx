"use client";
import { useEffect, useRef, useState } from "react";
import { RequestError } from "@/lib/client";
import { cn } from "@/shared/lib/utils";
import { Button } from "@/shared/ui/button";
import { AppleButton, Spinner } from "@/shared/ui/apple-button";
import { BrandLockup } from "@/shared/ui/brand-lockup";
import { Sheet, SheetClose, SheetContent, SheetDescription, SheetTitle } from "@/shared/ui/sheet";
import {
  appleNonce,
  appleSignIn as finishApple,
  googleNonce,
  googleSignIn as finishGoogle,
  linkIdentity,
  passkeyOptions,
  passkeySignIn,
  registerAccount,
  type Provider,
  type SocialResult,
} from "../api/auth-client";
import { getPasskey, passkeyCancelled, PasskeyUnsupported } from "../lib/webauthn";
import { APPLE_REDIRECT_URI, APPLE_SERVICES_ID, GOOGLE_CLIENT_ID, appleCancelled, appleDisplayName, appleSignIn, prepareApple, renderGoogleButton } from "../lib/social";

type Action = "apple" | "google" | "passkey" | "new" | "link";
type Pending = { provider: Provider; linkToken: string; email: string | null };
const providerName: Record<Provider, string> = { apple: "애플", google: "구글" };

/**
 * 로그인 시트 (contract "패스키 로그인" › 앱 동작, "애플 로그인" › 로그인 고지, "웹 로그인"): the lockup, then Apple → Google → the
 * notice line → `패스키로 로그인` as the quiet text button. New accounts start only with Apple or Google (2026-10-25). A first-time
 * Apple/Google account (202) asks whether to start a new account or link an existing passkey account — the web cannot check the
 * device's passkeys quietly, so it asks straight away. The body mounts with the sheet, so every opening starts clean.
 */
export function SignInSheet({
  open,
  onOpenChange,
  onSignedIn,
  footer,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSignedIn: () => void;
  /** Composed by the app layer under the notice line (the 비공개 테스트 invite); this feature does not import it. */
  footer?: React.ReactNode;
}) {
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
      <SheetContent aria-describedby={undefined} className="min-h-[50dvh]">
        <div className="grid grid-cols-[1fr_auto_1fr] items-center">
          <span />
          <SheetTitle className="text-center">로그인</SheetTitle>
          <SheetClose className="-mr-2 inline-flex min-h-11 items-center justify-self-end px-2 text-body text-teal" disabled={busy}>
            닫기
          </SheetClose>
        </div>
        <SignInBody onBusy={setBusy} onSignedIn={onSignedIn} footer={footer} />
      </SheetContent>
    </Sheet>
  );
}

/**
 * Nonces are fetched when the body mounts, because the Apple and Google popups must start inside the click, and fetched again
 * after each attempt (each nonce is accepted once).
 */
function SignInBody({ onBusy, onSignedIn, footer }: { onBusy: (busy: boolean) => void; onSignedIn: () => void; footer?: React.ReactNode }) {
  const [running, setRunning] = useState<Action>();
  const [message, setMessage] = useState("");
  const [pending, setPending] = useState<Pending>();
  const [attempt, setAttempt] = useState(0);
  /** The attempt whose Apple nonce is on Apple JS; the button waits until it matches `attempt`. */
  const [appleReadyFor, setAppleReadyFor] = useState(-1);
  const [appleOff, setAppleOff] = useState(false);
  const [googleOff, setGoogleOff] = useState(false);
  const [googleReadyFor, setGoogleReadyFor] = useState(-1);
  const appleChallenge = useRef<string>(undefined);
  const googleHost = useRef<HTMLDivElement>(null);
  const busy = running !== undefined;
  const appleShown = APPLE_SERVICES_ID !== undefined && !appleOff;
  const googleShown = GOOGLE_CLIENT_ID !== undefined && !googleOff;
  const social = appleShown || googleShown;
  useEffect(() => onBusy(busy), [busy, onBusy]);

  // Apple: a nonce per attempt, set on Apple JS before the click.
  useEffect(() => {
    const clientId = APPLE_SERVICES_ID;
    if (!clientId || appleOff) return;
    let cancelled = false;
    appleChallenge.current = undefined;
    appleNonce()
      .then(({ challengeId, nonce }) => prepareApple(clientId, nonce).then(() => challengeId))
      .then((challengeId) => {
        if (cancelled) return;
        appleChallenge.current = challengeId;
        setAppleReadyFor(attempt);
      })
      .catch((error) => {
        if (cancelled) return;
        if (error instanceof RequestError && error.status === 503) setAppleOff(true);
        else setMessage("지금은 애플 로그인을 쓸 수 없어요.");
      });
    return () => {
      cancelled = true;
    };
  }, [attempt, appleOff]);
  // Google: the nonce is fixed when the button is initialised, so the button is rendered per attempt.
  useEffect(() => {
    const clientId = GOOGLE_CLIENT_ID;
    if (!clientId || googleOff || pending) return;
    let cancelled = false;
    googleNonce()
      .then(({ nonce }) => {
        if (cancelled || !googleHost.current) return;
        return renderGoogleButton(googleHost.current, clientId, nonce, (idToken) => {
          if (!cancelled) run("google", () => finishGoogle(idToken, nonce).then(socialDone("google")));
        }).then(() => {
          if (!cancelled) setGoogleReadyFor(attempt);
        });
      })
      .catch((error) => {
        if (cancelled) return;
        if (error instanceof RequestError && error.status === 503) setGoogleOff(true);
        else setMessage("지금은 구글 로그인을 쓸 수 없어요.");
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- `run`/`socialDone` only close over state setters.
  }, [attempt, googleOff, pending]);

  function socialDone(provider: Provider) {
    return (result: SocialResult) => {
      if (result.kind === "signedIn") onSignedIn();
      else setPending({ provider, linkToken: result.linkToken, email: result.email });
    };
  }
  /** Runs one sign-in path with the usual messages; Apple and Google consume their nonce, so the next attempt gets new ones. */
  async function run(action: Action, work: () => Promise<void>, failure = "로그인하지 못했어요. 다시 시도해 주세요.") {
    setRunning(action);
    setMessage("");
    try {
      await work();
    } catch (error) {
      if (passkeyCancelled(error) || appleCancelled(error)) return;
      if (error instanceof PasskeyUnsupported) setMessage("이 브라우저는 패스키를 지원하지 않아요. Apple·Google로 로그인해 주세요.");
      else if (error instanceof RequestError && error.status === 429) setMessage("요청이 많아요. 잠시 후 다시 시도해 주세요.");
      else if (error instanceof RequestError && error.status === 503)
        setMessage(action === "apple" ? "지금은 애플 로그인을 쓸 수 없어요." : "지금은 구글 로그인을 쓸 수 없어요.");
      else if (error instanceof RequestError && error.status === 400 && (action === "link" || action === "new"))
        setMessage("이미 다른 계정과 연결돼 있어서 연결하지 못했어요.");
      else if (error instanceof RequestError && error.status === 401 && (action === "link" || action === "new"))
        setMessage(`${pending ? providerName[pending.provider] : "소셜"} 로그인 시간이 지났어요. 처음부터 다시 시작해 주세요.`);
      else setMessage(failure);
    } finally {
      setRunning(undefined);
      if (action === "apple" || action === "google") setAttempt((value) => value + 1);
    }
  }
  function apple() {
    const challengeId = appleChallenge.current;
    if (!challengeId) return;
    // The popup opens inside this click; everything else awaits.
    const response = appleSignIn();
    run(
      "apple",
      async () => {
        const result = await response;
        await finishApple({
          challengeId,
          identityToken: result.authorization.id_token,
          // Converts upstream only when Apple JS was given the API's callback as `redirectURI` (contract "웹 로그인" › 애플).
          authorizationCode: APPLE_REDIRECT_URI ? result.authorization.code : undefined,
          displayName: appleDisplayName(result.user),
        }).then(socialDone("apple"));
      },
      "애플로 로그인하지 못했어요. 다시 시도해 주세요.",
    );
  }
  /**
   * `패스키로 로그인`; with a pending first-time account, the same sign-in then links that account to it. Once the passkey sign-in
   * has set the cookies the person is signed in, so a failed link still finishes the sign-in (the link can be retried from 설정).
   */
  function passkey(link?: Pending) {
    run(
      link ? "link" : "passkey",
      async () => {
        const { challengeId, options } = await passkeyOptions();
        const credential = await getPasskey(options);
        await passkeySignIn(challengeId, credential);
        if (link) await linkIdentity(link.provider, link.linkToken).finally(onSignedIn);
        else onSignedIn();
      },
      link ? "패스키 계정에 연결하지 못했어요. 다시 시도해 주세요." : "패스키로 로그인하지 못했어요. 다시 시도하거나 Apple·Google로 로그인해 주세요.",
    );
  }
  function startAccount(link: Pending) {
    run("new", () => registerAccount(link.provider, link.linkToken).then(() => onSignedIn()), "새 계정을 만들지 못했어요. 다시 시도해 주세요.");
  }

  if (pending)
    return (
      <div className="flex flex-col items-center gap-3 pt-4 text-center">
        <h3 className="text-title2 font-bold break-keep">처음 보는 {providerName[pending.provider]} 계정이에요</h3>
        <SheetDescription className="text-body break-keep">이미 패스키로 만든 Dearby 계정이 있으면 연결하고, 없으면 새 계정으로 시작해요.</SheetDescription>
        {pending.email && <p className="text-footnote text-secondary-label">{pending.email}</p>}
        <Message message={message} />
        <div className="mt-3 flex w-full flex-col gap-2">
          <Button size="lg" disabled={busy} onClick={() => startAccount(pending)}>
            {running === "new" ? <Spinner /> : "새 계정으로 시작"}
          </Button>
          <Button size="lg" variant="tinted" disabled={busy} onClick={() => passkey(pending)}>
            {running === "link" ? <Spinner /> : "기존 패스키 계정에 연결"}
          </Button>
          <Button size="lg" variant="plain" disabled={busy} onClick={() => setPending(undefined)}>
            다른 방법으로 로그인
          </Button>
        </div>
      </div>
    );
  return (
    <div className="flex flex-col items-center gap-3 pt-6">
      <BrandLockup className="mb-4" />
      <Message message={message} />
      <div className="flex w-full flex-col gap-2">
        {appleShown && <AppleButton onClick={apple} disabled={busy || appleReadyFor !== attempt} busy={running === "apple"} />}
        {googleShown && (
          <div className="relative flex min-h-[44px] justify-center" aria-busy={running === "google"}>
            {/* Sized here, not by Google: the button is rendered at this host's width (see `renderGoogleButton`). */}
            <div ref={googleHost} className={cn("w-full max-w-[400px]", (busy || googleReadyFor !== attempt) && "pointer-events-none opacity-50")} />
            {running === "google" && <Spinner className="absolute top-1/2 -mt-2.5 text-teal" />}
          </div>
        )}
        {social && (
          <p className="px-2 pt-1 text-center text-footnote break-keep text-secondary-label">
            로그인하면 계정 식별을 위해 Apple·Google 계정 정보(이메일 등)를{" "}
            <a href="/privacy" target="_blank" rel="noopener" className="text-teal underline">
              개인정보처리방침
            </a>
            에 따라 처리해요.
          </p>
        )}
        <Button size="lg" variant={social ? "plain" : "filled"} disabled={busy} onClick={() => passkey()}>
          {running === "passkey" ? <Spinner /> : "패스키로 로그인"}
        </Button>
        {footer}
      </div>
    </div>
  );
}

function Message({ message }: { message: string }) {
  return (
    <p role="alert" className="text-center text-footnote break-keep text-danger [&:empty]:hidden">
      {message}
    </p>
  );
}
