# 운영·장애·사고 대응

## 운영 원칙

- health, DB/Flyway, 주문 상태, 거래소 오류율/429, timeout, UNKNOWN 주문, kill switch 상태를 모니터링한다.
- 알림에는 주문 ID·상태·오류 코드만 포함하고 자격증명·Authorization header는 포함하지 않는다.
- 배포 전 migration backup/rollback 계획, 테스트 결과, LIVE 설정 검토를 기록한다.

## 장애 및 보안 사고

1. 즉시 `LIVE_TRADING_ENABLED=false`와 `LIVE_TRADING_KILL_SWITCH=true`로 신규 LIVE 주문을 중지한다. kill switch 기본값은 `true`다.
2. UNKNOWN 주문은 client order ID로 거래소 상태를 조회하고 재전송하지 않는다.
3. API key 노출 의심 시 해당 key를 거래소에서 폐기·재발급하고 DB 계정을 비활성화한다.
4. 영향 범위, 로그(비밀값 제외), 시간대, 조치, 사용자 통지/신고 필요성을 기록한다.
5. 복구 뒤 원인 분석, 재발 방지 테스트, `TEST_RESULTS.md`와 TODO 후속 작업을 갱신한다.

## 복구 훈련

출시 전 PAPER 환경에서 kill switch, DB 복구, timeout/UNKNOWN 주문, credential 폐기, 권한 철회
시나리오를 연습하고 결과를 테스트 기록에 남긴다.

## Authentication ingress

Set `TRUSTED_PROXY_CIDRS` to the exact ingress proxy CIDRs before routing traffic through a reverse proxy. Configure the proxy to overwrite incoming `X-Forwarded-For`; never trust all networks. With an empty setting, authentication rate limits use the direct socket peer address and ignore forwarded headers.

## SMTP email delivery

Local development uses Compose Mailpit (`localhost:1025`, inbox at `localhost:8025`). In deployment, set `SPRING_PROFILES_ACTIVE=production` and provide `SMTP_HOST`, `SMTP_PORT` (default 587), `SMTP_USERNAME`, `SMTP_PASSWORD`, and verified sender `EMAIL_FROM` through a secret manager or deployment environment. The production profile requires SMTP authentication and STARTTLS, with server certificate hostname validation. Never store the SMTP password in Git, logs, or frontend assets.

Before enabling real delivery, create the provider account and sender domain, publish SPF, DKIM, and DMARC DNS records, and verify delivery of signup and password reset messages.

## Monthly PAPER league recovery

The scheduler retries opening and closing valuations every minute throughout the first day of the Seoul calendar month, including immediately after backend startup. PAPER fills for an enrolled participant are blocked until the month opening snapshot is saved. Each successful valuation stores its quote capture timestamp with the opening or final KRW value; the board reports the close snapshot time. If valuation cannot be recovered during that day, the entry remains pending and receives no ranking or badge until an operator resolves it. The stored timestamp is the actual quote time and should be considered when reviewing a delayed settlement.

## Monthly PAPER settlement outage policy

Opening and closing values are retried through Seoul time 23:59 on the first calendar day. The first successful valuation records its actual quote capture time. If the recovery window passes, no value is backdated or estimated: the affected entry stays unranked and earns no badge. A later month is not blocked. The public board must not present an incomplete entry as a result.

/actuator/health now includes a paperLeague component. It reports DOWN when an opening snapshot remains pending after the first day or a past-month closing snapshot remains pending. Component status is visible, while component details remain hidden on the public endpoint. Configure the deployment monitor to alert when health is not UP; reconcile the month and notify affected participants through the approved support channel. Do not manually invent or backdate valuations.

## Discussion reports and privacy requests

Use the ADMIN report queue for report intake and record each decision through the existing hide, dismiss, and restore actions. As an internal service target (not a statement of statutory deadlines), acknowledge a report within one business day and provide a decision or progress update within five business days. Triage credible immediate safety or unlawful-content concerns first; restrict access while review is underway when needed, preserve only the minimum evidence, and record the reason. Keep reporter identity private from the reported user. Provide an appeal/support route and have a second operator review contested decisions when available.

For account access, correction, or deletion requests, authenticate the requester and verify ownership before action. Record request/verification/completion times and outcome without copying credentials or API secrets into notes. The privacy owner must confirm the legally applicable response deadlines and retention period before public launch. Apply account deletion to application data and define when corresponding backup copies expire; do not promise immediate erasure from immutable backups.

## PostgreSQL backup and restore verification

Run scripts/backup/verify-postgres-backup.ps1 on a host with Docker Compose access. By default, the script stores the custom-format dump under the current user's Local AppData, restores it into a uniquely named temporary database in the local Compose PostgreSQL container, verifies that the restored database responds, then removes the temporary database and container-side dump. It does not alter the source database. Restrict access to the backup file because it contains personal and account data. Set -OutputDirectory to an approved encrypted location when needed.

Schedule the script at the deployment's approved backup frequency, monitor its exit code, and define retention/RPO/RTO with the service owner. Keep production backups in an access-controlled off-host or managed backup system; do not treat the development Compose volume as a backup. Before applying a production migration, verify a recent backup. Periodically restore into an isolated environment, run migrations and application smoke checks there, and record duration, errors, and recovery point. Never test restore over the live database.

## Monitoring checks

Poll /actuator/health from the deployment monitor. Alert on non-200 responses or an overall status other than UP; inspect component status to distinguish database/disk and paperLeague failures. Keep health details disabled and do not send request bodies, email addresses, API keys, authorization headers, or secrets to alert systems. Route application logs for exchange 429/timeouts, unresolved order states, scheduler failures, and kill-switch changes to the operator alert channel. The repository provides health status and safe scheduler logs; alert delivery, exchange error-rate dashboards, and unknown-order reconciliation dashboards still require deployment-side monitoring integration.
