"use client";
import { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import type { ProfileConsent } from "@/lib/models";
import { setSignedIn } from "@/shared/lib/analytics";
import { Dialog, DialogActions, DialogClose, DialogContent, DialogDescription, DialogTitle } from "@/shared/ui/dialog";
import { Toast, type Notice } from "@/shared/ui/toast";
import { clearGuestSession, guestSavedCount, importGuest, logout, session, type Session } from "../api/auth-client";
import { ConsentSheet } from "../ui/consent-sheet";
import { SignInSheet } from "../ui/sign-in-sheet";

type Status = "loading" | "signedOut" | "signedIn";
type State = { status: Status; consent: ProfileConsent | null | undefined };
export type Auth = {
  status: Status;
  signedIn: boolean;
  /** Signed in, and the current-version consent is missing: account features open the consent sheet instead (contract "개인정보 동의"). */
  consentRequired: boolean;
  openSignIn: () => void;
  openConsent: () => void;
  /** Runs `action` now when signed in with consent; otherwise asks to sign in (and consent) first and runs it afterwards (awaited before `epoch` moves). */
  requireAccount: (action: () => void | Promise<void>) => void;
  signOut: () => Promise<void>;
  /** The session ended elsewhere (account deleted, refresh refused): forget it here and optionally say why. */
  signedOutLocally: (notice?: string) => void;
  /**
   * Counts completed sign-ins (after consent and the deferred action) and sign-outs, so public reads that carry `favorited` reload on a
   * change — not on page load, and not before the heart that opened the sheet has been saved.
   */
  epoch: number;
  /** A transient notice at the bottom of the screen (shared toast). */
  notify: (text: string) => void;
};
const AuthContext = createContext<Auth | null>(null);
const signedOut: State = { status: "signedOut", consent: undefined };

/**
 * Web sign-in state (contract "웹 로그인"): asks `/api/auth/session` once per page load, owns the sign-in and consent sheets, and
 * after a sign-in (and consent) offers to move this browser's saved cards into the account (`비로그인 저장 → 계정 합치기`: asked,
 * never automatic). Consent is asked again on every load while required, like the apps ask at launch; closing it keeps the
 * session. GA's `signed_in` user property follows (`yes` only with consent, analytics-v1.md).
 */
export function AuthProvider({ children, signInFooter }: { children: React.ReactNode; signInFooter?: React.ReactNode }) {
  const [state, setState] = useState<State>({ status: "loading", consent: undefined });
  const [sheet, setSheet] = useState<"none" | "signIn" | "consent">("none");
  const [notice, setNotice] = useState<Notice>();
  /** Saved cards in this browser the person may move into the account; 0 hides the question. */
  const [moveOffer, setMoveOffer] = useState(0);
  const [moving, setMoving] = useState(false);
  const pending = useRef<(() => void) | null>(null);
  const askedOnLoad = useRef(false);
  const consentRequired = state.status === "signedIn" && state.consent?.consentRequired === true;

  const apply = useCallback((result: Session): State => {
    const next: State = result.signedIn ? { status: "signedIn", consent: result.consent ?? null } : signedOut;
    setState(next);
    // Asked once per page load while required (the apps ask at launch and on return to the foreground).
    if (next.consent?.consentRequired && !askedOnLoad.current) {
      askedOnLoad.current = true;
      setSheet("consent");
    }
    return next;
  }, []);
  const load = useCallback(() => session().then(apply, () => apply({ signedIn: false })), [apply]);
  useEffect(() => {
    load();
  }, [load]);
  useEffect(() => {
    if (state.status !== "loading") setSignedIn(state.status === "signedIn" && state.consent?.consentRequired === false);
  }, [state]);
  const [epoch, setEpoch] = useState(0);
  const status = useRef<Status>("loading");
  useEffect(() => {
    status.current = state.status;
  }, [state.status]);

  const notify = useCallback((text: string) => setNotice({ id: Date.now(), text }), []);
  /**
   * Signed in with consent: run (and await) what the person was doing, move `epoch` so public reads reload with the account, then ask
   * about the browser's saved cards.
   */
  const ready = useCallback(async () => {
    const action = pending.current;
    pending.current = null;
    try {
      await action?.();
    } catch {
      // The action reports its own failure.
    }
    setEpoch((value) => value + 1);
    guestSavedCount()
      .then((count) => setMoveOffer(count))
      .catch(() => {});
  }, []);
  async function move() {
    setMoving(true);
    try {
      const { imported } = await importGuest();
      await clearGuestSession().catch(() => {});
      notify(imported > 0 ? `저장해 둔 명함 ${imported}개를 계정으로 옮겼어요` : "옮길 명함이 없었어요.");
    } catch {
      notify("명함을 옮기지 못했어요. 잠시 후 설정에서 다시 시도해 주세요.");
    } finally {
      setMoving(false);
      setMoveOffer(0);
    }
  }
  const signedInNow = useCallback(async () => {
    setSheet("none");
    const next = await load();
    if (next.status !== "signedIn") return;
    if (next.consent?.consentRequired) setSheet("consent");
    else if (next.consent) ready();
  }, [load, ready]);
  const consented = useCallback(async () => {
    setSheet("none");
    const next = await load();
    if (next.status === "signedIn" && next.consent?.consentRequired === false) ready();
  }, [load, ready]);
  const signedOutLocally = useCallback((message?: string) => {
    pending.current = null;
    setSheet("none");
    setMoveOffer(0);
    if (status.current === "signedIn") setEpoch((value) => value + 1);
    setState(signedOut);
    if (message) setNotice({ id: Date.now(), text: message });
  }, []);
  const signOut = useCallback(async () => {
    try {
      await logout();
    } catch {
      // The server clears the cookies either way; the session is gone for this browser.
    }
    signedOutLocally();
  }, [signedOutLocally]);
  const requireAccount = useCallback(
    (action: () => void | Promise<void>) => {
      if (state.status === "signedIn" && state.consent?.consentRequired === false) {
        void action();
        return;
      }
      pending.current = action;
      setSheet(state.status === "signedIn" && state.consent?.consentRequired ? "consent" : "signIn");
    },
    [state],
  );
  const value: Auth = {
    status: state.status,
    signedIn: state.status === "signedIn",
    consentRequired,
    openSignIn: () => setSheet("signIn"),
    openConsent: () => setSheet("consent"),
    requireAccount,
    signOut,
    signedOutLocally,
    epoch,
    notify,
  };
  return (
    <AuthContext value={value}>
      {children}
      <SignInSheet open={sheet === "signIn"} onOpenChange={(open) => setSheet(open ? "signIn" : "none")} onSignedIn={signedInNow} footer={signInFooter} />
      <ConsentSheet
        open={sheet === "consent"}
        onOpenChange={(open) => setSheet(open ? "consent" : "none")}
        consent={state.consent}
        onConsented={consented}
        onLeft={signedOutLocally}
      />
      <Dialog open={moveOffer > 0} onOpenChange={(open) => !open && !moving && setMoveOffer(0)}>
        <DialogContent>
          <DialogTitle>브라우저에 저장한 명함 {moveOffer}개를 계정으로 옮길까요?</DialogTitle>
          <DialogDescription>옮긴 명함은 이 계정의 받은 명함이 되고, 브라우저 목록에서는 사라져요.</DialogDescription>
          <DialogActions>
            <DialogClose className="text-body text-teal" disabled={moving}>
              나중에
            </DialogClose>
            <button type="button" className="text-body font-semibold text-teal" disabled={moving} onClick={move}>
              옮기기
            </button>
          </DialogActions>
        </DialogContent>
      </Dialog>
      <Toast notice={notice} />
    </AuthContext>
  );
}

export function useAuth(): Auth {
  const value = useContext(AuthContext);
  if (!value) throw new Error("useAuth needs AuthProvider");
  return value;
}
