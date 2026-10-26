"use client";
import { useEffect, useState } from "react";
import { RequestError } from "@/lib/client";
import type { Profile } from "@/lib/account-models";
import { CONTACT_KINDS, type ContactKind } from "@/lib/models";
import { track } from "@/shared/lib/analytics";
import { Button } from "@/shared/ui/button";
import { Icon } from "@/shared/ui/icon";
import { Input } from "@/shared/ui/input";
import { emailChallenge, emailVerify, githubStart } from "../api/profile-client";
import { CONTACT_KIND_NAME, type ContactForm, newId, sanitizeUsername, USERNAME_INPUT } from "../model/form";

type EmailState = { challengeId: string; sentAt: number; code: string; busy?: boolean; error?: string };
const RESEND_SECONDS = 60;

/**
 * 연락처 입력 (contract "연락처 아이콘·바로가기" › 사용자명 입력, "연락처 인증" › 화면): kind · label · value rows; username kinds show the
 * service prefix and keep only allowed characters; email rows offer `인증번호 받기` → 6-digit code, GitHub rows `GitHub으로 연결`
 * (same tab, back to `/profile?code=`). A verified row shows the seal and warns that changing the value drops it.
 */
export function ContactFields({
  contacts,
  loaded,
  canVerify,
  onChange,
  onVerified,
  onNotice,
}: {
  contacts: ContactForm[];
  /** Values as loaded from the server, by contact id: a verified row that differs from this is about to lose its mark. */
  loaded: Record<string, string>;
  /** Profile saved at least once (version > 0); otherwise the API answers 400 `PROFILE_REQUIRED`. */
  canVerify: boolean;
  onChange: (contacts: ContactForm[]) => void;
  /** A verification succeeded: the new Profile (version + that kind's rows) is absorbed by the form owner. */
  onVerified: (profile: Profile, kind: "email" | "github") => void;
  onNotice: (text: string) => void;
}) {
  const [email, setEmail] = useState<Record<string, EmailState>>({});
  const [hidden, setHidden] = useState<{ email?: boolean; github?: boolean }>({});
  const [githubBusy, setGithubBusy] = useState(false);
  const [now, setNow] = useState(0);
  // The resend countdown re-renders once a second while a challenge is open.
  useEffect(() => {
    if (!Object.keys(email).length) return;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [email]);

  const update = (index: number, patch: Partial<ContactForm>) => onChange(contacts.map((contact, at) => (at === index ? { ...contact, ...patch } : contact)));
  const setEmailState = (id: string, state: EmailState | undefined) =>
    setEmail((all) => {
      const next = { ...all };
      if (state) next[id] = state;
      else delete next[id];
      return next;
    });

  async function sendCode(contact: ContactForm) {
    const value = contact.value.trim();
    setEmailState(contact.id, { challengeId: "", sentAt: Date.now(), code: "", busy: true });
    try {
      const { challengeId } = await emailChallenge(value);
      const sentAt = Date.now();
      setNow(sentAt);
      setEmailState(contact.id, { challengeId, sentAt, code: "" });
    } catch (error) {
      setEmailState(contact.id, undefined);
      if (error instanceof RequestError && error.status === 503) {
        setHidden((value) => ({ ...value, email: true }));
        onNotice("지금은 이메일 인증을 쓸 수 없어요. 값은 그대로 저장돼요.");
      } else onNotice(error instanceof RequestError && error.detail ? error.detail : "인증번호를 보내지 못했어요. 잠시 후 다시 시도해 주세요.");
    }
  }
  async function verify(contact: ContactForm) {
    const state = email[contact.id];
    if (!state || !/^\d{6}$/.test(state.code)) return;
    setEmailState(contact.id, { ...state, busy: true, error: undefined });
    try {
      const profile = await emailVerify(state.challengeId, state.code);
      setEmailState(contact.id, undefined);
      track("contact_verify", { kind: "email", result: "success" });
      onVerified(profile, "email");
    } catch (error) {
      track("contact_verify", { kind: "email", result: "failed" });
      if (error instanceof RequestError && error.code === "CHALLENGE_EXPIRED") {
        setEmailState(contact.id, undefined);
        onNotice(error.detail ?? "인증번호가 만료됐어요. 다시 받아 주세요.");
      } else setEmailState(contact.id, { ...state, busy: false, error: error instanceof RequestError && error.detail ? error.detail : "인증번호를 확인하지 못했어요." });
    }
  }
  async function connectGithub() {
    setGithubBusy(true);
    try {
      const { authorizeUrl } = await githubStart();
      window.location.assign(authorizeUrl);
    } catch (error) {
      setGithubBusy(false);
      if (error instanceof RequestError && error.status === 503) {
        setHidden((value) => ({ ...value, github: true }));
        onNotice("지금은 GitHub 연결을 쓸 수 없어요. 사용자명을 직접 적어 주세요.");
      } else onNotice(error instanceof RequestError && error.detail ? error.detail : "GitHub 연결을 시작하지 못했어요.");
    }
  }

  return (
    <div className="flex flex-col gap-4">
      {contacts.map((contact, index) => {
        const rule = USERNAME_INPUT[contact.kind];
        const changed = contact.verified && loaded[contact.id] !== undefined && loaded[contact.id] !== contact.value.trim();
        const state = email[contact.id];
        const secondsLeft = state ? Math.max(0, RESEND_SECONDS - Math.floor((Math.max(now, state.sentAt) - state.sentAt) / 1000)) : 0;
        const verifiable = canVerify && (contact.kind === "email" || contact.kind === "github") && !hidden[contact.kind] && !(contact.verified && !changed);
        return (
          <div key={contact.id} className="flex flex-col gap-2 rounded-control bg-fill/50 p-3">
            <div className="flex items-center gap-2">
              <label className="flex flex-1 flex-col gap-1">
                <span className="text-footnote text-secondary-label">종류</span>
                <select
                  value={contact.kind}
                  className="h-9 rounded-control bg-fill px-2 text-body text-label outline-none focus-visible:outline-2 focus-visible:outline-teal"
                  onChange={(event) => update(index, { kind: event.target.value as ContactKind, value: sanitizeUsername(event.target.value as ContactKind, contact.value), verified: false })}
                >
                  {CONTACT_KINDS.map((kind) => (
                    <option key={kind} value={kind}>
                      {CONTACT_KIND_NAME[kind]}
                    </option>
                  ))}
                </select>
              </label>
              <label className="flex flex-1 flex-col gap-1">
                <span className="text-footnote text-secondary-label">라벨 (선택)</span>
                <Input value={contact.label} maxLength={60} placeholder="예: 업무용" onChange={(event) => update(index, { label: event.target.value })} />
              </label>
              <button type="button" aria-label={`${CONTACT_KIND_NAME[contact.kind]} 연락처 삭제`} className="mt-5 inline-flex size-9 items-center justify-center text-danger" onClick={() => onChange(contacts.filter((_, at) => at !== index))}>
                <Icon name="trash" />
              </button>
            </div>
            <label className="flex flex-col gap-1">
              <span className="text-footnote text-secondary-label">{contact.kind === "kakao" ? "카카오톡 ID" : rule ? "사용자 이름" : CONTACT_KIND_NAME[contact.kind]}</span>
              <span className="flex items-center gap-1">
                {rule?.prefix && <span className="shrink-0 text-subheadline text-secondary-label">{rule.prefix}</span>}
                <span className="relative flex-1">
                  <Input
                    type={contact.kind === "email" ? "email" : contact.kind === "phone" ? "tel" : "text"}
                    inputMode={contact.kind === "phone" ? "tel" : undefined}
                    autoCapitalize="off"
                    autoCorrect="off"
                    value={contact.value}
                    placeholder={contact.kind === "kakao" ? "카카오톡 ID" : rule ? "사용자 이름" : contact.kind === "phone" ? "010-0000-0000" : "name@example.com"}
                    className={contact.verified && !changed ? "pr-8" : undefined}
                    onChange={(event) => update(index, { value: sanitizeUsername(contact.kind, event.target.value) })}
                  />
                  {contact.verified && !changed && (
                    <span className="pointer-events-none absolute inset-y-0 right-2 inline-flex items-center text-teal" title="인증됨">
                      <Icon name="seal-check" size="inline" />
                      <span className="sr-only">인증됨</span>
                    </span>
                  )}
                </span>
              </span>
            </label>
            {changed && <p className="text-footnote text-secondary-label">값을 바꾸면 인증이 풀려요</p>}
            {verifiable && contact.kind === "email" && !state && (
              <Button variant="tinted" size="sm" className="self-start" disabled={!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(contact.value.trim())} onClick={() => sendCode(contact)}>
                인증번호 받기
              </Button>
            )}
            {verifiable && contact.kind === "email" && state && (
              <div className="flex flex-col gap-2">
                <div className="flex items-center gap-2">
                  <Input
                    inputMode="numeric"
                    autoComplete="one-time-code"
                    pattern="\d{6}"
                    maxLength={6}
                    placeholder="인증번호 6자리"
                    aria-label="이메일 인증번호"
                    value={state.code}
                    className="w-40"
                    onChange={(event) => setEmailState(contact.id, { ...state, code: event.target.value.replace(/\D/g, "").slice(0, 6), error: undefined })}
                  />
                  <Button size="sm" disabled={state.busy || !state.challengeId || state.code.length !== 6} onClick={() => verify(contact)}>
                    확인
                  </Button>
                  <Button variant="plain" size="sm" disabled={state.busy || secondsLeft > 0} onClick={() => sendCode(contact)}>
                    {secondsLeft > 0 ? `다시 받기 (${secondsLeft}초)` : "다시 받기"}
                  </Button>
                </div>
                {state.error && (
                  <p role="alert" className="text-footnote text-danger">
                    {state.error}
                  </p>
                )}
                {!state.error && state.challengeId && <p className="text-footnote text-secondary-label">메일로 보낸 인증번호를 10분 안에 입력해 주세요.</p>}
              </div>
            )}
            {verifiable && contact.kind === "github" && (
              <Button variant="tinted" size="sm" className="self-start" disabled={githubBusy} onClick={connectGithub}>
                <Icon name="github" size="inline" />
                {githubBusy ? "GitHub으로 이동 중…" : "GitHub으로 연결"}
              </Button>
            )}
            {!canVerify && (contact.kind === "email" || contact.kind === "github") && <p className="text-footnote text-secondary-label">프로필을 먼저 저장하면 인증할 수 있어요</p>}
          </div>
        );
      })}
      <button type="button" className="flex min-h-11 items-center gap-2 text-body text-teal" onClick={() => onChange([...contacts, { id: newId(), kind: contacts.some((c) => c.kind === "phone") ? "email" : "phone", label: "", value: "", verified: false }])}>
        <Icon name="plus" size="inline" />
        연락처 추가
      </button>
      <p className="text-footnote text-secondary-label">전화와 이메일은 하나 이상 필요해요. GitHub·Instagram·Behance·카카오톡은 사용자명만 적으면 링크를 만들어요.</p>
    </div>
  );
}
