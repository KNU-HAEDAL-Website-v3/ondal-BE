-- V8: 사용자 승인 상태 (docs 결정 10, 2026-09-19)
--
-- 왜
--   - 홈페이지(구글) 로그인만 되면 누구나 Ondal 화면에 들어올 수 있었다. 부원 확인 없이 분반 목록·전체 공지·HOJ 가 열린다
--   - 첫 로그인 계정은 PENDING 으로 두고, 운영진 이상이 승인하거나 분반에 배정할 때 ACTIVE 로 바꾼다
--
-- 기존 행은 전부 ACTIVE - 이미 쓰고 있는 관리자·운영진·테스트 계정을 잠그면 안 된다. 새 행의 값은 엔티티가 명시한다
alter table users add column status varchar(20) not null default 'ACTIVE';
alter table users add constraint chk_users_status check (status in ('PENDING', 'ACTIVE'));
create index idx_users_status on users (status);   -- 부원 목록의 "승인 대기" 필터·대시보드 카운트
