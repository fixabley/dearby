"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import type { EligibilityProfile, Profile, RecommendationPreferences } from "@/lib/account-models";
import { categoryLabels } from "@/lib/category";
import { Button } from "@/shared/ui/button";
import { Icon } from "@/shared/ui/icon";
import { InsetGroup } from "@/shared/ui/inset-group";
import { PublicCardBody } from "@/widgets/card/public-card-body";
import { eligibilityProfile, recommendationPreferences } from "../api/profile-client";
import { eligibilitySummary, profileToCard } from "../model/form";

/**
 * 보기 모드 (contract "프로필 읽기/수정 모드·상단 바 메뉴" › 보기 모드 = 명함 디자인): the card face with photo·name·job·contacts and the
 * 활동 이력 rows (same component as a card), then `이 내용으로 명함 만들기` and `수정`, then the read-only `추천` group
 * (contract "추천 설정 퍼널·지원 자격 정보·추천" — values are edited in the apps and never sent to analytics).
 */
export function ProfileView({ profile, photoSrc, onEdit }: { profile: Profile; photoSrc: string | undefined; onEdit: () => void }) {
  return (
    <>
      <div className="mx-auto mt-3 max-w-[480px]">
        <PublicCardBody card={profileToCard(profile, photoSrc)} />
        <div className="mt-4 flex flex-col gap-2">
          <Button asChild size="lg">
            <Link href="/cards/mine?new=1">
              <Icon name="card" size="inline" />
              이 내용으로 명함 만들기
            </Link>
          </Button>
          <Button size="lg" variant="tinted" onClick={onEdit}>
            <Icon name="pencil" size="inline" />
            수정
          </Button>
        </div>
      </div>
      <Recommendation />
    </>
  );
}

function Recommendation() {
  const [preferences, setPreferences] = useState<RecommendationPreferences | null>();
  const [eligibility, setEligibility] = useState<EligibilityProfile | null>();
  useEffect(() => {
    let cancelled = false;
    recommendationPreferences().then(
      (value) => !cancelled && setPreferences(value),
      () => !cancelled && setPreferences(null),
    );
    eligibilityProfile().then(
      (value) => !cancelled && setEligibility(value),
      () => !cancelled && setEligibility(null),
    );
    return () => {
      cancelled = true;
    };
  }, []);
  const kinds = preferences?.categories.map((code) => categoryLabels[code] ?? code).filter(Boolean) ?? [];
  const interests =
    preferences === undefined ? "" : preferences?.completedAt ? [kinds.length ? kinds.join(" · ") : undefined, preferences.fieldTagIds.length ? `관심 분야 ${preferences.fieldTagIds.length}개` : undefined].filter(Boolean).join(" · ") || "선택 없음" : "앱에서 설정해요";
  const summary = eligibility === undefined ? "" : eligibility ? (eligibilitySummary(eligibility) ?? "입력 안 함") : "입력 안 함";
  return (
    <InsetGroup title="추천" footer="추천 설정과 지원 자격 정보는 앱에서 고쳐요.">
      <Row label="관심 분야·활동 종류" value={interests} />
      <Row label="지원 자격 정보" value={summary} />
    </InsetGroup>
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
