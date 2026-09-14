-- V5: 제출 코멘트 (docs submission/design.md 결정 18, 2026-09-14 PM 확정) - 운영진이 제출별로 남기는 코멘트 1개. 점수는 두지 않는다(채점 결과는 채점 엔진이 말한다)
-- mentor_comment 열은 V1 의 "[P2 준비]" 예약 열을 그대로 쓰고, 누가·언제 남겼는지 두 열을 추가한다. score 는 사용하지 않기로 확정돼 제거

alter table submissions drop column score;
alter table submissions add column commented_by bigint;                          -- 코멘트 작성 운영진 - 코멘트 없으면 null
alter table submissions add column commented_at timestamp(6) with time zone;     -- 코멘트 마지막 변경 시각 - 코멘트 없으면 null
alter table submissions add constraint fk_submissions_commented_by foreign key (commented_by) references users (id);
