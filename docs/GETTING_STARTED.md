# QueryEcho 사용 가이드

이 문서는 QueryEcho 서버와 대시보드를 Docker로 실행하고, Spring Boot 애플리케이션에 QueryEcho SDK를 적용하는 방법을 설명한다.

현재 SDK의 기준 환경은 Java 21과 Spring Boot 4.1이다.

## 저장소 준비

1. Git과 Docker Desktop을 설치하고 Docker가 실행 중인지 확인한다.

    ```bash
    docker version
    docker compose version
    ```

1. QueryEcho 저장소를 복제한다.

    ```bash
    git clone https://github.com/QueryEcho/QueryEcho-app.git
    cd QueryEcho-app
    ```

1. 저장소 루트 경로를 확인한다.

    ```bash
    # bash/zsh
    REPOSITORY_ROOT=$(git rev-parse --show-toplevel)
    cd "$REPOSITORY_ROOT"
    ```

    ```powershell
    # PowerShell
    $REPOSITORY_ROOT = git rev-parse --show-toplevel
    Set-Location $REPOSITORY_ROOT
    ```

## Docker 이미지로 서버와 대시보드 실행

QueryEcho 서버 이미지는 Collector API와 대시보드를 함께 제공한다. 수집 데이터는 별도의 PostgreSQL 컨테이너에 저장된다.

1. 저장소 루트에서 환경변수 예제 파일을 복사한다.

    ```bash
    # bash/zsh
    cp .env.example .env
    ```

    ```powershell
    # PowerShell
    Copy-Item .env.example .env
    ```

1. `.env` 파일을 열어 비밀번호와 수집 API 키를 변경한다.

    ```dotenv
    QUERYECHO_DB_PASSWORD=충분히-긴-데이터베이스-비밀번호
    QUERYECHO_INGEST_API_KEY=충분히-긴-수집-API-키

    QUERYECHO_PORT=8080
    QUERYECHO_POSTGRES_PORT=5433
    QUERYECHO_IMAGE=ghcr.io/queryecho/queryecho-app
    QUERYECHO_VERSION=0.1.0
    ```

   `QUERYECHO_INGEST_API_KEY`는 타깃 애플리케이션의 SDK 설정에도 동일하게 넣어야 한다. 운영 환경에서는 예제 값을 그대로 사용하지 않는다.

1. 서버 이미지를 내려받고 컨테이너를 실행한다.

    ```bash
    docker compose pull
    docker compose up -d
    ```

1. 컨테이너 상태를 확인한다.

    ```bash
    docker compose ps
    ```

   `postgres`와 `queryecho`가 모두 `healthy`가 되면 준비가 끝난다. 최초 실행 시 데이터베이스 마이그레이션 때문에 잠시 시간이 걸릴 수 있다.

1. 서버 상태를 확인한다.

    ```bash
    curl http://localhost:8080/actuator/health
    ```

    PowerShell에서는 다음 명령을 사용할 수 있다.

    ```powershell
    Invoke-RestMethod http://localhost:8080/actuator/health
    ```

   정상 응답은 다음과 같다.

    ```json
    {"status":"UP"}
    ```

1. 웹 브라우저에서 `http://localhost:8080`을 열어 대시보드에 접속한다.

## 저장소 코드로 로컬 이미지 빌드

공개된 이미지를 받지 않고 현재 저장소 코드를 직접 빌드하려면 개발용 Compose 파일을 함께 사용한다.

1. 저장소 루트로 이동한다.

    ```bash
    cd "$REPOSITORY_ROOT"
    ```

    ```powershell
    Set-Location $REPOSITORY_ROOT
    ```

1. 로컬 이미지를 빌드하고 실행한다.

    ```bash
    docker compose -f docker-compose.yml -f compose.dev.yml up -d --build
    ```

1. 대시보드를 연다.

    ```text
    http://localhost:8080
    ```

1. 실행 로그가 필요하면 다음 명령을 사용한다.

    ```bash
    docker compose logs -f queryecho
    ```

## Spring Boot 애플리케이션에 SDK 적용

SDK는 Spring이 관리하는 `DataSource`를 프록시로 감싸 JDBC 쿼리 실행 시간을 측정한다. `@Transactional` 메서드의 완료 시점도 관찰해 커밋과 롤백 정보를 수집한다. 애플리케이션 코드를 직접 수정하지 않고 의존성과 설정을 추가하는 방식으로 적용한다.

1. Gradle 프로젝트라면 `build.gradle`에 Maven Central과 SDK 의존성을 추가한다.

    ```groovy
    repositories {
        mavenCentral()
    }

    dependencies {
        implementation 'io.github.queryecho:queryecho-sdk:0.1.0'
    }
    ```

    Gradle Kotlin DSL을 사용한다면 다음과 같이 추가한다.

    ```kotlin
    repositories {
        mavenCentral()
    }

    dependencies {
        implementation("io.github.queryecho:queryecho-sdk:0.1.0")
    }
    ```

    Maven 프로젝트라면 `pom.xml`에 추가한다.

    ```xml
    <dependency>
        <groupId>io.github.queryecho</groupId>
        <artifactId>queryecho-sdk</artifactId>
        <version>0.1.0</version>
    </dependency>
    ```

   위 버전은 예시다. 실제로 Maven Central에 공개된 최신 안정 버전을 사용한다.

1. 타깃 애플리케이션의 `application.properties`에 SDK 설정을 추가한다.

    ```properties
    queryecho.sdk.enabled=true
    queryecho.sdk.transport=HTTP
    queryecho.sdk.collector-url=http://localhost:8080
    queryecho.sdk.api-key=${QUERYECHO_INGEST_API_KEY}
    queryecho.sdk.app-name=orders-api
    queryecho.sdk.environment=local
    queryecho.sdk.instance-id=${HOSTNAME:local-instance}
    queryecho.sdk.db-type=postgresql
    ```

   주요 설정의 의미는 다음과 같다.

   | 설정 | 설명 |
   |---|---|
   | `enabled` | SDK 전체 활성화 여부 |
   | `transport` | 원격 서버 전송 시 `HTTP` 사용 |
   | `collector-url` | QueryEcho 서버 주소 |
   | `api-key` | 서버 `.env`의 `QUERYECHO_INGEST_API_KEY`와 같은 값 |
   | `app-name` | 대시보드에서 서비스를 구분할 이름 |
   | `environment` | `local`, `dev`, `staging`, `production` 같은 실행 환경 |
   | `instance-id` | 컨테이너, 서버 또는 Pod를 구분하는 값 |
   | `db-type` | `mysql`, `postgresql`, `h2` 같은 대상 DB 종류 |

1. API 키를 환경변수로 주입한다.

    ```bash
    # bash/zsh
    export QUERYECHO_INGEST_API_KEY='서버-.env와-같은-키'
    ```

    ```powershell
    # PowerShell
    $env:QUERYECHO_INGEST_API_KEY = '서버-.env와-같은-키'
    ```

1. 타깃 애플리케이션을 다시 실행한다.

    ```bash
    ./gradlew bootRun
    ```

    ```powershell
    .\gradlew.bat bootRun
    ```

1. 타깃 애플리케이션의 API를 호출해 실제 JDBC 쿼리나 트랜잭션을 발생시킨다.

1. 약 1초 후 QueryEcho 대시보드에서 애플리케이션 이름, 쿼리 실행 시간, 트랜잭션 상태를 확인한다. SDK는 이벤트를 메모리 큐에 모은 뒤 기본적으로 1초마다 배치 전송한다.

## Docker에서 실행 중인 애플리케이션 연결

타깃 애플리케이션도 Docker 컨테이너에서 실행 중이면 컨테이너 안의 `localhost`는 QueryEcho 서버가 아니라 해당 컨테이너 자신을 의미한다.

1. Windows 또는 macOS의 Docker Desktop에서 호스트의 QueryEcho 서버로 연결하려면 다음 주소를 사용한다.

    ```properties
    queryecho.sdk.collector-url=http://host.docker.internal:8080
    ```

1. 타깃 애플리케이션과 QueryEcho가 같은 Docker Compose 네트워크에 있다면 QueryEcho 서비스 이름을 사용한다.

    ```properties
    queryecho.sdk.collector-url=http://queryecho:8080
    ```

1. Kubernetes에서는 QueryEcho Service의 DNS 이름을 사용한다.

    ```properties
    queryecho.sdk.collector-url=http://queryecho.monitoring.svc.cluster.local:8080
    ```

## SDK 전송량 조정

기본값으로도 사용할 수 있지만 트래픽이 많은 서비스에서는 큐와 배치 크기를 조정할 수 있다.

```properties
queryecho.sdk.buffer.capacity=10000
queryecho.sdk.buffer.batch-size=200
queryecho.sdk.buffer.flush-interval-ms=1000
queryecho.sdk.buffer.request-timeout-ms=3000
```

- 큐가 가득 차면 모니터링 때문에 원래 서비스가 멈추지 않도록 새 지표를 버린다.
- Collector 요청이 제한 시간 안에 끝나지 않아도 업무 요청 스레드는 기다리지 않는다.
- 파라미터 값은 기본적으로 수집하지 않는다.

## 수집 범위 확인

SDK가 수집하는 대상은 SDK가 설치된 Java 애플리케이션의 JDBC 호출이다.

- JPA, Hibernate, JdbcTemplate도 최종적으로 JDBC를 사용하므로 수집 대상이다.
- `@Transactional` 메서드의 커밋, 롤백, 실행 시간을 수집한다.
- DBeaver, MySQL CLI, PostgreSQL CLI 또는 다른 언어에서 직접 실행한 쿼리는 Java SDK를 통과하지 않는다.
- DBeaver 같은 외부 클라이언트 쿼리는 별도의 MySQL `performance_schema` 또는 PostgreSQL `pg_stat_statements` 서버 수집기를 활성화해야 한다.

## 종료와 업데이트

1. QueryEcho 서버와 PostgreSQL을 중지한다.

    ```bash
    docker compose down
    ```

   위 명령은 PostgreSQL 데이터 볼륨을 삭제하지 않는다.

1. 저장된 데이터까지 삭제하려면 명시적으로 다음 명령을 실행한다.

    ```bash
    docker compose down -v
    ```

   이 명령은 QueryEcho에 저장된 쿼리와 트랜잭션 데이터를 복구하기 어렵게 삭제하므로 주의한다.

1. 새 버전으로 업데이트하려면 `.env`의 `QUERYECHO_VERSION`을 변경한 뒤 다시 실행한다.

    ```bash
    docker compose pull
    docker compose up -d
    ```

## 문제 해결

### 대시보드가 열리지 않음

```bash
docker compose ps
docker compose logs --tail 200 queryecho
```

`queryecho`가 시작되지 않으면 PostgreSQL 상태와 `.env`의 `QUERYECHO_DB_PASSWORD`를 확인한다.

### 수집 API가 401을 반환함

서버의 `QUERYECHO_INGEST_API_KEY`와 타깃 애플리케이션의 `queryecho.sdk.api-key`가 같은지 확인한다.

### 대시보드는 열리지만 쿼리가 표시되지 않음

다음 항목을 확인한다.

1. SDK 의존성이 실제 실행 클래스패스에 포함됐는지 확인한다.
1. `queryecho.sdk.enabled=true`인지 확인한다.
1. `queryecho.sdk.transport=HTTP`인지 확인한다.
1. `queryecho.sdk.collector-url`을 타깃 애플리케이션 실행 위치에서 접근할 수 있는지 확인한다.
1. 애플리케이션 로그에서 `[QueryEcho] SDK HTTP transport enabled` 메시지를 확인한다.
1. 쿼리에 사용되는 `DataSource`가 Spring Bean으로 관리되는지 확인한다.

### PostgreSQL 포트가 이미 사용 중임

`.env`에서 호스트 포트를 변경한다.

```dotenv
QUERYECHO_POSTGRES_PORT=15433
```

QueryEcho 컨테이너는 Docker 내부에서 `postgres:5432`로 연결하므로 이 값을 바꿔도 서버 동작에는 영향이 없다.

### 8080 포트가 이미 사용 중임

`.env`에서 대시보드 포트를 변경한다.

```dotenv
QUERYECHO_PORT=18080
```

이 경우 대시보드와 로컬 SDK 주소도 `http://localhost:18080`으로 변경한다.

## 운영 환경 참고 사항

- 인터넷에 공개할 때는 QueryEcho 앞에 HTTPS reverse proxy를 둔다.
- `.env` 파일을 Git 저장소에 커밋하지 않는다.
- `latest` 대신 `0.1.0`처럼 고정된 이미지 버전을 사용한다.
- PostgreSQL 볼륨을 정기적으로 백업한다.
- 수집 API 키는 환경별로 분리하고 주기적으로 교체한다.

