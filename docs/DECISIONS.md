# DECISIONS.md

이 문서는 주요 기술 / 아키텍처 결정을 기록한다.

새로운 중요한 결정을 내릴 때 아래 형식으로 추가한다.

---

## ADR-001 — Backend는 Java 21 + Spring Boot를 사용한다

### 상태

결정됨

### 결정

Backend는 Java 21과 Spring Boot 3.x를 사용한다.

### 이유

- Java 기반 개발 경험 활용
- 장기 지원 버전 사용
- Spring Security / JPA / WebClient 생태계 활용
- 테스트 도구 성숙도

---

## ADR-002 — PostgreSQL을 기본 DB로 사용한다

### 상태

결정됨

### 이유

- 무료 오픈소스
- 금융 데이터 처리에 적합
- Docker / Testcontainers 지원 우수
- 향후 확장성

---

## ADR-003 — Schema 변경은 Flyway로 관리한다

### 상태

결정됨

### 이유

DB 변경 이력을 코드와 함께 관리하고 환경 간 차이를 줄이기 위함.

---

## ADR-004 — 초기 아키텍처는 Modular Monolith로 한다

### 상태

결정됨

### 이유

초기 개인 프로젝트에서 Microservice는 운영 복잡도가 지나치게 크다.

도메인 경계를 명확히 하되 단일 Application으로 시작한다.

---

## ADR-005 — 유료 AI API를 기본 사용하지 않는다

### 상태

결정됨

### 이유

운영 비용을 최소화하고 추천 결과를 재현 가능하게 하기 위함.

추천은 기술지표와 설정 가능한 규칙을 이용한다.

---

## ADR-006 — 거래 기본값은 PAPER로 한다

### 상태

결정됨

### 이유

개발 / 자동테스트 중 실제 자산 손실 위험을 줄이기 위함.

---

## ADR-007 — Docker Desktop을 개발 환경으로 사용한다

### 상태

결정됨

### 이유

Windows에서 PostgreSQL / Redis / Testcontainers 환경을 일관되게 구성하기 쉽고 자료가 풍부하다.

개인 개발 환경 기준으로 사용한다.

---

## ADR 추가 Template

```text
## ADR-XXX — 제목

### 상태
제안 / 결정됨 / 폐기

### 배경

### 결정

### 이유

### 대안

### 영향
```
