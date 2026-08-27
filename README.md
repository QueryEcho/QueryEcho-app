# QueryEcho

QueryEcho는 Java/Spring 애플리케이션의 JDBC 쿼리와 트랜잭션을 수집하고, 셀프호스팅 대시보드에서 함께 추적하는 DB 모니터링 프로젝트입니다.

## 서버 실행

Docker와 Docker Compose가 필요합니다.

```bash
cp .env.example .env
# .env의 DB 비밀번호와 수집 API 키를 임의의 긴 값으로 변경
docker compose up -d
```

대시보드는 기본적으로 `http://localhost:8080`에서 열립니다. PostgreSQL은 DBeaver 확인용으로 로컬 인터페이스의 `5433` 포트에만 노출됩니다.

저장소 코드를 직접 빌드해 실행하려면 다음 명령을 사용합니다.

```bash
docker compose -f docker-compose.yml -f compose.dev.yml up -d --build
```

## SDK 적용

Maven Central 배포 후 Spring Boot 애플리케이션에 다음 의존성을 추가합니다.

```groovy
dependencies {
    implementation 'io.github.queryecho:queryecho-sdk:0.1.0'
}
```

타깃 애플리케이션에는 다음 설정을 추가합니다.

```properties
queryecho.sdk.enabled=true
queryecho.sdk.transport=HTTP
queryecho.sdk.collector-url=http://queryecho.example.com:8080
queryecho.sdk.api-key=${QUERYECHO_INGEST_API_KEY}
queryecho.sdk.app-name=orders-api
queryecho.sdk.environment=production
queryecho.sdk.instance-id=${HOSTNAME:unknown}
queryecho.sdk.db-type=postgresql
```

SDK는 DataSource와 Connection을 프록시로 감싸 JDBC 실행 시간을 측정합니다. 이벤트 전송은 업무 요청 스레드를 막지 않도록 별도 메모리 큐에서 배치로 처리합니다. Collector 장애 시 모니터링 때문에 원래 서비스가 멈추지 않도록 큐 상한과 요청 제한 시간을 둡니다.

## 배포 구조

- 서버 이미지: `ghcr.io/queryecho/queryecho-app:<version>`
- SDK: `io.github.queryecho:queryecho-sdk:<version>`
- `v0.1.0` 형식의 Git 태그를 푸시하면 GitHub Actions가 두 산출물을 각각 배포합니다.

Maven Central 최초 배포에 필요한 계정·서명·GitHub Secret 설정은 [docs/PUBLISHING.md](docs/PUBLISHING.md)에 정리되어 있습니다.

## 라이선스

Apache License 2.0

