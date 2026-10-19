-- 2026-10-19 활동 종류(shared/contracts/native-v1.md "활동 종류(category)"): 부트캠프·컨퍼런스·워크샵·스터디·동아리·강연·해커톤·공모전·밋업·기타. null은 미분류
alter table activities add column category varchar(20)
    check (category is null or category in ('BOOTCAMP', 'CONFERENCE', 'WORKSHOP', 'STUDY', 'CLUB', 'LECTURE', 'HACKATHON', 'CONTEST', 'MEETUP', 'OTHER'));
