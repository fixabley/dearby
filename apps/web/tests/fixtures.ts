import type { Card, Catalog } from "../src/lib/models";
export const cardId = "11111111-1111-4111-8111-111111111111";
export const secondCardId = "22222222-2222-4222-8222-222222222222";
export const revokedId = "33333333-3333-4333-8333-333333333333";
export const activityId = "44444444-4444-4444-8444-444444444444";
export const card: Card = {
  id: cardId,
  name: "함께 일하는 연결",
  description: "만나서 반가워요.",
  profileName: "테스트 지민",
  job: "서비스 기획자",
  introduction: "사람을 연결하는 경험을 만듭니다.",
  createdAt: "2026-09-29T00:00:00Z",
  contacts: [
    {
      id: secondCardId,
      kind: "email",
      label: "이메일",
      value: "public@example.test",
    },
    {
      id: revokedId,
      kind: "github",
      label: "GitHub",
      value: "https://github.com/example",
    },
  ],
  histories: [
    {
      id: activityId,
      title: "커넥트 IT 컨퍼런스 (테스트)",
      role: "운영 스태프",
      startDate: "2026-09-01",
      endDate: null,
      description: "참여 경험을 설계했어요.",
    },
  ],
};
export function catalog(now = Date.now()): Catalog {
  return {
    generatedAt: new Date(now).toISOString(),
    organizations: [
      { id: cardId, name: "테스트 커넥트", description: "테스트 전용" },
    ],
    programs: [
      {
        id: secondCardId,
        organizationId: cardId,
        title: "테스트 프로그램",
        description: "",
      },
    ],
    activities: [
      {
        id: activityId,
        organizationId: cardId,
        programId: secondCardId,
        title: "커넥트 IT 컨퍼런스 (테스트)",
        summary: "IT에 관심 있는 누구나 함께하는 기술과 경험의 자리",
        participationType: "registration",
        recruitmentStatus: "open",
        isRecruiting: true,
        recruitmentStartAt: null,
        recruitmentEndAt: new Date(now + 3600000).toISOString(),
        dateLabel: "10월 18일 · 10:00–18:00",
        location: "서울",
        cost: "무료",
        audience: "IT에 관심 있는 누구나",
        qualification: null,
        roles: [],
        schedules: [],
        officialUrl: "https://example.test/source",
        applicationUrl: "https://example.test/apply",
        sourceCheckedAt: new Date(now - 1000).toISOString(),
        validUntil: new Date(now + 3600000).toISOString(),
        freshness: "verified",
        sourceNote: "테스트 환경 전용 데이터입니다.",
      },
    ],
  };
}
