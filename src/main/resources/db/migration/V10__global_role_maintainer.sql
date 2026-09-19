-- V10: 전역 역할 MAINTAINER(관리자) 추가 - 2026-09-19 PM, docs 결정 12
--
-- 유지보수 팀은 동아리 임원(해구르르, ADMIN)도 교육운영진도 수강생도 아니지만 해구르르와 같은 권한이 필요하다.
-- 권한은 코드에서 User.isAdmin() 이 ADMIN·MAINTAINER 를 똑같이 통과시키고, 다른 것은 표시 명칭("관리자")뿐이다.
-- 지정은 부트스트랩과 같은 수동 SQL: UPDATE users SET global_role = 'MAINTAINER' WHERE login_id = '<loginId>';
alter table users drop constraint chk_users_global_role;
alter table users add constraint chk_users_global_role check (global_role in ('ADMIN', 'MAINTAINER', 'MEMBER'));
