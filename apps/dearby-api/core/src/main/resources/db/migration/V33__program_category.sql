-- 2026-10-19 프로그램 기본 종류(shared/contracts/admin-v1.md "프로그램 기본 종류"): 활동 종류와 같은 코드. 수집·운영자 생성 활동의 기본값이며 일괄 적용에 쓴다
alter table programs add column category varchar(20)
    check (category is null or category in ('BOOTCAMP', 'CONFERENCE', 'WORKSHOP', 'STUDY', 'CLUB', 'LECTURE', 'HACKATHON', 'CONTEST', 'MEETUP', 'OTHER'));
