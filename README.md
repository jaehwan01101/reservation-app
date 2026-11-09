# reservation-app

공연 좌석 예약/주문 웹앱 (Spring Boot 4.1 · Java 21 · PostgreSQL).
백엔드 API 서비스. 프론트엔드는 `../frontend` 에 분리되어 S3 + CloudFront 로 배포되고, 이 서비스는 ALB 뒤 EKS 에서 `/api/**` 만 제공한다.

> 이 문서는 **인프라 담당자용 계약서**다. 포트, 프로브, 환경변수, 메트릭, 빌드 명령만 보면 배포할 수 있게 정리했다.

---

## 1. 인프라 계약 요약

| 항목 | 값 |
|---|---|
| 이미지 빌드 | Jib (Dockerfile 없음), 멀티아키 `linux/amd64` + `linux/arm64` |
| 베이스 이미지 | `eclipse-temurin:21-jre` |
| 실행 유저 | UID/GID `1000` (non-root) → `runAsNonRoot: true` 가능 |
| 앱 포트 | `8080` — `/api/**` + 프로브 (Ingress/ALB 대상) |
| 관리 포트 | `8081` — `/actuator/prometheus`, `/actuator/health`, `/actuator/info` (**외부 노출 금지**, Prometheus 스크랩 전용) |
| Liveness | `GET :8080/livez` |
| Readiness | `GET :8080/readyz` (DB 연결 포함 → DB 끊기면 트래픽만 빠지고 재시작은 안 됨) |
| ALB 헬스체크 | `/readyz` |
| 버전 확인 | `GET :8081/actuator/info` → `build.version` (내부 전용, 사용자 화면/응답에는 노출하지 않음) |
| 기동 시간 | 로컬 Docker 기준 **약 17초** (Flyway 포함) → `startupProbe` 필수 |
| 종료 | SIGTERM 후 graceful shutdown 최대 **20초** → `terminationGracePeriodSeconds` 는 30 이상 |
| JVM 메모리 | `-XX:MaxRAMPercentage=75` 내장 → **memory limit 으로 힙 크기가 결정됨** |
| DB | PostgreSQL 13+ (RDS). 스키마는 앱 기동 시 Flyway 가 자동 마이그레이션 |
| 프론트엔드 | **포함하지 않음** (`../frontend` → S3 + CloudFront). 이 서비스는 `/api/**` 만 제공 |

### 권장 리소스 (시작값)
```yaml
resources:
  requests: { cpu: 250m, memory: 768Mi }
  limits:   { memory: 768Mi }   # CPU limit 은 걸지 않음 (JVM 기동 시 throttling 방지)
```

### 권장 프로브
```yaml
startupProbe:
  httpGet: { path: /livez, port: 8080 }
  periodSeconds: 5
  failureThreshold: 24        # 최대 120초까지 기동 대기
livenessProbe:
  httpGet: { path: /livez, port: 8080 }
  periodSeconds: 10
readinessProbe:
  httpGet: { path: /readyz, port: 8080 }
  periodSeconds: 5
lifecycle:
  preStop:
    exec: { command: ["sh", "-c", "sleep 10"] }   # ALB 타깃 해제 전파 대기
```

---

## 2. 환경변수

| 변수 | 기본값 | 설명 |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/reservation` | 예: `jdbc:postgresql://<rds-endpoint>:5432/reservation` |
| `DB_USERNAME` | `reservation` | **Secret 으로 주입** (External Secrets 권장) |
| `DB_PASSWORD` | `reservation` | **Secret 으로 주입** |
| `DB_POOL_SIZE` | `10` | HikariCP 풀 크기. `Pod 수 × 풀 크기 < RDS max_connections` 확인 |
| `APP_FAULT_ERROR_RATE` | `0` | 장애 주입: `/api/**` 요청 중 이 비율(0~1)만큼 500 응답 |
| `APP_FAULT_LATENCY_MS` | `0` | 장애 주입: `/api/**` 요청마다 지연(ms) 추가 |
| `LOGGING_STRUCTURED_FORMAT_CONSOLE` | (없음) | `ecs` 로 설정 시 JSON 로그 → CloudWatch/Fluent Bit 파싱 용이 |
| `JAVA_TOOL_OPTIONS` | (없음) | JVM 옵션 추가가 필요할 때 |

> DB 비밀번호가 바뀌면 **Pod 재시작이 필요**하다 (Spring 은 기동 시에만 읽음). Reloader 등 검토.

---

## 3. 빌드 (CodeBuild 에서 쓸 명령)

```bash
# 테스트 (H2 인메모리 사용, 외부 DB 불필요)
./gradlew test

# ECR 로 빌드+푸시 (Docker 데몬 불필요, privileged 모드 불필요)
export APP_VERSION=$(echo $CODEBUILD_RESOLVED_SOURCE_VERSION | cut -c1-7)
export IMAGE_REPO=<account>.dkr.ecr.<region>.amazonaws.com/reservation-app
aws ecr get-login-password | docker login --username AWS --password-stdin ${IMAGE_REPO%%/*}
./gradlew jib
# → ${IMAGE_REPO}:${APP_VERSION} 으로 푸시됨
```

- `APP_VERSION` 이 이미지 태그와 `/actuator/info` 의 `build.version` 에 **동일하게** 들어간다.
- `docker login` 대신 `amazon-ecr-credential-helper` 를 써도 된다 (Jib 이 자동 인식).
- CodeBuild 런타임은 Java 21 이상 이미지 사용 (Gradle 은 wrapper 가 자동 다운로드).

---

## 4. 메트릭 (Prometheus, `:8081/actuator/prometheus`)

| 메트릭 | 용도 |
|---|---|
| `http_server_requests_seconds_count{status, uri, outcome}` | 에러율 (canary 분석) |
| `http_server_requests_seconds_bucket` | p95/p99 지연시간 (히스토그램 활성화됨) |
| `reservation_events_total{result="created\|cancelled\|conflict"}` | 비즈니스 지표 |
| `hikaricp_connections_active` 등 | DB 커넥션 풀 |
| `jvm_memory_used_bytes`, `jvm_gc_pause_seconds` | JVM |

모든 메트릭에 `application="reservation-app"` 태그가 붙는다.

### Argo Rollouts AnalysisTemplate 쿼리 예시
```promql
# 5xx 에러율 (프로브 경로 제외)
sum(rate(http_server_requests_seconds_count{application="reservation-app", uri!~"/livez|/readyz", status=~"5..", pod=~"{{args.pod-selector}}"}[1m]))
/
sum(rate(http_server_requests_seconds_count{application="reservation-app", uri!~"/livez|/readyz", pod=~"{{args.pod-selector}}"}[1m]))
```
> `pod` 라벨은 Prometheus 스크랩 설정(ServiceMonitor/PodMonitor)에서 붙는다. 장애 주입으로 생긴 500 은 핸들러를 거치지 않아 `uri="UNKNOWN"` 으로 집계되니, **uri 로 필터링하지 말고 status 로** 판단할 것.

---

## 5. 장애 주입 — canary 자동 롤백 시연

새 revision 의 Pod 템플릿에만 env 를 넣으면 "버그 있는 배포"가 된다.

```yaml
env:
  - name: APP_FAULT_ERROR_RATE
    value: "0.3"      # 30% 요청 실패
```

- 프로브(`/livez`, `/readyz`)는 영향받지 않는다 → K8s 가 Pod 를 죽이지 않고, **Rollouts 분석이 실패를 판단**하게 된다.
- 사용자 화면에는 아무 표시도 없다 (실서비스처럼 "일시적인 오류" 메시지만 뜸).
- 트래픽이 적으면 통계가 의미 없으므로 부하 생성기(k6 등)를 같이 돌릴 것.
- 진행 상황은 인프라 도구로 본다: `kubectl argo rollouts get rollout reservation-app --watch`, Argo Rollouts 대시보드, Grafana(Pod/버전별 에러율).
- 실무에서는 앱 내장 대신 **Chaos Mesh / AWS FIS** 같은 카오스 도구로 장애를 주입하기도 한다. 이 플래그는 기본값 0 이고 env 로만 켤 수 있어 사용자에게는 영향이 없다.

---

## 6. DB 마이그레이션 주의 (인프라와 맞물리는 부분)

- Flyway 는 **앱 기동 시** 실행된다. 여러 Pod 가 동시에 떠도 Flyway 잠금 테이블로 한 번만 실행된다.
- Canary 중에는 **구버전과 신버전이 같은 DB 를 쓴다** → 신버전이 기동하면서 스키마를 먼저 바꾼다.
  따라서 마이그레이션은 항상 **하위호환(expand → contract)** 으로 작성한다:
  1. 배포 N: 컬럼 추가 (nullable), 구버전도 그대로 동작
  2. 배포 N+1: 신규 컬럼 사용, 구 컬럼 미사용
  3. 배포 N+2: 구 컬럼 삭제
- **Rollout 롤백은 DB 를 되돌리지 않는다.** 롤백 대상 버전이 현재 스키마에서 동작해야 한다.
- 운영 규모가 커지면 마이그레이션을 앱 기동에서 분리(Argo CD PreSync Job)하는 것을 검토한다.

---

## 7. 로컬 실행

```bash
# A. DB 없이 (H2 인메모리)
./gradlew bootRun --args='--spring.profiles.active=local'

# B. 운영과 같은 조건 (PostgreSQL + 컨테이너 이미지)
./gradlew jibBuildTar -Djib.from.platforms=linux/amd64
docker load -i build/jib-image.tar
docker compose up -d
```

API 는 http://localhost:8080 , 프론트 포함 전체 화면은 http://localhost:3000 (nginx 가 CloudFront 역할).

로컬에 JDK 가 없으면 Docker 로 Gradle 실행:
```bash
docker run --rm -v "$PWD":/app -w /app gradle:9.8.2-jdk21 ./gradlew test
```

---

## 8. API

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/performances` | 공연 목록 (잔여석 포함) |
| GET | `/api/performances/{id}/seats` | 좌석 목록 |
| POST | `/api/reservations` | 예약 `{"seatId": 1, "customerName": "홍길동"}` → 201, 이미 예약된 좌석 409 |
| GET | `/api/reservations/{code}` | 예약 조회 |
| DELETE | `/api/reservations/{code}` | 예약 취소 (이미 취소 시 409) |

에러는 모두 RFC 9457 `application/problem+json` 형식. 4xx 는 클라이언트 오류, **5xx 만 장애 지표로 본다.**

동시성: 같은 좌석에 동시 예약이 오면 `SELECT ... FOR UPDATE` 로 하나만 성공한다 (테스트로 검증).

---

## 9. 프로젝트 구조

```
src/main/java/com/eksproject/reservation/
├── performance/   공연 + 좌석 조회 API
├── seat/          좌석 엔티티, 비관적 락 조회
├── reservation/   예약 생성/조회/취소, 비즈니스 메트릭
├── common/        예외, ProblemDetail 핸들러
└── ops/           장애 주입 필터 (기본 비활성)
src/main/java/db/migration/   Flyway Java 마이그레이션 (시드 데이터)
src/main/resources/
├── db/migration/  Flyway SQL 마이그레이션
├── application.yml
└── application-local.yml   H2 프로필
```
