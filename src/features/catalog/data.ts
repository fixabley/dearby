import type { Program } from "./model";
export { organizations } from "./organizations";
export const snapshotDate = "2026-09-24";

// Manually verified official-source snapshot; not a live crawler.
export const programs: Program[] = [
  {
    "id": "feconf",
    "orgId": "org-fedg",
    "title": "FEConf",
    "subtitle": "프론트엔드 개발 경험과 동료들의 이야기",
    "category": "컨퍼런스",
    "cover": "/conferences/feconf.png",
    "coverSource": {
      "url": "https://2026.feconf.kr/opengraph-image.png?opengraph-image.03nqa-lmhuxgj.png",
      "pageUrl": "https://2026.feconf.kr",
      "kind": "og",
      "checkedAt": "2026-09-24"
    },
    "notices": [
      {
        "id": "feconf-2026",
        "round": "2026",
        "current": true,
        "participationType": "registration",
        "status": "scheduled",
        "roles": [
          "프론트엔드",
          "AI",
          "디자인"
        ],
        "activities": [
          {
            "action": "청강",
            "target": "기술 세션",
            "evidence": "공식 프로그램에 기술 발표 세션이 안내되어 있습니다."
          },
          {
            "action": "네트워킹",
            "target": "동료",
            "evidence": "공식 Experience 영역에서 개발자끼리 대화하는 시간을 안내합니다."
          }
        ],
        "start": null,
        "deadline": null,
        "eventDate": "2026-10-24",
        "eventEndDate": null,
        "location": "서울 롯데월드타워 31층",
        "cost": null,
        "audience": null,
        "qualification": null,
        "officialUrl": "https://2026.feconf.kr",
        "registrationUrl": null,
        "sources": [
          {
            "url": "https://2026.feconf.kr",
            "label": "공식 행사 안내",
            "checkedAt": "2026-09-24",
            "evidence": "10월 24일 롯데타워 31층 개최 및 티켓 오픈 전 안내 확인. 정확한 등록 시작일·가격은 미확인."
          }
        ]
      }
    ]
  },
  {
    "id": "woowacon",
    "orgId": "org-woowa",
    "title": "WOOWACON · 우아콘",
    "subtitle": "배민 서비스와 일하는 방식을 바꾸는 기술",
    "category": "컨퍼런스",
    "cover": "/conferences/woowacon.png",
    "coverSource": {
      "url": "https://woowacon.com/og-default-20260921.png",
      "pageUrl": "https://woowacon.com/",
      "kind": "og",
      "checkedAt": "2026-09-24"
    },
    "notices": [
      {
        "id": "woowacon-2026",
        "round": "2026",
        "current": true,
        "participationType": "application",
        "status": "open",
        "roles": [
          "프론트엔드",
          "백엔드",
          "iOS",
          "Android",
          "AI",
          "데이터",
          "디자인",
          "기획",
          "보안"
        ],
        "activities": [
          {
            "action": "청강",
            "target": "기술 세션",
            "evidence": "공식 프로그램에 기술 발표 세션이 안내되어 있습니다."
          },
          {
            "action": "멘토링",
            "evidence": "집중형 멘토링 트랙 안내. 행사 참석 선정자에게 별도 신청 절차가 있으며 참여를 보장하지 않습니다."
          }
        ],
        "start": null,
        "deadline": "2026-10-13",
        "eventDate": "2026-10-28",
        "eventEndDate": null,
        "location": "서울 그랜드 인터컨티넨탈 파르나스 5층",
        "cost": "무료",
        "audience": [
          "직군 제한 없이 관심 있는 사람"
        ],
        "qualification": "신청자 중 추첨 선정. 10월 16일 이후 결과 안내. 멘토링은 선정자에게 별도 신청 방법 안내.",
        "officialUrl": "https://woowacon.com/",
        "registrationUrl": "https://woowacon.com/apply",
        "sources": [
          {
            "url": "https://woowacon.com/",
            "label": "공식 행사 안내",
            "checkedAt": "2026-09-24",
            "evidence": "홈페이지 신청 마감 카운트다운과 FAQ에서 10월 13일 신청 마감, 무료, 신청자 추첨을 확인."
          }
        ]
      }
    ]
  },
  {
    "id": "dan",
    "orgId": "org-naver",
    "title": "NAVER DAN",
    "subtitle": "네이버의 AI·서비스·비즈니스 기술 공유",
    "category": "컨퍼런스",
    "cover": "/conferences/dan.png",
    "coverSource": {
      "url": "https://dan.naver.com/2025/img/dan25_og.png",
      "pageUrl": "https://dan.naver.com/25",
      "kind": "og",
      "checkedAt": "2026-09-24"
    },
    "notices": [
      {
        "id": "dan-2025",
        "round": "2025",
        "current": true,
        "participationType": "registration",
        "status": "ended",
        "roles": [
          "AI",
          "데이터",
          "기획"
        ],
        "activities": [
          {
            "action": "청강",
            "target": "기술 세션",
            "evidence": "공식 프로그램에 기술 발표 세션이 안내되어 있습니다."
          }
        ],
        "start": null,
        "deadline": null,
        "eventDate": "2025-11-06",
        "eventEndDate": "2025-11-07",
        "location": "서울 코엑스 그랜드볼룸·아셈볼룸·오디토리움",
        "cost": null,
        "audience": null,
        "qualification": null,
        "officialUrl": "https://dan.naver.com/25",
        "registrationUrl": null,
        "sources": [
          {
            "url": "https://dan.naver.com/25",
            "label": "공식 행사 안내",
            "checkedAt": "2026-09-24",
            "evidence": "DAN25 공식 화면의 2025년 11월 6–7일과 코엑스 장소, 세션 다시보기 공개 확인. 2026 회차로 추정하지 않음."
          }
        ]
      }
    ]
  },
  {
    "id": "kakao",
    "orgId": "org-kakao",
    "title": "if(kakao)",
    "subtitle": "카카오 AI와 제품 기술을 공유하는 컨퍼런스",
    "category": "컨퍼런스",
    "cover": "/conferences/kakao-2026.png",
    "coverSource": {
      "url": "https://t1.kakaocdn.net/service_if_kakao_prod/service/2026/images/img_og2026.png",
      "pageUrl": "https://if.kakao.com/2026",
      "kind": "og",
      "checkedAt": "2026-09-24"
    },
    "notices": [
      {
        "id": "kakao-2026",
        "round": "2026",
        "current": true,
        "participationType": "application",
        "status": "open",
        "roles": [
          "AI"
        ],
        "activities": [
          {
            "action": "청강",
            "target": "기술 세션",
            "evidence": "공식 프로그램에 기술 발표 세션이 안내되어 있습니다."
          }
        ],
        "start": "2026-09-07",
        "deadline": "2026-09-28",
        "eventDate": "2026-10-13",
        "eventEndDate": "2026-10-14",
        "location": "카카오 AI 캠퍼스 · 온라인 생중계",
        "cost": "무료",
        "audience": [
          "만 18세 이상"
        ],
        "qualification": "오프라인은 신청자 중 선정자만 참석. 만 18세 이상, 1일만 신청 가능. 9월 28일 낮 12시 마감, 10월 1일 선정 발표 예정.",
        "officialUrl": "https://if.kakao.com/2026",
        "registrationUrl": "https://if.kakao.com/2026",
        "sources": [
          {
            "url": "https://if.kakao.com/2026",
            "label": "공식 행사 안내 및 FAQ",
            "checkedAt": "2026-09-24",
            "evidence": "10월 13–14일 개최, 9월 7일–28일 낮 12시 신청 접수, 신청 OPEN, 무료·만 18세 이상·선정자만 오프라인 참석 확인."
          }
        ]
      },
      {
        "id": "kakao-2025",
        "round": "2025",
        "current": false,
        "participationType": "application",
        "status": "ended",
        "roles": [
          "AI"
        ],
        "activities": [
          {
            "action": "청강",
            "target": "기술 세션",
            "evidence": "공식 프로그램에 기술 발표 세션이 안내되어 있습니다."
          }
        ],
        "start": "2025-08-28",
        "deadline": "2025-09-08",
        "eventDate": "2025-09-23",
        "eventEndDate": "2025-09-25",
        "location": "카카오 AI 캠퍼스",
        "cost": "무료",
        "audience": [
          "만 18세 이상"
        ],
        "qualification": "오프라인은 초청자와 신청자 중 선정자만 참석. 3일차는 카카오 그룹 임직원 대상. 자격에 따라 탐색에서 제외하지 않습니다.",
        "officialUrl": "https://if.kakao.com/2025",
        "registrationUrl": null,
        "sources": [
          {
            "url": "https://if.kakao.com/2025",
            "label": "공식 행사 안내",
            "checkedAt": "2026-09-24",
            "evidence": "공식 FAQ에서 행사 종료, 9월 23–25일, 무료, 만 18세 이상, 사전 초청/신청 선정자를 확인."
          }
        ]
      }
    ]
  },
  {
    "id": "toss",
    "orgId": "org-toss",
    "title": "Toss Makers Conference",
    "subtitle": "토스 메이커들의 기술과 제품 이야기",
    "category": "컨퍼런스",
    "cover": "/conferences/toss.png",
    "coverSource": {
      "url": "https://static.toss.im/assets/homepage/tmc-25/og-image.png",
      "pageUrl": "https://toss.im/tmc-25",
      "kind": "og",
      "checkedAt": "2026-09-24"
    },
    "notices": [
      {
        "id": "toss-2025",
        "round": "2025",
        "current": true,
        "participationType": "registration",
        "status": "ended",
        "roles": [
          "기획",
          "디자인",
          "데이터"
        ],
        "activities": [
          {
            "action": "청강",
            "target": "기술 세션",
            "evidence": "공식 종료 보도자료에서 제품·디자인·엔지니어링·데이터 분야 발표 세션을 확인했습니다."
          },
          {
            "action": "네트워킹",
            "target": "현직자",
            "evidence": "공식 종료 보도자료에 발표자와 참석자가 직접 소통하는 네트워킹 세션이 명시되어 있습니다."
          }
        ],
        "start": null,
        "deadline": null,
        "eventDate": "2025-07-23",
        "eventEndDate": "2025-07-25",
        "location": "서울 코엑스 그랜드볼룸",
        "cost": null,
        "audience": null,
        "qualification": null,
        "officialUrl": "https://toss.im/tmc-25",
        "registrationUrl": null,
        "sources": [
          {
            "url": "https://toss.im/tmc-25",
            "label": "공식 행사 안내",
            "checkedAt": "2026-09-24",
            "evidence": "공식 페이지의 행사 종료 인사와 세션 다시보기 안내 확인. 현재 첫 화면에서 개최일·장소·비용·자격은 미확인."
          },
          {
            "url": "https://toss.im/tossfeed/article/tmc25__",
            "label": "토스 공식 종료 보도자료 (2025.07.27)",
            "checkedAt": "2026-09-24",
            "evidence": "7월 23–25일 코엑스 그랜드볼룸 개최, PO·디자인·엔지니어·DA 발표 및 발표자/참석자 네트워킹 확인. 비용·참가 자격은 미확인."
          }
        ]
      }
    ]
  },
  {
    "id": "techverse",
    "orgId": "org-ly",
    "title": "Tech-Verse",
    "subtitle": "한국어로 만나는 LY Corporation 기술 공유",
    "category": "컨퍼런스",
    "cover": "/conferences/techverse.png",
    "coverSource": {
      "url": "https://tech-verse.lycorp.co.jp/2026/images/ogp-tech-verse-2026-en-ko.png",
      "pageUrl": "https://tech-verse.lycorp.co.jp/2026/ko/",
      "kind": "og",
      "checkedAt": "2026-09-24"
    },
    "notices": [
      {
        "id": "techverse-2026",
        "round": "2026",
        "current": true,
        "participationType": "registration",
        "status": "ended",
        "roles": [
          "AI",
          "백엔드",
          "데이터",
          "보안"
        ],
        "activities": [
          {
            "action": "청강",
            "target": "기술 세션",
            "evidence": "공식 프로그램에 기술 발표 세션이 안내되어 있습니다."
          }
        ],
        "start": null,
        "deadline": null,
        "eventDate": "2026-06-29",
        "eventEndDate": null,
        "location": "온라인 · 한국어 페이지 제공",
        "cost": null,
        "audience": null,
        "qualification": null,
        "officialUrl": "https://tech-verse.lycorp.co.jp/2026/ko/",
        "registrationUrl": null,
        "sources": [
          {
            "url": "https://tech-verse.lycorp.co.jp/2026/ko/",
            "label": "공식 행사 안내",
            "checkedAt": "2026-09-24",
            "evidence": "공식 한국어 페이지의 6월 29일 온라인 개최 및 종료 안내, AI/Core Technology 트랙 확인."
          }
        ]
      }
    ]
  },
  {
    "id": "pycon",
    "orgId": "org-python",
    "title": "PyCon Korea · 파이콘 한국",
    "subtitle": "파이썬 개발자들의 컨퍼런스와 실습 프로그램",
    "category": "컨퍼런스",
    "cover": "/conferences/pycon.png",
    "coverSource": {
      "url": "https://2026.pycon.kr/slogan_logo.png",
      "pageUrl": "https://2026.pycon.kr/",
      "kind": "og",
      "checkedAt": "2026-09-24"
    },
    "notices": [
      {
        "id": "pycon-2026",
        "round": "2026",
        "current": true,
        "participationType": "registration",
        "status": "ended",
        "roles": [
          "Python"
        ],
        "activities": [
          {
            "action": "청강",
            "target": "기술 세션",
            "evidence": "공식 프로그램에 기술 발표 세션이 안내되어 있습니다."
          },
          {
            "action": "실습",
            "evidence": "공식 일정에 8월 17일 튜토리얼·스프린트·딥다이브가 안내되어 있습니다. 별도 신청 조건은 미확인입니다."
          }
        ],
        "start": null,
        "deadline": null,
        "eventDate": "2026-08-15",
        "eventEndDate": "2026-08-17",
        "location": "서울 동국대학교 신공학관",
        "cost": null,
        "audience": null,
        "qualification": "컨퍼런스는 8월 15–16일, 튜토리얼·스프린트·딥다이브는 17일. 각 프로그램의 별도 참가 조건은 미확인.",
        "officialUrl": "https://2026.pycon.kr/",
        "registrationUrl": null,
        "sources": [
          {
            "url": "https://2026.pycon.kr/",
            "label": "공식 행사 안내",
            "checkedAt": "2026-09-24",
            "evidence": "8월 15–16일 컨퍼런스, 17일 튜토리얼/스프린트/딥다이브 및 티켓 마감 표시 확인."
          }
        ]
      }
    ]
  },
  {
    "id": "springcamp",
    "orgId": "org-ksug",
    "title": "Spring Camp",
    "subtitle": "JVM·Spring 개발 경험을 공유하는 자리",
    "category": "컨퍼런스",
    "cover": "/conferences/springcamp.png",
    "coverSource": {
      "url": "https://springcamp.ksug.org/2026/ko/",
      "pageUrl": "https://springcamp.ksug.org/2026/ko/",
      "kind": "capture",
      "checkedAt": "2026-09-24"
    },
    "notices": [
      {
        "id": "springcamp-2026",
        "round": "2026",
        "current": true,
        "participationType": "registration",
        "status": "ended",
        "roles": [
          "백엔드",
          "AI"
        ],
        "activities": [
          {
            "action": "청강",
            "target": "기술 세션",
            "evidence": "공식 프로그램에 기술 발표 세션이 안내되어 있습니다."
          }
        ],
        "start": null,
        "deadline": null,
        "eventDate": "2026-06-13",
        "eventEndDate": null,
        "location": "서울 강남 모나코스페이스",
        "cost": null,
        "audience": null,
        "qualification": null,
        "officialUrl": "https://springcamp.ksug.org/2026/ko/",
        "registrationUrl": null,
        "sources": [
          {
            "url": "https://springcamp.ksug.org/2026/ko/",
            "label": "공식 행사 안내",
            "checkedAt": "2026-09-24",
            "evidence": "공식 2026 페이지의 6월 13일, 강남 모나코스페이스와 JVM/Spring AI 등 세션 분류 확인."
          }
        ]
      },
      {
        "id": "springcamp-2025",
        "round": "2025",
        "current": false,
        "participationType": "registration",
        "status": "ended",
        "roles": [
          "백엔드"
        ],
        "activities": [],
        "start": null,
        "deadline": null,
        "eventDate": "2025-06-28",
        "eventEndDate": null,
        "location": "서울 스페이스쉐어 삼성",
        "cost": null,
        "audience": null,
        "qualification": null,
        "officialUrl": "https://springcamp.ksug.org/2025/",
        "registrationUrl": null,
        "sources": [
          {
            "url": "https://springcamp.ksug.org/",
            "label": "공식 행사 이력",
            "checkedAt": "2026-09-24",
            "evidence": "공식 행사 이력의 2025년 6월 28일·스페이스쉐어 삼성 확인. 경험을 2026 회차에서 복사하지 않음."
          }
        ]
      }
    ]
  },
  {
    "id": "letswift",
    "orgId": "org-letswift",
    "title": "Let’Swift",
    "subtitle": "Swift·iOS 개발 경험을 나누는 커뮤니티 컨퍼런스",
    "category": "컨퍼런스",
    "cover": "/conferences/letswift.png",
    "coverSource": {
      "url": "https://letswift.kr/2025/og.image.png",
      "pageUrl": "https://letswift.kr/2025/",
      "kind": "og",
      "checkedAt": "2026-09-24"
    },
    "notices": [
      {
        "id": "letswift-2025",
        "round": "2025",
        "current": true,
        "participationType": "registration",
        "status": "ended",
        "roles": [
          "iOS"
        ],
        "activities": [
          {
            "action": "청강",
            "target": "기술 세션",
            "evidence": "공식 프로그램에 기술 발표 세션이 안내되어 있습니다."
          }
        ],
        "start": null,
        "deadline": null,
        "eventDate": "2025-11-24",
        "eventEndDate": null,
        "location": "서울 세종대학교 컨벤션센터",
        "cost": null,
        "audience": null,
        "qualification": null,
        "officialUrl": "https://letswift.kr/2025/",
        "registrationUrl": null,
        "sources": [
          {
            "url": "https://letswift.kr/2025/",
            "label": "공식 행사 안내",
            "checkedAt": "2026-09-24",
            "evidence": "공식 2025 페이지의 11월 24일·세종대학교 컨벤션센터 및 연사/시간표 확인."
          }
        ]
      }
    ]
  },
  {
    "id": "gophercon",
    "orgId": "org-golang",
    "title": "GopherCon Korea",
    "subtitle": "Go 언어 개발자를 위한 컨퍼런스",
    "category": "컨퍼런스",
    "cover": "/conferences/gophercon.png",
    "coverSource": {
      "url": "https://gophercon.kr/ogImage.png",
      "pageUrl": "https://gophercon.kr/",
      "kind": "og",
      "checkedAt": "2026-09-24"
    },
    "notices": [
      {
        "id": "gophercon-2025",
        "round": "2025",
        "current": true,
        "participationType": "registration",
        "status": "ended",
        "roles": [
          "Go",
          "백엔드"
        ],
        "activities": [
          {
            "action": "청강",
            "target": "기술 세션",
            "evidence": "공식 프로그램에 기술 발표 세션이 안내되어 있습니다."
          }
        ],
        "start": null,
        "deadline": null,
        "eventDate": "2025-11-09",
        "eventEndDate": null,
        "location": "서울 코엑스 마곡 4층 르웨스트홀 B",
        "cost": null,
        "audience": null,
        "qualification": null,
        "officialUrl": "https://gophercon.kr/",
        "registrationUrl": null,
        "sources": [
          {
            "url": "https://gophercon.kr/",
            "label": "공식 행사 안내",
            "checkedAt": "2026-09-24",
            "evidence": "공식 페이지의 2025년 11월 9일, COEX Magok 4F Le West Hall B 확인. 남아 있는 구매 버튼을 현재 등록으로 보지 않음."
          }
        ]
      }
    ]
  },
  {
    "id": "aws",
    "orgId": "org-aws",
    "title": "AWS Summit Seoul",
    "subtitle": "클라우드와 AI의 산업 적용·기술 세션",
    "category": "컨퍼런스",
    "cover": "/conferences/aws.png",
    "coverSource": {
      "url": "https://aws.amazon.com/ko/events/summits/seoul/",
      "pageUrl": "https://aws.amazon.com/ko/events/summits/seoul/",
      "kind": "capture",
      "checkedAt": "2026-09-24"
    },
    "notices": [
      {
        "id": "aws-2026",
        "round": "2026",
        "current": true,
        "participationType": "registration",
        "status": "ended",
        "roles": [
          "클라우드",
          "AI",
          "데이터",
          "보안"
        ],
        "activities": [
          {
            "action": "청강",
            "target": "기술 세션",
            "evidence": "공식 프로그램에 기술 발표 세션이 안내되어 있습니다."
          },
          {
            "action": "실습",
            "evidence": "공식 Workshop 설명에서 개인 노트북을 이용한 AI 실습을 안내합니다. 프로그램별 조건을 확인해야 합니다."
          },
          {
            "action": "네트워킹",
            "target": "동료",
            "evidence": "공식 소개에서 전문가·동료·파트너와의 교류 기회를 안내합니다."
          }
        ],
        "start": null,
        "deadline": null,
        "eventDate": "2026-05-20",
        "eventEndDate": "2026-05-21",
        "location": "서울 코엑스",
        "cost": "무료",
        "audience": null,
        "qualification": null,
        "officialUrl": "https://aws.amazon.com/ko/events/summits/seoul/",
        "registrationUrl": null,
        "sources": [
          {
            "url": "https://aws.amazon.com/ko/events/summits/seoul/",
            "label": "공식 행사 안내",
            "checkedAt": "2026-09-24",
            "evidence": "공식 페이지의 5월 20–21일 코엑스 일정, 무료 등록 메타 설명, 행사 종료 본문 확인. 온디맨드 등록은 현장 행사 등록과 구분."
          }
        ]
      }
    ]
  },
  {
    "id": "ktcloud",
    "orgId": "org-kt",
    "title": "kt cloud summit",
    "subtitle": "클라우드와 AX 실행 사례를 만나는 컨퍼런스",
    "category": "컨퍼런스",
    "cover": "/conferences/ktcloud.jpg",
    "coverSource": {
      "url": "https://summit.ktcloud.com/resource/images/share-img.jpg",
      "pageUrl": "https://summit.ktcloud.com/",
      "kind": "og",
      "checkedAt": "2026-09-24"
    },
    "notices": [
      {
        "id": "ktcloud-2026",
        "round": "2026",
        "current": true,
        "participationType": "registration",
        "status": "ended",
        "roles": [
          "클라우드",
          "AI"
        ],
        "activities": [
          {
            "action": "청강",
            "target": "기술 세션",
            "evidence": "공식 프로그램에 기술 발표 세션이 안내되어 있습니다."
          }
        ],
        "start": null,
        "deadline": null,
        "eventDate": "2026-09-15",
        "eventEndDate": null,
        "location": "서울 그랜드 인터컨티넨탈 파르나스 5층",
        "cost": null,
        "audience": null,
        "qualification": null,
        "officialUrl": "https://summit.ktcloud.com/",
        "registrationUrl": null,
        "sources": [
          {
            "url": "https://summit.ktcloud.com/",
            "label": "공식 행사 안내",
            "checkedAt": "2026-09-24",
            "evidence": "공식 페이지의 2026년 9월 15일, 장소와 다시보기 안내 확인."
          }
        ]
      }
    ]
  },
  {
    "id": "droid",
    "orgId": "org-droid",
    "title": "드로이드나이츠 · Droid Knights",
    "subtitle": "Android 현업 문제 해결과 개발 경험",
    "category": "컨퍼런스",
    "cover": "/conferences/droid.png",
    "coverSource": {
      "url": "https://droidknights.dev/ko",
      "pageUrl": "https://droidknights.dev/ko",
      "kind": "capture",
      "checkedAt": "2026-09-24"
    },
    "notices": [
      {
        "id": "droid-2026",
        "round": "2026",
        "current": true,
        "participationType": "registration",
        "status": "open",
        "roles": [
          "Android",
          "AI"
        ],
        "activities": [
          {
            "action": "청강",
            "target": "기술 세션",
            "evidence": "공식 프로그램에 기술 발표 세션이 안내되어 있습니다."
          }
        ],
        "start": "2026-09-14",
        "deadline": "2026-11-01",
        "eventDate": "2026-11-02",
        "eventEndDate": null,
        "location": "서울 과학기술컨벤션센터 B1 대회의실",
        "cost": "일반 69,000원 · 개인후원 150,000원",
        "audience": null,
        "qualification": "1인 1매, 현장 구매 불가. 2026년 발표 영상은 녹화·공개하지 않는다고 안내합니다. 이력서 리뷰는 별도 선정 프로그램입니다.",
        "officialUrl": "https://droidknights.dev/ko",
        "registrationUrl": "https://ticketa.co/event/2o8rdpls",
        "sources": [
          {
            "url": "https://droidknights.dev/ko",
            "label": "공식 행사 안내",
            "checkedAt": "2026-09-24",
            "evidence": "공식 행사 사이트와 연결된 주최자 티켓 페이지에서 일정·장소·판매기간·가격 확인."
          },
          {
            "url": "https://ticketa.co/event/2o8rdpls",
            "label": "공식 연결 티켓 페이지",
            "checkedAt": "2026-09-24",
            "evidence": "판매기간 9월 14일 13:00–11월 1일 23:59, 일반 69,000원/개인후원 150,000원 확인."
          }
        ]
      }
    ]
  },
  {
    "id": "gdgseoul",
    "orgId": "org-gdgseoul",
    "title": "DevFest Cloud x Seoul",
    "subtitle": "AI·클라우드 기술과 실습, 커피챗",
    "category": "컨퍼런스",
    "cover": "/conferences/gdgseoul.png",
    "coverSource": {
      "url": "https://res.cloudinary.com/startup-grind/image/upload/c_fill,dpr_2.0,f_auto,g_center,h_1080,q_100,w_1080/v1/gcs/platform-data-goog/events/blob_rwnV2tB",
      "pageUrl": "https://gdg.community.dev/events/details/google-gdg-seoul-presents-gdg-devfest-cloud-x-seoul-2025/",
      "kind": "og",
      "checkedAt": "2026-09-24"
    },
    "notices": [
      {
        "id": "gdgseoul-2025",
        "round": "2025",
        "current": true,
        "participationType": "registration",
        "status": "ended",
        "roles": [
          "AI",
          "클라우드"
        ],
        "activities": [
          {
            "action": "청강",
            "target": "기술 세션",
            "evidence": "공식 프로그램에 기술 발표 세션이 안내되어 있습니다."
          },
          {
            "action": "실습",
            "evidence": "공식 Track 3는 Hands-on Lab으로 안내합니다."
          },
          {
            "action": "네트워킹",
            "target": "현직자",
            "evidence": "공식 Activity & Coffee-chat Zone에서 연사 소규모 대화와 기술/커리어 Q&A를 안내합니다."
          }
        ],
        "start": null,
        "deadline": null,
        "eventDate": "2025-11-30",
        "eventEndDate": null,
        "location": "서울 서강대학교 마태오관",
        "cost": null,
        "audience": [
          "대학생 개발자",
          "주니어 개발자",
          "AI·Cloud·LLM에 관심 있는 사람"
        ],
        "qualification": "정확한 티켓 조건은 주최자의 연결 티켓 페이지에서 확인하세요.",
        "officialUrl": "https://gdg.community.dev/events/details/google-gdg-seoul-presents-gdg-devfest-cloud-x-seoul-2025/",
        "registrationUrl": "https://event-us.kr/gdgseoul/event/116702",
        "sources": [
          {
            "url": "https://gdg.community.dev/events/details/google-gdg-seoul-presents-gdg-devfest-cloud-x-seoul-2025/",
            "label": "공식 행사 안내",
            "checkedAt": "2026-09-24",
            "evidence": "주최 GDG 행사 페이지의 2025년 11월 30일, 장소, 대상·트랙·실습·커피챗 안내 확인."
          }
        ]
      }
    ]
  },
  {
    "id": "gdgandroid",
    "orgId": "org-gdgandroid",
    "title": "DevFest Korea Android",
    "subtitle": "Android 커뮤니티 기술 공유 세션",
    "category": "컨퍼런스",
    "cover": "/conferences/gdgandroid.png",
    "coverSource": {
      "url": "https://gdg.community.dev/events/details/google-gdg-korea-android-presents-gdg-devfest-korea-android-2025-beoteomaegjupati/",
      "pageUrl": "https://gdg.community.dev/events/details/google-gdg-korea-android-presents-gdg-devfest-korea-android-2025-beoteomaegjupati/",
      "kind": "capture",
      "checkedAt": "2026-09-24"
    },
    "notices": [
      {
        "id": "gdgandroid-2025",
        "round": "2025",
        "current": true,
        "participationType": "registration",
        "status": "ended",
        "roles": [
          "Android"
        ],
        "activities": [
          {
            "action": "청강",
            "target": "기술 세션",
            "evidence": "공식 프로그램에 기술 발표 세션이 안내되어 있습니다."
          }
        ],
        "start": null,
        "deadline": null,
        "eventDate": "2025-12-14",
        "eventEndDate": null,
        "location": "서울 우아한형제들 작은집 7층",
        "cost": null,
        "audience": [
          "Android 개발자 및 관심 있는 사람"
        ],
        "qualification": "맥주 파티 콘셉트 행사입니다. 미성년 참가·주류 제공 조건은 미확인이므로 공식 주최자 확인이 필요합니다.",
        "officialUrl": "https://gdg.community.dev/events/details/google-gdg-korea-android-presents-gdg-devfest-korea-android-2025-beoteomaegjupati/",
        "registrationUrl": null,
        "sources": [
          {
            "url": "https://gdg.community.dev/events/details/google-gdg-korea-android-presents-gdg-devfest-korea-android-2025-beoteomaegjupati/",
            "label": "공식 행사 안내",
            "checkedAt": "2026-09-24",
            "evidence": "주최 GDG 페이지의 12월 14일, 장소와 Android 기술 세션 확인. 본문과 일정란 시간이 달라 시각은 등록하지 않음."
          }
        ]
      }
    ]
  },
  {
    "id": "saif",
    "orgId": "org-samsung",
    "title": "Samsung AI Forum · 삼성 AI 포럼",
    "subtitle": "에이전틱 AI 연구와 산업 적용",
    "category": "컨퍼런스",
    "cover": "/conferences/saif.jpg",
    "coverSource": {
      "url": "https://saif2026.com/theme/saif2026/assets/images/og-share-20260905.jpg",
      "pageUrl": "https://saif2026.com/",
      "kind": "og",
      "checkedAt": "2026-09-24"
    },
    "notices": [
      {
        "id": "saif-2026",
        "round": "2026",
        "current": true,
        "participationType": "registration",
        "status": "unknown",
        "roles": [
          "AI"
        ],
        "activities": [
          {
            "action": "청강",
            "target": "기술 세션",
            "evidence": "공식 프로그램에 기술 발표 세션이 안내되어 있습니다."
          }
        ],
        "start": null,
        "deadline": null,
        "eventDate": "2026-09-30",
        "eventEndDate": null,
        "location": "온라인 · 삼성전자 서초사옥(현장 참석 제한)",
        "cost": null,
        "audience": null,
        "qualification": "현장은 사전 초청자 및 신청 임직원 중 참석 확정자에 한함. 일반인의 온라인 참가 자격·등록기간·비용은 미확인.",
        "officialUrl": "https://saif2026.com/",
        "registrationUrl": "https://saif2026.com/registration.php",
        "sources": [
          {
            "url": "https://saif2026.com/",
            "label": "공식 행사 안내",
            "checkedAt": "2026-09-24",
            "evidence": "공식 페이지에서 9월 30일 일정, 온라인 생중계, 현장 초청자/신청 임직원 확정자 제한 확인. 공개 등록 기간은 미확인."
          }
        ]
      }
    ]
  },
  {
    "id": "stc",
    "orgId": "org-samsung",
    "title": "Samsung Tech Conference · 삼성 테크 콘퍼런스",
    "subtitle": "삼성의 AI·소프트웨어 기술 세션",
    "category": "컨퍼런스",
    "cover": "/conferences/stc.png",
    "coverSource": {
      "url": "https://stckorea.com/assets/image/og_img.png",
      "pageUrl": "https://www.stckorea.com/",
      "kind": "og",
      "checkedAt": "2026-09-24"
    },
    "notices": [
      {
        "id": "stc-2025",
        "round": "2025",
        "current": true,
        "participationType": "registration",
        "status": "ended",
        "roles": [
          "AI",
          "데이터",
          "보안",
          "클라우드"
        ],
        "activities": [
          {
            "action": "청강",
            "target": "기술 세션",
            "evidence": "공식 프로그램에 기술 발표 세션이 안내되어 있습니다."
          }
        ],
        "start": null,
        "deadline": null,
        "eventDate": "2025-11-20",
        "eventEndDate": null,
        "location": "온라인",
        "cost": null,
        "audience": null,
        "qualification": null,
        "officialUrl": "https://www.stckorea.com/",
        "registrationUrl": null,
        "sources": [
          {
            "url": "https://www.stckorea.com/",
            "label": "공식 행사 안내",
            "checkedAt": "2026-09-24",
            "evidence": "삼성 Developer 공식 안내와 연결 행사 사이트에서 2025년 11월 20일 온라인 개최, 세션 다시보기 확인."
          },
          {
            "url": "https://developer.samsung.com/sdp/events/ko/2025/11/13/samsung-tech-conference-2025",
            "label": "삼성 Developer 행사 안내",
            "checkedAt": "2026-09-24",
            "evidence": "2025년 11월 20일 온라인 행사와 AI·보안 등 기술 세션 소개."
          }
        ]
      }
    ]
  },
  {
    "id": "deview",
    "orgId": "org-naver",
    "title": "DEVIEW",
    "subtitle": "2023 과거 아카이브 · 2024부터 DAN으로 이어진 기술 컨퍼런스",
    "category": "컨퍼런스",
    "cover": "/conferences/deview.jpg",
    "coverSource": {
      "url": "https://deview.naver.com/2023/img/og-edit.jpg",
      "pageUrl": "https://deview.naver.com/2023",
      "kind": "og",
      "checkedAt": "2026-09-24"
    },
    "notices": [
      {
        "id": "deview-2023",
        "round": "2023",
        "current": true,
        "participationType": "registration",
        "status": "ended",
        "roles": [
          "AI",
          "데이터",
          "백엔드",
          "프론트엔드",
          "클라우드",
          "보안"
        ],
        "activities": [
          {
            "action": "청강",
            "target": "기술 세션",
            "evidence": "공식 프로그램에 기술 발표 세션이 안내되어 있습니다."
          }
        ],
        "start": null,
        "deadline": null,
        "eventDate": "2023-02-27",
        "eventEndDate": "2023-02-28",
        "location": "서울 코엑스 그랜드볼룸",
        "cost": null,
        "audience": null,
        "qualification": null,
        "officialUrl": "https://deview.naver.com/2023",
        "registrationUrl": null,
        "sources": [
          {
            "url": "https://deview.naver.com/2023",
            "label": "공식 행사 안내",
            "checkedAt": "2026-09-24",
            "evidence": "공식 DEVIEW 2023 페이지의 종료 안내·2월 27–28일·코엑스·세션 자료 공개 확인. 최신 회차로 추정하지 않는 과거 자료."
          },
          {
            "url": "https://d2.naver.com/news/9328071",
            "label": "NAVER D2 · DEVIEW와 DAN 안내",
            "checkedAt": "2026-09-24",
            "evidence": "2024년 10월 22일 공식 안내에서 DEVIEW가 DAN으로 확대되고 DEVIEW 기술 세션을 제공한다고 설명합니다. 이 카드는 통합 전 2023 아카이브입니다."
          }
        ]
      }
    ]
  },
  {
    "id": "ubucon",
    "orgId": "org-ubuntu-debian",
    "title": "UbuCon Korea × MiniDebConf Korea",
    "subtitle": "우분투·데비안 공동 컨퍼런스 · 리눅스와 오픈소스",
    "category": "컨퍼런스",
    "cover": "/conferences/ubucon.png",
    "coverSource": {
      "url": "https://2026.ubuntu-kr.org/og.png",
      "pageUrl": "https://2026.ubuntu-kr.org/ko/",
      "kind": "og",
      "checkedAt": "2026-09-24"
    },
    "notices": [
      {
        "id": "ubucon-2026",
        "round": "2026",
        "current": true,
        "participationType": "registration",
        "status": "ended",
        "roles": [
          "Linux",
          "AI"
        ],
        "activities": [
          {
            "action": "청강",
            "target": "기술 세션",
            "evidence": "공식 일정에 리눅스 운영·PyTorch 메모리·오픈소스 기여 기술 발표가 안내되어 있습니다."
          }
        ],
        "start": null,
        "deadline": "2026-08-25",
        "eventDate": "2026-08-29",
        "eventEndDate": null,
        "location": "서울 센터필드 EAST · AWS 코리아 18층",
        "cost": "일반 30,000원 · 개인후원 100,000원 (권종별 조건 상이)",
        "audience": [
          "우분투·리눅스·자유 및 오픈소스 기술에 관심 있는 사람"
        ],
        "qualification": "공동 개최 행사로 한 번의 등록으로 참가. 최종 마감은 8월 25일 0시이며 현장 등록 불가. 후기 티켓은 식사·티셔츠를 보장하지 않음.",
        "officialUrl": "https://2026.ubuntu-kr.org/ko/",
        "registrationUrl": "https://2026.ubuntu-kr.org/tickets",
        "sources": [
          {
            "url": "https://2026.ubuntu-kr.org/ko/",
            "label": "공식 행사 안내",
            "checkedAt": "2026-09-24",
            "evidence": "8월29일 AWS 코리아 개최 및 행사 종료, MiniDebConf 공동 개최 확인."
          },
          {
            "url": "https://korea2026.mini.debconf.org/",
            "label": "공식 행사 안내",
            "checkedAt": "2026-09-24",
            "evidence": "공동행사 및 일반3만원·개인후원10만원 확인. 이 페이지의 초기마감보다 주최 측 최종연장공지 우선."
          },
          {
            "url": "https://2026.ubuntu-kr.org/en/about/",
            "label": "공식 행사 안내",
            "checkedAt": "2026-09-24",
            "evidence": "우분투·리눅스·자유/오픈소스 기술에 관심 있는 참가자를 위한 커뮤니티 행사."
          },
          {
            "url": "https://discourse.ubuntu-kr.org/t/ubucon-korea-x-minidebconf-korea-2026-8-25-0/52295",
            "label": "공식 행사 안내",
            "checkedAt": "2026-09-24",
            "evidence": "최종등록 마감8월25일0시, 현장등록과 추가연장불가 공지."
          },
          {
            "url": "https://discourse.ubuntu-kr.org/t/ubucon-korea-x-minidebconf-korea-2026-latebird/52292",
            "label": "공식 행사 안내",
            "checkedAt": "2026-09-24",
            "evidence": "8월17일부터 후기티켓의 식사·티셔츠 제공은 보장하지 않는다고 안내."
          }
        ]
      }
    ]
  },
  {
    "id": "openinfra",
    "orgId": "org-kcd-ceph-openinfra",
    "title": "KCD × Ceph × OpenInfra Day Korea",
    "subtitle": "쿠버네티스·분산 스토리지·개방형 인프라 공동 컨퍼런스",
    "category": "컨퍼런스",
    "cover": "/conferences/openinfra.png",
    "coverSource": {
      "url": "https://res.cloudinary.com/startup-grind/image/upload/c_fill,dpr_2.0,f_auto,g_center,h_1080,q_100,w_1080/v1/gcs/platform-data-cncf/events/blob_UiLtkr4",
      "pageUrl": "https://community2.cncf.io/events/details/cncf-kcd-south-korea-presents-kcd-x-ceph-x-openinfra-day-korea-2026/",
      "kind": "og",
      "checkedAt": "2026-09-24"
    },
    "notices": [
      {
        "id": "openinfra-2026",
        "round": "2026",
        "current": true,
        "participationType": "registration",
        "status": "ended",
        "roles": [
          "클라우드",
          "AI",
          "데이터",
          "보안"
        ],
        "activities": [
          {
            "action": "청강",
            "target": "기술 세션",
            "evidence": "공식 프로그램에서 쿠버네티스·Ceph·OpenInfra 기술 세션을 확인했습니다."
          },
          {
            "action": "네트워킹",
            "target": "현직자",
            "evidence": "공식 행사 소개에 오픈소스 기여자와 분야 전문가의 직접 교류를 안내합니다."
          }
        ],
        "start": null,
        "deadline": "2026-09-01",
        "eventDate": "2026-09-01",
        "eventEndDate": null,
        "location": "서울 용산구 백범김구기념관",
        "cost": "스탠다드 70,000원",
        "audience": [
          "클라우드·스토리지·인프라 엔지니어",
          "오픈소스에 관심 있는 개발자·학생·연구자"
        ],
        "qualification": null,
        "officialUrl": "https://community2.cncf.io/events/details/cncf-kcd-south-korea-presents-kcd-x-ceph-x-openinfra-day-korea-2026/",
        "registrationUrl": "https://event.plan9.co.kr/#/kcd_odk2026",
        "sources": [
          {
            "url": "https://community2.cncf.io/events/details/cncf-kcd-south-korea-presents-kcd-x-ceph-x-openinfra-day-korea-2026/",
            "label": "공식 행사 안내",
            "checkedAt": "2026-09-24",
            "evidence": "9월1일 백범김구기념관, 세 커뮤니티 공동주최, 기술세션·네트워킹 및 권장참가대상 확인."
          },
          {
            "url": "https://openinfra-kr.org/",
            "label": "공식 행사 안내",
            "checkedAt": "2026-09-24",
            "evidence": "스탠다드7만원 확인. 남아 있는 등록중 표시는 지난9월1일행사의 현재모집으로 사용하지 않음."
          },
          {
            "url": "https://wiki.ceph.com/en/community/events/2026/ceph-days-korea/",
            "label": "공식 행사 안내",
            "checkedAt": "2026-09-24",
            "evidence": "주최커뮤니티가 게시한9월1일 행사일·등록마감 확인."
          }
        ]
      }
    ]
  },
  {
    "id": "real",
    "orgId": "org-sds",
    "title": "REAL Summit",
    "subtitle": "삼성SDS의 엔터프라이즈 AI·클라우드 혁신 컨퍼런스",
    "category": "컨퍼런스",
    "cover": "/conferences/real.png",
    "coverSource": {
      "url": "https://www.realsummit2026.com/images/og.png",
      "pageUrl": "https://www.realsummit2026.com/",
      "kind": "og",
      "checkedAt": "2026-09-24"
    },
    "notices": [
      {
        "id": "real-2026",
        "round": "2026",
        "current": true,
        "participationType": "registration",
        "status": "ended",
        "roles": [
          "AI",
          "클라우드",
          "데이터"
        ],
        "activities": [
          {
            "action": "청강",
            "target": "기술 세션",
            "evidence": "공식 홈페이지에 키노트·산업별 AX 기술 세션이 안내되어 있습니다."
          },
          {
            "action": "실습",
            "evidence": "삼성SDS 공식 보도자료에서 AI 에이전트 제작과 클라우드 자원 생성·배포 체험을 안내합니다."
          }
        ],
        "start": null,
        "deadline": null,
        "eventDate": "2026-09-08",
        "eventEndDate": null,
        "location": "서울 코엑스 컨벤션센터",
        "cost": null,
        "audience": null,
        "qualification": "개별 체험 프로그램의 참가 조건은 미확인.",
        "officialUrl": "https://www.realsummit2026.com/",
        "registrationUrl": null,
        "sources": [
          {
            "url": "https://www.realsummit2026.com/",
            "label": "공식 행사 안내",
            "checkedAt": "2026-09-24",
            "evidence": "9월8일 코엑스 개최·행사종료 및 키노트/세션자료 공개 확인. 비용·등록기간·자격 미확인."
          },
          {
            "url": "https://www.samsungsds.com/kr/news/sds-260901.html",
            "label": "공식 행사 안내",
            "checkedAt": "2026-09-24",
            "evidence": "삼성SDS 주최, AI·클라우드 체험형 프로그램 확인."
          }
        ]
      }
    ]
  },
  {
    "id": "skai",
    "orgId": "org-sk",
    "title": "SK AI Summit",
    "subtitle": "SK의 AI 인프라·모델·산업 적용 컨퍼런스",
    "category": "컨퍼런스",
    "cover": "/conferences/skai.png",
    "coverSource": {
      "url": "https://www.skaisummit.com/image/common/OG.png",
      "pageUrl": "https://skaisummit.com/",
      "kind": "og",
      "checkedAt": "2026-09-24"
    },
    "notices": [
      {
        "id": "skai-2025",
        "round": "2025",
        "current": true,
        "participationType": "registration",
        "status": "ended",
        "roles": [
          "AI"
        ],
        "activities": [
          {
            "action": "청강",
            "target": "기술 세션",
            "evidence": "공식 FAQ에서 AI Infra·AI Model·AIX 트랙의 발표 및 온라인 중계를 확인했습니다."
          }
        ],
        "start": null,
        "deadline": null,
        "eventDate": "2025-11-03",
        "eventEndDate": "2025-11-04",
        "location": "서울 코엑스 · 온라인 스트리밍",
        "cost": null,
        "audience": null,
        "qualification": "당시 키노트·세션은 현장 선착순 입장. 2026 회차 개최 여부는 미확인.",
        "officialUrl": "https://skaisummit.com/",
        "registrationUrl": null,
        "sources": [
          {
            "url": "https://eng.sk.com/news/sk-hosts-sk-ai-summit-2025-with-vision-to-solve-ai-challenges-through-memory-infrastructure-and-advanced-ai-solutions",
            "label": "공식 행사 안내",
            "checkedAt": "2026-09-24",
            "evidence": "SK 공식보도자료의2025년11월3–4일 코엑스 개최 확인. 2026년회차를 추정하지 않음."
          },
          {
            "url": "https://skaisummit.com/faq",
            "label": "공식 행사 안내",
            "checkedAt": "2026-09-24",
            "evidence": "키노트/세션 현장선착순·온라인중계 및 녹화영상 공개안내 확인. 현재 남아 있는 참가신청버튼은 종료된2025행사로 분류."
          }
        ]
      }
    ]
  },

  {
    "id": "sopt",
    "orgId": "org-sopt",
    "title": "SOPT · 솝트",
    "subtitle": "대학생 IT 벤처창업 동아리 · 확인된 OB 서버 전형",
    "category": "연합동아리",
    "cover": "/clubs/sopt.png",
    "coverSource": {
      "url": "https://www.sopt.org/39th_og.png",
      "pageUrl": "https://www.sopt.org",
      "kind": "og",
      "checkedAt": "2026-09-24"
    },
    "notices": [
      {
        "id": "sopt-verified",
        "round": "39기 OB · 서버",
        "current": true,
        "participationType": "application",
        "status": "closed",
        "roles": [
          "백엔드"
        ],
        "activities": [],
        "start": "2026-08-08",
        "deadline": "2026-08-11",
        "eventDate": null,
        "eventEndDate": null,
        "activitySchedule": "9월 19일 OT · 전체 활동 기간 미확인",
        "selectionProcess": [
          "서류",
          "면접",
          "최종 발표"
        ],
        "location": null,
        "cost": null,
        "audience": [
          "대학생 · OB 전형"
        ],
        "qualification": "신규 YB 모집 안내가 아닌 OB 서버 파트 공고입니다. 휴학·졸업 및 OB 자격 상세는 공식 안내를 확인하세요.",
        "officialUrl": "https://www.sopt.org",
        "registrationUrl": "https://recruit.sopt.org/part/Server",
        "sources": [
          {
            "url": "https://recruit.sopt.org/part/Server",
            "label": "공식 모집 안내",
            "checkedAt": "2026-09-24",
            "evidence": "39기 OB 서버 파트: 8/8–11 접수, 8/15–16 면접, 8/19 최종 발표. 신규 회원 전형으로 확대 해석하지 않음."
          },
          {
            "url": "https://www.sopt.org",
            "label": "공식 소개",
            "checkedAt": "2026-09-24",
            "evidence": "대학생 IT 벤처창업 동아리 · 확인된 OB 서버 전형의 활동 소개와 프로그램 정체성을 확인했습니다."
          }
        ]
      }
    ]
  },
  {
    "id": "piro",
    "orgId": "org-piro",
    "title": "피로그래밍",
    "subtitle": "대학생 웹 개발 학습과 팀 프로젝트",
    "category": "연합동아리",
    "cover": "/clubs/piro.png",
    "coverSource": {
      "url": "https://pirogramming.com",
      "pageUrl": "https://pirogramming.com",
      "kind": "capture",
      "checkedAt": "2026-09-24"
    },
    "notices": [
      {
        "id": "piro-verified",
        "round": "25기",
        "current": true,
        "participationType": "application",
        "status": "unknown",
        "roles": [
          "프론트엔드",
          "백엔드",
          "Python"
        ],
        "activities": [
          {
            "action": "제작",
            "target": "웹 서비스",
            "method": "팀협업",
            "evidence": "공식 소개에 웹 개발 학습 후 팀을 구성해 웹 서비스를 제작하고 발표한다고 안내합니다."
          }
        ],
        "start": null,
        "deadline": null,
        "eventDate": null,
        "eventEndDate": null,
        "activitySchedule": "방학 중 약 2개월 · 주 3회 세션",
        "selectionProcess": [
          "서류 · 코딩테스트 안내 확인",
          "면접",
          "최종 발표"
        ],
        "location": null,
        "cost": null,
        "audience": [
          "대학생"
        ],
        "qualification": "코딩테스트 안내가 있습니다. 테스트 방식·제출물과 상세 자격은 공식 모집 안내에서 확인하세요.",
        "officialUrl": "https://pirogramming.com",
        "registrationUrl": "https://pirogramming.com/recruit/",
        "sources": [
          {
            "url": "https://pirogramming.com/recruit/",
            "label": "공식 모집 안내",
            "checkedAt": "2026-09-24",
            "evidence": "25기 5/25–6/5 서류, 6/13–14 면접 안내. 본문 연도 미표기로 정확한 날짜값은 미확인 유지. 공식 소개에서 대학생 대상 웹 학습·팀 프로젝트 확인."
          },
          {
            "url": "https://pirogramming.com/about/",
            "label": "공식 소개",
            "checkedAt": "2026-09-24",
            "evidence": "대학생 웹 개발 학습과 팀 프로젝트의 활동 소개와 프로그램 정체성을 확인했습니다."
          }
        ]
      }
    ]
  },
  {
    "id": "cotato",
    "orgId": "org-cotato",
    "title": "COTATO · 코테이토",
    "subtitle": "CS 학습과 기획·디자인·개발 협업",
    "category": "연합동아리",
    "cover": "/clubs/cotato.png",
    "coverSource": {
      "url": "https://www.cotato.kr/images/meta-data/thumbnail.png",
      "pageUrl": "https://www.cotato.kr",
      "kind": "og",
      "checkedAt": "2026-09-24"
    },
    "notices": [
      {
        "id": "cotato-verified",
        "round": "기수 미확인",
        "current": true,
        "participationType": "application",
        "status": "unknown",
        "roles": [
          "기획",
          "디자인",
          "프론트엔드",
          "백엔드"
        ],
        "activities": [
          {
            "action": "제작",
            "evidence": "공식 FAQ에서 기획 발표와 팀빌딩 후 약 4개월 프로젝트 및 데모데이를 안내합니다."
          }
        ],
        "start": null,
        "deadline": null,
        "eventDate": null,
        "eventEndDate": null,
        "activitySchedule": "한 기수 6개월 · 매주 금요일 19시",
        "selectionProcess": [
          "면접 · 약 30분, 4:2",
          "기타 선발 단계 미확인"
        ],
        "location": "서울",
        "cost": "기수당 50,000원 · 변경 가능",
        "audience": [
          "서울·경기권 대학교 4학기 이상 수료자"
        ],
        "qualification": "개발자는 컴퓨터·IT 관련 학과 주·복수전공생. 매주 금요일 19시 서울 오프라인 세션 참여 필수.",
        "officialUrl": "https://www.cotato.kr",
        "registrationUrl": "https://www.cotato.kr/recruit",
        "sources": [
          {
            "url": "https://www.cotato.kr/recruit",
            "label": "공식 모집 안내",
            "checkedAt": "2026-09-24",
            "evidence": "공식 모집 페이지 자격·FAQ에서 4학기 수료/개발자 전공/면접/회비 확인. 지난 상반기 일정과 남은 지원 버튼만으로 현재 모집을 판정하지 않음."
          },
          {
            "url": "https://www.cotato.kr",
            "label": "공식 소개",
            "checkedAt": "2026-09-24",
            "evidence": "CS 학습과 기획·디자인·개발 협업의 활동 소개와 프로그램 정체성을 확인했습니다."
          }
        ]
      }
    ]
  },
  {
    "id": "yapp",
    "orgId": "org-yapp",
    "title": "YAPP · 얍",
    "subtitle": "기획자·디자이너·개발자의 서비스 제작",
    "category": "연합동아리",
    "cover": "/clubs/yapp.png",
    "coverSource": {
      "url": "https://www.yapp.co.kr/assets/images/28th/preview.png",
      "pageUrl": "https://www.yapp.co.kr",
      "kind": "og",
      "checkedAt": "2026-09-24"
    },
    "notices": [
      {
        "id": "yapp-verified",
        "round": "28기",
        "current": true,
        "participationType": "application",
        "status": "closed",
        "roles": [
          "기획",
          "디자인",
          "프론트엔드",
          "백엔드",
          "Android",
          "iOS"
        ],
        "activities": [
          {
            "action": "제작",
            "target": "웹 서비스",
            "method": "팀협업",
            "evidence": "공식 소개에서 PM·디자인·개발 팀으로 웹/앱 서비스를 제작한다고 안내합니다."
          },
          {
            "action": "제작",
            "target": "앱",
            "method": "팀협업",
            "evidence": "공식 소개에서 PM·디자인·개발 팀으로 웹/앱 서비스를 제작한다고 안내합니다."
          }
        ],
        "start": null,
        "deadline": null,
        "eventDate": null,
        "eventEndDate": null,
        "activitySchedule": "약 4개월 · 토요일 13–17시",
        "selectionProcess": [
          "지원서·포트폴리오",
          "온라인 면접",
          "최종 합격"
        ],
        "location": "수도권 오프라인",
        "cost": "10만원대 초반 예상 · 확정액 미확인",
        "audience": [
          "대학생",
          "고졸·졸업자",
          "재직자"
        ],
        "qualification": "타 직군과 1회 이상 협업 경험 및 직군별 기술 조건. 토요일 정기 세션 참여 필수. 고졸 가능을 고등학교 재학생 가능으로 해석하지 않습니다.",
        "officialUrl": "https://www.yapp.co.kr",
        "registrationUrl": "https://www.yapp.co.kr/recruit",
        "sources": [
          {
            "url": "https://www.yapp.co.kr/recruit",
            "label": "공식 모집 안내",
            "checkedAt": "2026-09-24",
            "evidence": "28기 FAQ와 선발 절차/자격/시간/예상회비 확인. 현재 모집기간 아님·29기 알림신청 표시. 다음 기수 접수일은 미확인."
          },
          {
            "url": "https://www.yapp.co.kr",
            "label": "공식 소개",
            "checkedAt": "2026-09-24",
            "evidence": "기획자·디자이너·개발자의 서비스 제작의 활동 소개와 프로그램 정체성을 확인했습니다."
          }
        ]
      }
    ]
  },
  {
    "id": "depromeet",
    "orgId": "org-depromeet",
    "title": "디프만 · Depromeet",
    "subtitle": "디자이너와 개발자가 함께 출시하는 서비스",
    "category": "연합동아리",
    "cover": "/clubs/depromeet.png",
    "coverSource": {
      "url": "https://www.depromeet.com/og-main.jpg",
      "pageUrl": "https://www.depromeet.com",
      "kind": "og",
      "checkedAt": "2026-09-24"
    },
    "notices": [
      {
        "id": "depromeet-verified",
        "round": "19기",
        "current": true,
        "participationType": "application",
        "status": "scheduled",
        "roles": [
          "디자인",
          "프론트엔드",
          "백엔드",
          "Android",
          "iOS"
        ],
        "activities": [
          {
            "action": "제작",
            "evidence": "공식 소개는 서비스 기획부터 출시까지 협업하며, 19기 일정에 프리 런칭·런칭데이를 명시합니다."
          }
        ],
        "start": "2026-10-02",
        "deadline": "2026-10-08",
        "eventDate": null,
        "eventEndDate": null,
        "activitySchedule": "2026.10.31 OT – 2027.02.20 최종 발표 · 토요일",
        "selectionProcess": [
          "자기소개서·이력서/포트폴리오",
          "직무·컬처핏 온라인 인터뷰",
          "최종 발표"
        ],
        "location": "수도권 오프라인·온라인",
        "cost": null,
        "audience": null,
        "qualification": "매주 토요일 세션. 서류에 자기소개서와 이력서/포트폴리오 제출. 학적·나이 제한은 미확인. 마감 10월 8일 23:59:59.",
        "officialUrl": "https://www.depromeet.com",
        "registrationUrl": "https://www.depromeet.com/recruit",
        "sources": [
          {
            "url": "https://www.depromeet.com/recruit",
            "label": "공식 모집 안내",
            "checkedAt": "2026-09-24",
            "evidence": "19기 공식 공지: 10/2–8 서류, 10/12 서류 발표, 10/17–18 인터뷰, 10/22 최종 발표. 10/31 OT부터 이듬해 2/20 최종 발표 일정."
          },
          {
            "url": "https://www.depromeet.com",
            "label": "공식 소개",
            "checkedAt": "2026-09-24",
            "evidence": "디자이너와 개발자가 함께 출시하는 서비스의 활동 소개와 프로그램 정체성을 확인했습니다."
          }
        ]
      }
    ]
  },
  {
    "id": "mashup",
    "orgId": "org-mashup",
    "title": "Mash-Up · 매쉬업",
    "subtitle": "학생과 직장인이 함께하는 디자인·개발 커뮤니티",
    "category": "연합동아리",
    "cover": "/clubs/mashup.png",
    "coverSource": {
      "url": "https://mash-up.kr/_next/static/media/mash-up-thumbnail.d72025fe.png",
      "pageUrl": "https://mash-up.kr",
      "kind": "og",
      "checkedAt": "2026-09-24"
    },
    "notices": [
      {
        "id": "mashup-verified",
        "round": "16기",
        "current": true,
        "participationType": "application",
        "status": "unknown",
        "roles": [
          "디자인",
          "프론트엔드",
          "백엔드",
          "Android",
          "iOS"
        ],
        "activities": [
          {
            "action": "제작",
            "evidence": "공식 소개에서 디자이너와 개발자의 협업 서비스 제작 및 팀 해커톤을 안내합니다."
          }
        ],
        "start": null,
        "deadline": null,
        "eventDate": null,
        "eventEndDate": null,
        "activitySchedule": null,
        "selectionProcess": [
          "서류",
          "온·오프라인 면접",
          "최종 합격"
        ],
        "location": null,
        "cost": null,
        "audience": [
          "학생",
          "직장인"
        ],
        "qualification": "팀 중복 지원 불가. 세부 나이·학적·직군 조건은 공식 팀별 공고 확인. 공식 페이지의 전형 날짜가 모두 접속일로 표시되어 일정·모집상태를 확정하지 않았습니다.",
        "officialUrl": "https://mash-up.kr",
        "registrationUrl": "https://recruit.mash-up.kr/",
        "sources": [
          {
            "url": "https://recruit.mash-up.kr/",
            "label": "공식 모집 안내",
            "checkedAt": "2026-09-24",
            "evidence": "16기 공식 모집 페이지의 서류/면접 절차와 팀/학생·직장인 대상 확인. OPEN 문구와 비정상 날짜를 현재 접수 근거로 사용하지 않음."
          },
          {
            "url": "https://mash-up.kr",
            "label": "공식 소개",
            "checkedAt": "2026-09-24",
            "evidence": "학생과 직장인이 함께하는 디자인·개발 커뮤니티의 활동 소개와 프로그램 정체성을 확인했습니다."
          }
        ]
      }
    ]
  }
];
