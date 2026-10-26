"use client";
import { useState } from "react";
import { useAuth } from "@/features/auth/model/auth-context";
import { errorMessage, RequestError } from "@/lib/client";
import { type Profile, profilePhotoSrc } from "@/lib/account-models";
import { Button } from "@/shared/ui/button";
import { Card } from "@/shared/ui/card";
import { Input } from "@/shared/ui/input";
import { putProfile } from "../api/profile-client";
import { fromProfile, type ProfileForm, toRequest, validate } from "../model/form";
import { ContactFields } from "./contact-fields";
import { HistoryFields } from "./history-fields";
import { JobFields } from "./job-fields";
import { PhotoField } from "./photo-field";

/**
 * 수정 모드 (contract "프로필 읽기/수정 모드·상단 바 메뉴" › 수정 모드, "소개 링크·소개 글 폐지" › 입력): 사진 → 이름 → 소개 링크 → 직무 →
 * 연락처 → 활동 이력, with `저장`·`원래대로` under it. `저장` is `PUT /v1/profile` with `If-Match: <version>`; 409 asks to reload, 400 shows the
 * API's sentence, 403 `CONSENT_REQUIRED` opens the consent sheet. A contact verification answers a full Profile: only its `version`
 * and that kind's rows are absorbed so other edits survive.
 */
export function ProfileEditor({
  profile,
  onSaved,
  onCancel,
  onReload,
  onPhotoChanged,
  onNotice,
}: {
  profile: Profile;
  onSaved: (profile: Profile) => void;
  onCancel: () => void;
  /** Reload the profile from the server (after a 409, or 인증자료 changes) and keep editing. */
  onReload: () => void;
  onPhotoChanged: (photoUrl: string | null) => void;
  onNotice: (text: string) => void;
}) {
  const auth = useAuth();
  const [form, setForm] = useState<ProfileForm>(() => fromProfile(profile));
  const [version, setVersion] = useState(profile.version);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [conflict, setConflict] = useState(false);
  const loadedValues = Object.fromEntries(profile.contacts.map((contact) => [contact.id, fromProfile(profile).contacts.find((c) => c.id === contact.id)?.value ?? contact.value]));
  const saved = Object.fromEntries(profile.histories.map((history) => [history.id, history]));
  const patch = (next: Partial<ProfileForm>) => setForm((value) => ({ ...value, ...next }));

  /** iOS `absorbVerification`: the server changed one kind's rows and bumped the version; everything else in the form stays. */
  function absorb(verified: Profile, kind: "email" | "github") {
    const rows = fromProfile(verified).contacts.filter((contact) => contact.kind === kind);
    setForm((value) => ({ ...value, contacts: [...value.contacts.filter((contact) => contact.kind !== kind), ...rows] }));
    setVersion(verified.version);
    onNotice(kind === "email" ? "이메일을 인증했어요." : "GitHub을 연결했어요.");
  }
  async function save() {
    const problem = validate(form);
    if (problem) return setError(problem);
    setBusy(true);
    setError("");
    setConflict(false);
    try {
      const next = await putProfile(toRequest(form), version);
      onSaved(next);
    } catch (caught) {
      if (caught instanceof RequestError && caught.status === 409) setConflict(true);
      else if (caught instanceof RequestError && caught.code === "CONSENT_REQUIRED") auth.openConsent();
      else if (caught instanceof RequestError && caught.status === 401) auth.signedOutLocally("로그인이 만료됐어요. 다시 로그인해 주세요.");
      else setError(caught instanceof RequestError && caught.detail ? caught.detail : errorMessage(caught));
    } finally {
      setBusy(false);
    }
  }
  return (
    <form
      className="mx-auto mt-3 flex max-w-[560px] flex-col gap-6 pb-28"
      onSubmit={(event) => {
        event.preventDefault();
        save();
      }}
    >
      <Card className="flex flex-col gap-5 p-5">
        <Section title="사진">
          <PhotoField src={profilePhotoSrc(profile.photoUrl)} name={form.name} onChanged={onPhotoChanged} onNotice={onNotice} />
        </Section>
        <Section title="이름">
          <Input value={form.name} maxLength={100} required autoComplete="name" placeholder="이름" onChange={(event) => patch({ name: event.target.value })} />
        </Section>
        <Section title="소개 링크 (선택)" hint="노션·포트폴리오 같은 https 주소 하나. 이름 옆에 작은 링크로 보여요.">
          <Input type="url" inputMode="url" autoCapitalize="off" autoCorrect="off" value={form.introLink} maxLength={500} placeholder="notion.so/…" onChange={(event) => patch({ introLink: event.target.value })} />
        </Section>
      </Card>
      <Card className="flex flex-col gap-5 p-5">
        <Section title="직무">
          <JobFields jobs={form.jobs} onChange={(jobs) => patch({ jobs })} />
        </Section>
      </Card>
      <Card className="flex flex-col gap-5 p-5">
        <Section title="연락처">
          <ContactFields contacts={form.contacts} loaded={loadedValues} canVerify={version > 0} onChange={(contacts) => patch({ contacts })} onVerified={absorb} onNotice={onNotice} />
        </Section>
      </Card>
      <Card className="flex flex-col gap-5 p-5">
        <Section title="활동 이력">
          <HistoryFields histories={form.histories} saved={saved} onChange={(histories) => patch({ histories })} onEvidenceChanged={onReload} />
        </Section>
      </Card>
      {conflict && (
        <p role="alert" className="flex flex-wrap items-center justify-between gap-2 rounded-control bg-fill px-4 py-3 text-footnote text-danger">
          다른 곳에서 프로필이 바뀌었어요. 다시 불러온 뒤 고쳐 주세요
          <Button variant="plain" size="sm" className="px-0" onClick={onReload}>
            다시 불러오기
          </Button>
        </p>
      )}
      {error && (
        <p role="alert" className="px-1 text-footnote text-danger">
          {error}
        </p>
      )}
      <div className="fixed inset-x-0 bottom-0 z-30 border-t border-separator bg-bar backdrop-blur-xl backdrop-saturate-200">
        <div className="mx-auto flex max-w-[720px] gap-2 px-4 pt-3 pb-[max(12px,env(safe-area-inset-bottom))]">
          <Button type="button" variant="tinted" size="lg" className="flex-1" disabled={busy} onClick={onCancel}>
            원래대로
          </Button>
          <Button type="submit" size="lg" className="flex-1" disabled={busy}>
            {busy ? "저장 중…" : "저장"}
          </Button>
        </div>
      </div>
    </form>
  );
}

function Section({ title, hint, children }: { title: string; hint?: string; children: React.ReactNode }) {
  return (
    <section className="flex flex-col gap-2">
      <h2 className="text-subheadline font-semibold text-secondary-label">{title}</h2>
      {children}
      {hint && <p className="text-footnote text-secondary-label">{hint}</p>}
    </section>
  );
}
