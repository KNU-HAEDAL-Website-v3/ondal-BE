# CLAUDE.md — haedal-online-judge-backend

> 전체 기획·설계 맥락은 [docs 레포](https://github.com/KNU-HAEDAL-Website-v3/haedal-online-judge-docs)에 있다.
> 이 파일은 백엔드 작업 시 필요한 최소 맥락 요약본이다.

## 이 레포는

HOJ(부트캠프 과제 제출·관리 플랫폼 + 온라인 저지)의 API 서버. Spring Boot + Gradle + PostgreSQL.

## 핵심 설계 원칙

1. **OJ 문제 = 분반 없는 과제.** Assignment의 `cohort_id NULL` + `deadline NULL` + 자동채점이면 OJ 문제다. 과제 제출과 OJ 풀이는 파이프라인 하나로 처리한다. 코드를 두 벌 만들지 않는다.
2. **권한은 2층.** `User.global_role`(ADMIN/MEMBER) + `Enrollment.role`(OPERATOR/STUDENT). 판정 순서: ①로그인? ②전역 ADMIN이면 통과 ③해당 분반 Enrollment 있나 ④role이 요구 수준 충족하나. 이 판정은 공통 컴포넌트 하나로 만들어 모든 API에 붙인다. 개별 API에서 권한 로직을 따로 짜지 않는다. 상세: docs 레포 `docs/permissions.md`
3. **분반 생성·운영진 지정은 전역 ADMIN 전용.** 최초 관리자는 DB 수동 지정(부트스트랩).
4. **마감 판정은 서버 수신 시각 기준.** 저장은 UTC, 표시는 KST.
5. **인증 금지선: 홈페이지 비밀번호를 HOJ가 직접 받는 구조 금지.** 홈페이지 로그인(구글 OAuth + 자체 로그인, 구축 중)에 연동한다. 연동 확정 전까지는 스텁 인증으로 개발한다.

## 코드 규칙

- 새 API는 Cohort 수직 슬라이스(entity → repository → service → controller → DTO → 테스트) 패턴을 그대로 따른다. 인터넷 튜토리얼 스타일 혼용 금지.
- 고위험 영역(인증, 제출 판정, 파일 업로드, 권한 공통 처리)은 PM 담당 — 다른 사람이 수정하지 않는다.
- 제출 실패(401/400/422) 응답은 프론트가 작성 내용을 보존할 수 있도록 명확한 에러 코드를 준다.
- 모든 PR은 "왜 이렇게 설계했는지"를 본문에 적는다.

## 스코프 (P1)

로그인(스텁) · 분반 CRUD+운영진 지정 · 수강생 배정 · 과제 CRUD · 제출 · 마감 판정 · 미제출자 대시보드.
출석부 / Q&A / 수강신청 흐름은 P1 제외(백로그). 자동채점(Judge0)은 P2.
