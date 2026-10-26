"use client";
import { useCallback, useEffect, useState } from "react";
import { useAuth } from "@/features/auth/model/auth-context";
import { errorMessage, RequestError } from "@/lib/client";
import { type Profile, profilePhotoSrc } from "@/lib/account-models";
import { track } from "@/shared/lib/analytics";
import { CenteredMessage } from "@/shared/ui/centered-message";
import { SkeletonGroup } from "@/shared/ui/skeleton";
import { Failure } from "@/shared/ui/states";
import { PublicCardBodySkeleton } from "@/widgets/card/public-card-body";
import { getProfile, githubComplete } from "../api/profile-client";
import { ProfileEditor } from "./profile-editor";
import { ProfileView } from "./profile-view";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const EXPIRED_SESSION = "로그인이 만료됐어요. 다시 로그인해 주세요.";

/**
 * `/profile` for a signed-in, consented person (contract "웹 화면 — 2단계 C"): loads `GET /v1/profile`, shows the card-shaped view
 * (edit on `수정`), or the editor straight away for a profile never saved (version 0). Also the landing of the GitHub connection:
 * `/profile?code=<uuid>` completes it (`POST …/github/complete`) and the query is dropped from the address; `?error=cancelled`
 * is silent, `?error=failed` says so.
 */
export function ProfilePage() {
  const auth = useAuth();
  const [profile, setProfile] = useState<Profile>();
  const [error, setError] = useState("");
  const [editing, setEditing] = useState(false);
  const [notice, setNotice] = useState("");
  const [attempt, setAttempt] = useState(0);
  const reload = useCallback(() => setAttempt((value) => value + 1), []);
  const expired = useCallback(() => auth.signedOutLocally(EXPIRED_SESSION), [auth]);

  useEffect(() => {
    let cancelled = false;
    getProfile().then(
      (found) => {
        if (cancelled) return;
        setProfile(found);
        if (found.version === 0) setEditing(true);
      },
      (caught) => {
        if (cancelled) return;
        if (caught instanceof RequestError && caught.status === 401) expired();
        else setError(errorMessage(caught));
      },
    );
    return () => {
      cancelled = true;
    };
  }, [attempt, expired]);

  // GitHub comes back to this page (contract "연락처 인증" › callback, web): finish with the one-time code, then clean the address.
  useEffect(() => {
    const url = new URL(window.location.href);
    const code = url.searchParams.get("code");
    const failure = url.searchParams.get("error");
    if (code === null && failure === null) return;
    url.searchParams.delete("code");
    url.searchParams.delete("error");
    history.replaceState(history.state, "", url);
    if (failure === "cancelled") {
      track("contact_verify", { kind: "github", result: "cancelled" });
      return;
    }
    if (failure !== null || !code || !UUID.test(code)) {
      track("contact_verify", { kind: "github", result: "failed" });
      const timer = setTimeout(() => setNotice("GitHub 연결에 실패했어요. 다시 시도해 주세요"), 0);
      return () => clearTimeout(timer);
    }
    githubComplete(code).then(
      (found) => {
        track("contact_verify", { kind: "github", result: "success" });
        setProfile(found);
        setNotice("GitHub을 연결했어요.");
      },
      (caught) => {
        track("contact_verify", { kind: "github", result: "failed" });
        if (caught instanceof RequestError && caught.status === 401) expired();
        else setNotice("GitHub 연결에 실패했어요. 다시 시도해 주세요");
      },
    );
  }, [expired]);

  if (error)
    return (
      <Failure
        message={error}
        retry={() => {
          setError("");
          reload();
        }}
      />
    );
  return (
    <>
      <p role="status" className="px-4 text-footnote text-teal [&:not(:empty)]:pt-2">
        {notice}
      </p>
      {!profile ? (
        <SkeletonGroup className="mx-auto mt-3 max-w-[480px]">
          <PublicCardBodySkeleton />
        </SkeletonGroup>
      ) : editing ? (
        <ProfileEditor
          key={`${profile.version}-${attempt}`}
          profile={profile}
          onSaved={(saved) => {
            setProfile(saved);
            setEditing(false);
            setNotice("프로필을 저장했어요.");
          }}
          onCancel={() => (profile.version === 0 ? setEditing(true) : setEditing(false))}
          onReload={reload}
          onPhotoChanged={(photoUrl) => setProfile((value) => value && { ...value, photoUrl })}
          onNotice={setNotice}
        />
      ) : profile.version === 0 ? (
        <CenteredMessage icon="person" title="프로필을 아직 만들지 않았어요" message="이름·직무·연락처·활동 이력을 적으면 명함을 만들 수 있어요." primary={{ label: "프로필 입력하기", onClick: () => setEditing(true) }} />
      ) : (
        <ProfileView profile={profile} photoSrc={profilePhotoSrc(profile.photoUrl)} onEdit={() => setEditing(true)} />
      )}
    </>
  );
}
