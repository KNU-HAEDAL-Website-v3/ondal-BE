# haedal-online-judge-backend

HOJ(Haedal Online Judge) 백엔드 — Spring Boot 기반 API 서버.

> 📚 **기획·설계 문서는 [haedal-online-judge-docs](https://github.com/KNU-HAEDAL-Website-v3/haedal-online-judge-docs)에 모여 있습니다. 이 프로젝트가 처음이라면 거기부터 읽어주세요.**

## 실행법

사전 준비: JDK 21, Docker

```bash
# 1. DB 띄우기 (최초 1회 이후엔 자동 재사용)
docker compose up -d

# 2. 서버 실행
./gradlew bootRun
```

동작 확인:

```bash
# 로그인 (admin은 local 프로필에서 자동 생성된 관리자. 다른 아무 loginId를 넣으면 MEMBER로 새로 생성됨)
curl -i -X POST localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"loginId":"admin"}' -c /tmp/hoj-cookie.txt

# 내 정보 (세션 쿠키 사용)
curl localhost:8080/api/auth/me -b /tmp/hoj-cookie.txt

# 쿠키 없이 호출하면 401 {"code":"UNAUTHENTICATED", ...} 가 정상
curl -i localhost:8080/api/auth/me
```

API 문서(Swagger UI): http://localhost:8080/swagger-ui/index.html — 프론트 계약의 기준. 로그인 API를 먼저 호출하면 이후 요청에 세션 쿠키가 자동으로 붙습니다.

local 프로필 샘플 데이터(시더): 계정 `admin`(ADMIN) / `operator1` / `student1`~`student3`, 분반 "2026-2 C언어"(진행 중) · "2026-1 파이썬"(보관). 어떤 loginId로든 스텁 로그인이 되므로 역할별 화면을 바로 확인할 수 있습니다.

테스트: `./gradlew test` — Testcontainers로 PostgreSQL을 띄우므로 Docker가 실행 중이어야 합니다.

종료: `Ctrl+C` (서버), `docker compose down` (DB. 데이터는 볼륨에 유지됨)

문제가 생기면: 5432 포트 충돌(로컬에 다른 PostgreSQL이 떠 있는 경우)이 가장 흔합니다. `docker compose ps`와 `lsof -i :5432`로 확인하세요.

## 기술 스택

| 항목 | 값 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1 (Spring Framework 7, Jackson 3) |
| Build | Gradle |
| DB | PostgreSQL 16 (로컬: Docker) |
| API 명세 | springdoc-openapi 3.x → `/swagger-ui/index.html`, `/v3/api-docs` |
| 테스트 | JUnit 5 + MockMvc + Testcontainers(PostgreSQL) — `src/test/java/kr/haedal/hoj/support/` 참고 |

## 인증 (P1)

현재는 **스텁 로그인**입니다 — loginId만 보내면 검증 없이 통과. 홈페이지 로그인 연동 시 `AuthService` 구현체만 교체합니다.
상세: docs 레포 `docs/decisions/5-세션-인증-채택-spring-security-보류.md`

## 규칙

- `main` 직접 push 금지 — 모든 변경은 PR로 (승인 1명 필수, 팀원 합류 후 적용)
- API 계약(요청/응답)이 바뀌는 PR은 본문에 **[API 변경]** 을 명시하고 프론트에 공유
- 새 API는 Cohort 수직 슬라이스 패턴(`cohort/`, `enrollment/` 패키지)을 그대로 복제해 작성 — 규약은 docs 레포 `docs/cohort/design.md` §4
- `/api/**` 의 모든 핸들러는 `@LoginOnly` / `@AdminOnly` / `@CohortRole` 중 하나를 단다. 빠지면 부팅이 실패한다 (`AuthorizationMappingValidator`)
- 분반 스코프 리소스는 항상 `/api/cohorts/{cohortId}/...` 아래에 두고, 하위 id 는 서비스에서 `findByIdAndCohortId` 로 조회한다
