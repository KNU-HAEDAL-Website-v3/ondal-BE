# ondal-BE

Ondal(온달) 백엔드 - Spring Boot 기반 API 서버.

> 📚 **기획·설계 문서: [ondal-docs](https://github.com/KNU-HAEDAL-Website-v3/ondal-docs)에 집약. 프로젝트 첫 진입 시 docs 레포 선행 정독 권장.**

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
  -d '{"loginId":"admin"}' -c /tmp/ondal-cookie.txt

# 내 정보 (세션 쿠키 사용)
curl localhost:8080/api/auth/me -b /tmp/ondal-cookie.txt

# 쿠키 없이 호출하면 401 {"code":"UNAUTHENTICATED", ...} 가 정상
curl -i localhost:8080/api/auth/me
```

- API 문서(Swagger UI): http://localhost:8080/swagger-ui/index.html - 프론트 계약의 기준
  - 로그인 API 선호출 시 이후 요청에 세션 쿠키 자동 첨부
- local 프로필 샘플 데이터(시더)
  - 계정: `admin`(ADMIN) / `operator1` / `student1`~`student3`
  - 분반: "2026-2 C언어"(진행 중) · "2026-1 파이썬"(보관)
  - 어떤 loginId로든 스텁 로그인 가능 → 역할별 화면 즉시 확인
- 테스트: `./gradlew test` - Testcontainers로 PostgreSQL 구동, Docker 실행 필수
- 종료: `Ctrl+C` (서버), `docker compose down` (DB - 데이터는 볼륨에 유지됨)
- 트러블슈팅: 최다 사례는 5432 포트 충돌(로컬에 다른 PostgreSQL이 떠 있는 경우) → `docker compose ps`, `lsof -i :5432`로 확인

## 기술 스택

| 항목 | 값 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1 (Spring Framework 7, Jackson 3) |
| Build | Gradle |
| DB | PostgreSQL 16 (로컬: Docker) |
| API 명세 | springdoc-openapi 3.x → `/swagger-ui/index.html`, `/v3/api-docs` |
| 테스트 | JUnit 5 + MockMvc + Testcontainers(PostgreSQL) - `src/test/java/kr/haedal/ondal/support/` 참고 |

## 인증 (P1)

- 현재: **스텁 로그인** - loginId만 전송하면 검증 없이 통과
- 홈페이지 로그인 연동 시: `AuthService` 구현체만 교체
- 상세: docs 레포 `docs/decisions/5-세션-인증-채택-spring-security-보류.md`

## 규칙

- `main` 직접 push 금지 - 모든 변경은 PR로 (승인 1명 필수, 팀원 합류 후 적용)
- API 계약(요청/응답) 변경 PR: 본문에 **[API 변경]** 명시 + 프론트에 공유
- 새 API: Cohort 수직 슬라이스 패턴 그대로 복제해 작성 - 규약: docs 레포 `docs/guide/design.md` 4절
- 도메인 패키지 내부: 계층별 하위 패키지로 분리 - `<도메인>/controller`, `service`, `repository`, `entity`, `dto` (예: `cohort/`, `enrollment/` 참고)
- `/api/**` 의 모든 핸들러: `@LoginOnly` / `@AdminOnly` / `@CohortRole` 중 하나 필수 - 누락 시 부팅 실패 (`AuthorizationMappingValidator`)
- 분반 스코프 리소스: 항상 `/api/cohorts/{cohortId}/...` 하위에 배치, 하위 id 는 서비스에서 `findByIdAndCohortId` 로 조회
