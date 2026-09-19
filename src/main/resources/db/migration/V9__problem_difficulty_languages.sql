-- V9: 문제 난이도 + 허용 언어 (문제 은행 100문제 준비, 2026-09-19 PM)
--
-- difficulty: 1~25 정수. 표기는 "대분류-소분류" (1-1 ~ 5-5, 둘 다 클수록 어려움). 저장은 (대분류-1)*5+소분류 하나로 - 정렬·필터가 단순해진다.
--   백준 티어 대응: 브론즈 5~1 = 1-1~1-5, 실버 = 2-x, 골드 = 3-x, 플래티넘 = 4-x, 다이아 = 5-x. null = 미지정(기존 문제)
-- allowed_languages: 제출 허용 언어(쉼표 구분, ondal.judge.languages 키 그대로 - 예 "C,Python 3"). null = 제한 없음.
--   "C언어" 태그 문제를 파이썬으로 풀면 태그가 장식이 된다 - 언어별 특화 문제에만 건다 (judge/design.md 결정 7 의 "과제별 제한 없음"은 유지, 문제별 제한만 추가)
alter table problems add column difficulty integer;   -- Integer 필드와 타입을 맞춘다 (smallint 면 Hibernate 스키마 검증이 int2≠integer 로 실패)
alter table problems add constraint chk_problems_difficulty check (difficulty is null or (difficulty between 1 and 25));
alter table problems add column allowed_languages varchar(200);
create index idx_problems_difficulty on problems (difficulty);
