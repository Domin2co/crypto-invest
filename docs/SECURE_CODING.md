# 시큐어 코딩 기준

## 필수 규칙

- 모든 외부 입력을 allow-list·길이·형식·금액 범위로 검증한다.
- SQL은 `JdbcTemplate` parameter binding만 사용하며 동적 식별자 조합을 금지한다.
- 인증된 사용자 ID는 서버 토큰에서만 얻고 request의 사용자 ID를 신뢰하지 않는다.
- API key, 비밀번호, 토큰, DB 비밀번호, request header/body는 로그·예외·응답에 넣지 않는다.
- 오류 응답은 내부 stack trace·거래소 원문·자격증명을 노출하지 않는 고정 코드로 제한한다.
- 권한 변경, 키 저장/삭제, LIVE 주문, 개인정보 열람/삭제에는 감사 이벤트를 남긴다.
- 의존성 업데이트와 취약점 스캔은 release 전 실행하고 결과를 `TEST_RESULTS.md`에 기록한다.

## 검증 체크

- 인증 없음/변조 토큰/타 사용자 접근/권한 상승/SQL injection/XSS/CORS를 테스트한다.
- 주문 timeout은 새 주문을 재시도하지 않고 client order ID로 상태만 조회한다.
- 비밀정보가 git diff, 로그, API 응답, frontend bundle에 없는지 검색한다.
