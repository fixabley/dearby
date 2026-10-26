"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { RequestError, errorMessage } from "@/lib/client";
import { contactUsername } from "@/lib/models";
import type { MyCard, Profile, ProfileHistory } from "@/lib/account-models";
import { useAuth } from "@/features/auth/model/auth-context";
import { Badge } from "@/shared/ui/badge";
import { Button } from "@/shared/ui/button";
import { CheckRow } from "@/shared/ui/check-row";
import { Icon } from "@/shared/ui/icon";
import { Input } from "@/shared/ui/input";
import { Sheet, SheetClose, SheetContent, SheetDescription, SheetTitle } from "@/shared/ui/sheet";
import { Skeleton, SkeletonGroup } from "@/shared/ui/skeleton";
import { myProfile, publishCard, updateCard } from "../api/cards-client";

/**
 * 명함 만들기·고치기 시트 (contract "명함·지갑", "명함 고치기는 같은 명함을 수정", "프로필 사진" › 명함에 싣기, "소개 링크" › 명함):
 * name, description, the profile contacts and approved histories to publish, `사진 넣기`, `소개 링크 넣기`. `card` set means
 * `PUT /v1/cards/:id` (same id, same link); otherwise `POST /v1/cards`. Templates are gone (2026-10-25).
 */
export function CardEditor({ card, onClose, onSaved }: { card?: MyCard; onClose: () => void; onSaved: (card: MyCard) => void }) {
  const auth = useAuth();
  const [profile, setProfile] = useState<Profile>();
  const [loadError, setLoadError] = useState("");
  const [name, setName] = useState(card?.name ?? "");
  const [description, setDescription] = useState(card?.description ?? "");
  const [contactIds, setContactIds] = useState<Set<string>>(new Set(card?.contacts.map((contact) => contact.id)));
  const [historyIds, setHistoryIds] = useState<Set<string>>(new Set(card?.histories.map((history) => history.id)));
  const [includePhoto, setIncludePhoto] = useState(card ? !!card.photoUrl : true);
  const [includeIntroLink, setIncludeIntroLink] = useState(card ? !!card.introLink : true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  useEffect(() => {
    let cancelled = false;
    myProfile().then(
      (found) => {
        if (cancelled) return;
        setProfile(found);
        // 고치기: 프로필에 아직 있는 연락처·이력만 선택으로 남긴다. 새 명함: 연락처는 모두, 승인된 이력은 모두 고른 채 시작한다.
        const contacts = new Set(found.contacts.map((contact) => contact.id));
        const approved = new Set(found.histories.filter((history) => history.approval === "approved").map((history) => history.id));
        setContactIds((chosen) => (card ? new Set([...chosen].filter((id) => contacts.has(id))) : contacts));
        setHistoryIds((chosen) => (card ? new Set([...chosen].filter((id) => approved.has(id))) : approved));
      },
      (caught) => {
        if (cancelled) return;
        if (caught instanceof RequestError && caught.status === 401) auth.signedOutLocally("로그인이 만료됐어요. 다시 로그인해 주세요.");
        else setLoadError(errorMessage(caught));
      },
    );
    return () => {
      cancelled = true;
    };
    // The profile is read once when the sheet opens.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  const toggle = (set: typeof setContactIds) => (id: string, checked: boolean) =>
    set((ids) => {
      const next = new Set(ids);
      if (checked) next.add(id);
      else next.delete(id);
      return next;
    });
  const trimmed = name.trim();
  async function submit() {
    if (!trimmed) return;
    setBusy(true);
    setError("");
    const body = {
      name: trimmed,
      description: description.trim(),
      contactIds: [...contactIds],
      historyIds: [...historyIds],
      includePhoto,
      includeIntroLink,
    };
    try {
      onSaved(card ? await updateCard(card.id, body) : await publishCard(body));
    } catch (caught) {
      if (caught instanceof RequestError && caught.status === 401) auth.signedOutLocally("로그인이 만료됐어요. 다시 로그인해 주세요.");
      else if (caught instanceof RequestError && caught.code === "CONSENT_REQUIRED") auth.openConsent();
      else if (caught instanceof RequestError && caught.status === 404) setError("이미 지운 명함이에요.");
      else setError(caught instanceof RequestError && caught.detail ? caught.detail : errorMessage(caught));
    } finally {
      setBusy(false);
    }
  }
  const field = "flex flex-col gap-1 text-footnote text-secondary-label";
  return (
    <Sheet open onOpenChange={(open) => !open && !busy && onClose()}>
      <SheetContent aria-describedby={undefined} className="max-h-[92dvh] overflow-y-auto">
        <div className="grid grid-cols-[1fr_auto_1fr] items-center">
          <span />
          <SheetTitle className="text-center">{card ? "명함 고치기" : "새 명함"}</SheetTitle>
          <SheetClose className="-mr-2 inline-flex min-h-11 items-center justify-self-end px-2 text-body text-teal" disabled={busy}>
            닫기
          </SheetClose>
        </div>
        {loadError ? (
          <p role="alert" className="py-6 text-center text-footnote text-danger">
            {loadError}
          </p>
        ) : !profile ? (
          <SkeletonGroup className="flex flex-col gap-3 py-4">
            <Skeleton className="h-9" />
            <Skeleton className="h-9" />
            <Skeleton className="h-24" />
          </SkeletonGroup>
        ) : profile.version === 0 ? (
          <div className="flex flex-col items-center gap-3 py-8 text-center">
            <SheetDescription>프로필을 먼저 만들어 주세요</SheetDescription>
            <p className="text-footnote text-secondary-label">명함은 프로필의 이름·연락처·활동 이력에서 골라 만들어요.</p>
            <Button asChild variant="tinted">
              <Link href="/profile">프로필로 가기</Link>
            </Button>
          </div>
        ) : (
          <form
            className="flex flex-col gap-5 pt-3"
            onSubmit={(event) => {
              event.preventDefault();
              submit();
            }}
          >
            <label className={field}>
              명함 이름
              <Input value={name} onChange={(event) => setName(event.target.value)} maxLength={60} required placeholder="예: 우아콘 2026" autoFocus />
            </label>
            <label className={field}>
              설명 (선택)
              <Input value={description} onChange={(event) => setDescription(event.target.value)} maxLength={200} placeholder="나만 보는 메모" />
            </label>
            <fieldset className="flex flex-col gap-1">
              <legend className="mb-1 text-footnote text-secondary-label">공개할 연락처</legend>
              {profile.contacts.length === 0 && <p className="px-4 py-2 text-subheadline text-secondary-label">프로필에 연락처가 없어요.</p>}
              <ul className="divide-y divide-separator overflow-hidden rounded-card bg-fill/40">
                {profile.contacts.map((contact) => (
                  <li key={contact.id}>
                    <CheckRow checked={contactIds.has(contact.id)} onChange={(checked) => toggle(setContactIds)(contact.id, checked)}>
                      <span className="flex items-center gap-2">
                        <Icon name={contact.kind === "email" ? "mail" : contact.kind} tone="teal" />
                        <span className="break-all">{contactUsername(contact) ?? contact.value}</span>
                        {contact.verified && (
                          <span className="inline-flex text-teal">
                            <Icon name="seal-check" size="inline" />
                            <span className="sr-only">인증됨</span>
                          </span>
                        )}
                      </span>
                    </CheckRow>
                  </li>
                ))}
              </ul>
            </fieldset>
            <fieldset className="flex flex-col gap-1">
              <legend className="mb-1 text-footnote text-secondary-label">공개할 활동 이력</legend>
              {profile.histories.length === 0 && <p className="px-4 py-2 text-subheadline text-secondary-label">프로필에 활동 이력이 없어요.</p>}
              <ul className="divide-y divide-separator overflow-hidden rounded-card bg-fill/40">
                {profile.histories.map((history) => (
                  <li key={history.id}>
                    <CheckRow
                      checked={historyIds.has(history.id)}
                      disabled={history.approval !== "approved"}
                      onChange={(checked) => toggle(setHistoryIds)(history.id, checked)}
                    >
                      <span className="flex flex-col gap-0.5">
                        <span className="text-subheadline font-semibold">{chain(history).join(" · ")}</span>
                        <span className="flex flex-wrap items-center gap-2 text-footnote text-secondary-label">
                          {history.role?.name ?? history.roleName}
                          {history.approval === "pending" && <Badge>승인 대기</Badge>}
                          {history.approval === "rejected" && <Badge className="text-danger">거절</Badge>}
                        </span>
                      </span>
                    </CheckRow>
                  </li>
                ))}
              </ul>
              <p className="px-4 text-caption text-secondary-label">직접 입력한 이력은 관리자가 승인한 뒤 명함에 넣을 수 있어요.</p>
            </fieldset>
            <div className="divide-y divide-separator overflow-hidden rounded-card bg-fill/40">
              {profile.photoUrl ? (
                <CheckRow checked={includePhoto} onChange={setIncludePhoto}>
                  사진 넣기
                </CheckRow>
              ) : (
                <p className="flex min-h-11 items-center px-4 text-subheadline text-secondary-label">프로필에서 사진을 넣을 수 있어요</p>
              )}
              {profile.introLink && (
                <CheckRow checked={includeIntroLink} onChange={setIncludeIntroLink}>
                  소개 링크 넣기
                </CheckRow>
              )}
            </div>
            {error && (
              <p role="alert" className="text-footnote text-danger">
                {error}
              </p>
            )}
            <Button type="submit" size="lg" disabled={busy || !trimmed}>
              {busy ? "저장 중…" : card ? "고친 내용 저장" : "새 명함 만들기"}
            </Button>
          </form>
        )}
      </SheetContent>
    </Sheet>
  );
}

/** 조직 → 프로그램 → 활동 이름(카탈로그 또는 직접 입력). */
function chain(history: ProfileHistory): string[] {
  return [history.organization?.name ?? history.organizationName, history.program?.name ?? history.programName, history.activity?.name ?? history.activityName].filter(
    (value): value is string => !!value,
  );
}
