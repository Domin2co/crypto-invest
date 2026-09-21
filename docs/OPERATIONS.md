# 운영·장애·사고 대응

## 운영 원칙

- health, DB/Flyway, 주문 상태, 거래소 오류율/429, timeout, UNKNOWN 주문, kill switch 상태를 모니터링한다.
- 알림에는 주문 ID·상태·오류 코드만 포함하고 자격증명·Authorization header는 포함하지 않는다.
- 배포 전 migration backup/rollback 계획, 테스트 결과, LIVE 설정 검토를 기록한다.

## 장애 및 보안 사고

1. 즉시 `LIVE_TRADING_ENABLED=false` 및 kill switch로 신규 주문을 중지한다.
2. UNKNOWN 주문은 client order ID로 거래소 상태를 조회하고 재전송하지 않는다.
3. API key 노출 의심 시 해당 key를 거래소에서 폐기·재발급하고 DB 계정을 비활성화한다.
4. 영향 범위, 로그(비밀값 제외), 시간대, 조치, 사용자 통지/신고 필요성을 기록한다.
5. 복구 뒤 원인 분석, 재발 방지 테스트, `TEST_RESULTS.md`와 TODO 후속 작업을 갱신한다.

## 복구 훈련

출시 전 PAPER 환경에서 kill switch, DB 복구, timeout/UNKNOWN 주문, credential 폐기, 권한 철회
시나리오를 연습하고 결과를 테스트 기록에 남긴다.
