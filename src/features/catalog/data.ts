import { experiences, type Program, type Notice } from "./model";
export const organizations = [
  {
    id: "org-orbit",
    name: "오빗 컬렉티브",
    initial: "O",
    color: "#6451c9",
    description: "작은 아이디어를 함께 만드는 가상의 대학생 커뮤니티입니다.",
  },
  {
    id: "org-layer",
    name: "레이어 랩",
    initial: "L",
    color: "#28644e",
    description: "디자인과 기술의 접점을 탐색하는 가상의 활동 조직입니다.",
  },
  {
    id: "org-next",
    name: "넥스트 테이블",
    initial: "N",
    color: "#b64f31",
    description: "다양한 직무의 이야기를 나누는 가상의 교류 커뮤니티입니다.",
  },
  {
    id: "org-wave",
    name: "웨이브 메이커스",
    initial: "W",
    color: "#2768a7",
    description: "직접 만들고 발표하는 경험을 설계한 가상의 조직입니다.",
  },
];
const rows = [
  [
    "build-together",
    "함께 만드는 첫 번째 서비스",
    "아이디어에서 출시까지, 우리의 8주",
    "프로젝트",
    "서울 · 온라인",
    "프론트엔드,백엔드,디자인",
    "web,peer",
  ],
  [
    "design-sprint",
    "문제를 발견하는 디자인 스프린트",
    "관찰하고, 질문하고, 프로토타입으로",
    "워크숍",
    "서울",
    "디자인,기획",
    "task,mentor",
  ],
  [
    "backend-lab",
    "백엔드, 그 다음 이야기",
    "더 깊이 이해하는 서버의 세계",
    "스터디",
    "온라인",
    "백엔드",
    "lecture,mentor",
  ],
  [
    "app-club",
    "손안의 아이디어, 앱이 되다",
    "팀으로 완성하는 작은 가능성",
    "프로젝트",
    "서울 · 온라인",
    "iOS,디자인",
    "app,presentation",
  ],
  [
    "open-table",
    "현직자와 마주 앉는 저녁",
    "일하는 사람들의 솔직한 대화",
    "네트워킹",
    "서울",
    "기획,디자인,백엔드",
    "mentor,peer",
  ],
  [
    "web-garden",
    "웹을 가꾸는 사람들",
    "매주 한 걸음, 함께 만드는 웹",
    "스터디",
    "온라인",
    "프론트엔드",
    "web",
  ],
  [
    "product-note",
    "기획자의 문제 해결 노트",
    "한 가지 문제를 끝까지 따라가기",
    "워크숍",
    "부산 · 온라인",
    "기획",
    "task,presentation",
  ],
  [
    "demo-day",
    "작은 시도들의 데모데이",
    "완벽하지 않아도, 세상에 보여줘",
    "발표",
    "서울",
    "프론트엔드,iOS,기획",
    "presentation,peer",
  ],
  [
    "career-window",
    "개발자의 하루를 열어보다",
    "업무 속 진짜 이야기를 듣는 시간",
    "강연",
    "온라인",
    "백엔드,프론트엔드",
    "lecture",
  ],
  [
    "design-circle",
    "디자이너의 사이드 프로젝트",
    "함께 만드는 새로운 시선",
    "프로젝트",
    "대전 · 온라인",
    "디자인",
    "peer,web",
  ],
  [
    "mobile-weekend",
    "주말에 만드는 모바일 실험",
    "작게 시작하는 앱 제작 모임",
    "프로젝트",
    "온라인",
    "iOS",
    "app,mentor",
  ],
  [
    "team-room",
    "우리의 첫 협업 공간",
    "다른 역할, 하나의 결과물",
    "프로젝트",
    "서울",
    "기획,백엔드,프론트엔드",
    "web,peer,presentation",
  ],
];
export const programs: Program[] = rows.map((r, i) => {
  const current: Notice = {
    id: `${r[0]}-2`,
    round: "2기",
    current: true,
    open: ![3, 8, 10].includes(i),
    roles: r[5].split(","),
    activities: r[6].split(",").map((k) => ({
      ...experiences[k],
      evidence:
        k === "mentor"
          ? "현직자와 소그룹 질의응답 및 피드백 시간을 진행합니다."
          : k === "peer"
            ? "참여자끼리 관심사를 소개하고 정기 교류 모임을 진행합니다."
            : k === "web"
              ? "직무별 역할을 나눠 팀으로 웹 서비스를 제작합니다."
              : k === "app"
                ? "팀을 구성해 앱 시제품을 함께 제작합니다."
                : k === "task"
                  ? "주어진 서비스 문제를 분석하고 기획 과제를 수행합니다."
                  : k === "lecture"
                    ? "현업 업무 과정에 대한 소개 강연을 듣습니다."
                    : "참여자가 자신의 결과물을 직접 발표합니다.",
    })),
    start: "2026-09-01",
    deadline: `2026-10-${String(4 + i).padStart(2, "0")}`,
    qualification:
      i % 2
        ? "대학생 및 휴학생 · 사전 과제 제출"
        : "대학생 · 주 1회 모임 참여 가능",
  };
  const past: Notice = {
    ...current,
    id: `${r[0]}-1`,
    round: "1기",
    current: false,
    open: false,
    start: "2026-03-01",
    deadline: "2026-03-20",
    activities: [
      {
        action: "네트워킹",
        target: "졸업생",
        evidence:
          "지난 기수에는 졸업생과 교류 모임을 진행했습니다. 이번 기수 제공 여부는 이 기록으로 확인할 수 없습니다.",
      },
    ],
  };
  const extra: Notice[] =
    i === 3
      ? [{ ...current, id: "app-club-design", roles: ["디자인"], open: true }]
      : [];
  return {
    id: r[0],
    orgId: organizations[i % 4].id,
    title: r[1],
    subtitle: r[2],
    category: r[3],
    location: r[4],
    cover: `/posters/${r[0]}.svg`,
    notices: [current, ...extra, past],
  };
});
