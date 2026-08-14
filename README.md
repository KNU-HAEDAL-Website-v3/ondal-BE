# haedal-online-judge-backend

HOJ(Haedal Online Judge) 백엔드 — Spring Boot 기반 API 서버.

> 📚 **기획·설계 문서는 [haedal-online-judge-docs](https://github.com/KNU-HAEDAL-Website-v3/haedal-online-judge-docs)에 모여 있습니다. 이 프로젝트가 처음이라면 거기부터 읽어주세요.**

## 실행법

TODO — Spring 스켈레톤 커밋 시 작성 예정입니다.

## 기술 스택

| 항목 | 값 |
|---|---|
| Language | Java (버전은 스켈레톤 확정 시 기록) |
| Framework | Spring Boot |
| Build | Gradle |
| DB | PostgreSQL |
| API 명세 | springdoc-openapi 자동 생성 |

## 규칙

- `main` 직접 push 금지 — 모든 변경은 PR로 (승인 1명 필수, 팀원 합류 후 적용)
- API 계약(요청/응답)이 바뀌는 PR은 본문에 **[API 변경]** 을 명시하고 프론트에 공유
- 새 API는 Cohort 수직 슬라이스(추가 예정) 패턴을 따라 작성 — 이 레포의 기존 코드가 스타일 기준
