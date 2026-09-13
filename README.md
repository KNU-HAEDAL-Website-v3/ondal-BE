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
# 로그인 - local 프로필은 스텁(loginId 만 보내면 통과). admin은 시더가 만든 관리자, 다른 아무 loginId를 넣으면 MEMBER로 새로 생성됨
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
- 기동 실패 `Found non-empty schema(s) "public" but no schema history table` 또는 `Schema-validation: missing table/column` → Flyway 이전 방식(ddl-auto update)으로 만든 개발 DB → `docker compose down -v && docker compose up -d` 후 재기동 (샘플 데이터는 시더가 재생성)

## 기술 스택

| 항목 | 값 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1 (Spring Framework 7, Jackson 3) |
| Build | Gradle |
| DB | PostgreSQL 16 (로컬: Docker) |
| DB 마이그레이션 | Flyway - `src/main/resources/db/migration/V{n}__{설명}.sql`, Hibernate `ddl-auto: validate` (DB를 고치지 않고 대조만) |
| API 명세 | springdoc-openapi 3.x → `/swagger-ui/index.html`, `/v3/api-docs` |
| 테스트 | JUnit 5 + MockMvc + Testcontainers(PostgreSQL) - `src/test/java/kr/haedal/ondal/support/` 참고 |

## 운영 배포 (해달 서버)

- 위치: 공용 서버 `/opt/haedal/3-haedal-ondal/ondal-BE` (git clone, `main` 추적) - JDK 불필요, 이미지 안에서 빌드
- 주소: API `https://ondal-api.haedal-sos-man-in-the-mirror.com` · FE `https://ondal.haedal-sos-man-in-the-mirror.com`
- 트래픽 경로: Cloudflare Tunnel(`0-haedal-infra/cloudflared`) → nginx(`0-haedal-infra/nginx`, 컨테이너 `nginx`, :80) → 외부 도커 네트워크 `ngx_to_srv` → `ondal-be:8080`
  - nginx 설정: `/opt/haedal/0-haedal-infra/nginx/conf.d/ondal-api.conf` - `client_max_body_size 15m`(zip 10MB + multipart 여유), `X-Forwarded-Proto https` 고정(nginx는 :80으로 받으므로 `$scheme`을 쓰면 http가 됨)
  - TLS 종단·HTTPS 강제(Always Use HTTPS)는 Cloudflare 대시보드 - 앱은 HTTP만 받고 `forward-headers-strategy: framework`로 https 를 인지
- 인증: 홈페이지 Keycloak 로그인(OIDC, `ondal.auth.mode=oidc`) - BE 가 공개 주소(`auth.` 도메인, Cloudflare 경유)로 Discovery·토큰 교환·JWKS 를 호출. 설정은 `.env` 의 `OIDC_*`, 상세는 아래 "인증" 절
- DB: 같은 compose의 `db` 컨테이너(PostgreSQL 16), 볼륨 `ondal-db-data` - 호스트 포트 미노출
- 제출 파일: 볼륨 `ondal-uploads` → 컨테이너 `/app/uploads`
- 인프라 소유: `0-haedal-infra`는 인프라 담당 관리 - nginx 설정 변경은 협의 후, 서버 안내는 `/opt/haedal/SERVER-GUIDE.md`

### 절차

| 단계 | 명령 (서버, 레포 루트) |
|---|---|
| 최초 1회 | `cp .env.example .env` 후 `DB_PASSWORD` 채우기 (`openssl rand -base64 24`) |
| 배포·재배포 | `git pull && sudo docker compose up -d --build` (.env의 `COMPOSE_FILE`이 prod 파일을 지정) |
| .env 변경 반영 | `sudo docker compose up -d` (컨테이너 재생성 - `restart`만으로는 미반영) |
| 로그 | `sudo docker compose logs -f ondal-be` |
| 확인 | `curl https://ondal-api.haedal-sos-man-in-the-mirror.com/api/health` → `{"status":"UP"}` |
| nginx 설정 변경 후 | `sudo docker exec nginx nginx -t && sudo docker exec nginx nginx -s reload` |
| Flyway 전환 배포 (1회) | 운영 DB가 Flyway 이전(ddl-auto update)에 생성된 경우: 데이터 폐기 가능하면 `sudo docker compose down && sudo docker volume rm <프로젝트>_ondal-db-data` 후 배포 (권장 - 실사용 전). 보존해야 하면 .env에 `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true` 를 넣고 1회 배포 후 제거 - 단 제약 이름·CHECK가 V1과 달라 후속 마이그레이션 전에 정리 필요 |

### 최초 관리자 (부트스트랩)

- 운영에는 시더가 돌지 않음 - 관리자 1명을 수동 SQL로 지정 (docs 레포 `permissions.md` 4절). 이후 분반 생성·운영진 지정은 이 관리자가 화면에서 진행

1. 관리자가 될 홈페이지 계정으로 FE 의 "홈페이지 계정으로 로그인" 을 1회 진행 → `users` 행 생성(MEMBER)
2. 승격 (loginId = 홈페이지(Keycloak) username - `/api/auth/me` 응답의 `loginId` 로 확인):

```bash
sudo docker compose exec db psql -U ondal -d ondal \
  -c "UPDATE users SET global_role = 'ADMIN' WHERE login_id = '<loginId>';"
```

3. 로그아웃 후 재로그인 → `/api/auth/me` 응답의 `globalRole`이 `ADMIN`

### 환경 변수 (.env)

| 키 | 필수 | 기본값(prod 프로필) | 설명 |
|---|---|---|---|
| `COMPOSE_FILE` | 권장 | - | `docker-compose.prod.yml` - `-f` 생략용 |
| `DB_PASSWORD` | 필수 | - | PostgreSQL 비밀번호 - db·app 양쪽에 주입 |
| `CORS_ORIGINS` | 선택 | FE 커스텀 도메인 | 쉼표 구분, 공백 금지 |
| `COOKIE_SAMESITE` | 선택 | `lax` | FE·API가 같은 등록 도메인이라 lax 가능. cross-site FE(*.pages.dev)일 때만 `none` |
| `COOKIE_SECURE` | 선택 | `true` | HTTPS 전제. http 직접 테스트 때만 `false` |
| `OIDC_ISSUER` | 필수 | - | Keycloak realm 의 issuer (예: `https://auth.<도메인>/realms/<realm>`) - 비면 기동 실패 |
| `OIDC_CLIENT_SECRET` | 필수 | - | Keycloak 클라이언트 `ondal-be`(confidential) 의 secret - 비면 기동 실패 |
| `OIDC_CLIENT_ID` | 선택 | `ondal-be` | Keycloak 클라이언트 ID |
| `OIDC_REDIRECT_URI` | 선택 | API 도메인 `/api/auth/callback` | Keycloak 클라이언트의 Valid redirect URIs 와 문자 단위 일치 |
| `FE_URL` | 선택 | FE 커스텀 도메인 | 로그인 후 복귀·실패 안내(`/login?error=`)·로그아웃 후 복귀 주소의 밑동 |

### 주의

- 운영 로그인은 홈페이지(Keycloak) 계정만 - 스텁(`ondal.auth.mode=stub`)은 prod 프로필에서 기동 자체가 거부된다(`StubAuthService`). `OIDC_ISSUER`·`OIDC_CLIENT_SECRET` 이 비어도 기동 실패
- Keycloak 쪽 선행 조건(realm·클라이언트·redirect URI·nginx `keycloak.conf`)은 인프라 담당 소관 - 미완이면 로그인 시 FE `/login?error=OIDC_UNAVAILABLE`
- `docker compose down -v`는 DB·업로드 볼륨을 모두 지움 - DB만 초기화하려면 `sudo docker compose down && sudo docker volume rm <프로젝트>_ondal-db-data` (이름은 `sudo docker volume ls`로 확인)
- 세션은 인메모리 - 재배포마다 전원 로그아웃 (docs 결정 5에서 감수)

## 인증

- 세션(HttpSession 쿠키) + 인터셉터 + `@LoginUser` - docs 결정 5. 로그인 방식만 `ondal.auth.mode` 로 갈린다 (`auth/AuthMode.java`)

| 모드 | 프로필 | 로그인 | 구현 |
|---|---|---|---|
| `stub` (기본) | local·test | `POST /api/auth/login` `{loginId}` - 검증 없이 통과, 없으면 MEMBER 생성 | `StubAuthController` + `StubAuthService` (prod 에서는 기동 거부) |
| `oidc` | prod | `GET /api/auth/login?returnTo=` → 홈페이지(Keycloak) 로그인 → `GET /api/auth/callback` → 세션 발급 후 FE 로 302 | `OidcAuthController` + `OidcAuthService` + `auth/oidc/` |

- 홈페이지 연동 방식: OIDC 인가 코드 + PKCE, **BE 가 코드를 교환**(confidential client) - 토큰이 브라우저 JS 에 노출되지 않고, 이후 API 인증은 세션 그대로 (docs 결정 7)
  - ID 토큰 검증: 서명(JWKS)·iss·aud·azp·exp·nonce. `preferred_username` → `loginId`, `name` → 이름(매 로그인 갱신). Ondal 은 비밀번호를 받지 않는다
  - 실패: FE `/login?error=코드` 로 302 - 코드 목록은 `auth/oidc/OidcLoginError.java`
  - 로그아웃 `POST /api/auth/logout` 응답의 `logoutUrl`(oidc 모드) 로 FE 가 이동하면 홈페이지 세션까지 종료(공용 PC 대비). 스텁 모드는 `null`
  - 첫 사용 시 Discovery(`{issuer}/.well-known/openid-configuration`)로 엔드포인트를 받는다 - 기동 시 Keycloak 을 부르지 않으므로 IdP 가 죽어 있어도 앱은 뜬다
- 공통(모드 무관): `AuthController`(`/me`·`/logout`), `LoginSession`(세션 고정 방지 재발급), `AuthPaths.PUBLIC`(면제 경로 단일 목록)
- 테스트: 스텁 `AuthApiTest`, 홈페이지 로그인 `OidcAuthApiTest`(`support/FakeOidcProvider` 가 Keycloak 대역 - RSA 서명·PKCE 대조까지 실제 수행)

## 규칙

- `main` 직접 push 금지 - 모든 변경은 PR로 (승인 1명 필수, 팀원 합류 후 적용)
- API 계약(요청/응답) 변경 PR: 본문에 **[API 변경]** 명시 + 프론트에 공유
- 새 API: Cohort 수직 슬라이스 패턴 그대로 복제해 작성 - 규약: docs 레포 `docs/guide/design.md` 4절
- 도메인 패키지 내부: 계층별 하위 패키지로 분리 - `<도메인>/controller`, `service`, `repository`, `entity`, `dto` (예: `cohort/`, `enrollment/` 참고)
- `/api/**` 의 모든 핸들러: `@LoginOnly` / `@AdminOnly` / `@CohortRole` 중 하나 필수 - 누락 시 부팅 실패 (`AuthorizationMappingValidator`)
- 분반 스코프 리소스: 항상 `/api/cohorts/{cohortId}/...` 하위에 배치, 하위 id 는 서비스에서 `findByIdAndCohortId` 로 조회
- 스키마 변경: 엔티티 수정 + `V{n}__{설명}.sql` 추가를 같은 PR에서 - 적용된 마이그레이션 파일은 수정 금지, 제약 이름은 docs 레포 `db/schema.md` 를 따른다
