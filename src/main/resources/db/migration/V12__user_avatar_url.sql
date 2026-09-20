-- V12: 프로필 사진 주소 - 2026-09-20 PM (원안 상단 바의 사진 아바타 반영)
--
-- Ondal 은 사진을 업로드받지 않는다. 홈페이지 로그인(Keycloak OIDC) 의 ID 토큰 `picture` 클레임(구글 프로필 사진)을 로그인마다 받아 적는다.
-- 클레임이 없으면 그대로(NULL 이면 화면은 이름 첫 글자). Keycloak 쪽 설정: Google IdP 매퍼(picture → 사용자 속성) + 클라이언트 프로토콜 매퍼(속성 → 토큰 claim picture)
alter table users add column avatar_url varchar(500);
