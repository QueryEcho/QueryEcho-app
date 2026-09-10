# QueryEcho

QueryEcho는 Java/Spring 애플리케이션에서 실행한 JDBC 쿼리와 트랜잭션을 수집하고, 웹 대시보드에서 확인하는 DB 모니터링 도구입니다.

이 문서는 SDK를 처음 적용하는 사용자를 위한 한국어 가이드입니다. QueryEcho 서버를 준비한 뒤 애플리케이션 환경에 맞는 SDK 하나를 선택해 적용합니다.

## 목차

- [사용할 SDK 선택](#사용할-sdk-선택)
- [공통 준비](#공통-준비)
- [Java SDK 사용 가이드](#java-sdk-사용-가이드)
- [Spring SDK 사용 가이드](#spring-sdk-사용-가이드)
- [수집 결과 확인](#수집-결과-확인)
- [문제 해결](#문제-해결)

## 사용할 SDK 선택

| 애플리케이션 환경 | 추가할 SDK | 적용 방법 |
|---|---|---|
| Spring 없이 사용하는 Java | `queryecho-java-sdk` | 기존 DataSource를 코드로 연결 |
| Spring Boot 3.5 | `queryecho-spring-boot-3-starter` | 의존성과 설정 추가 |
| Spring Boot 4.1 | `queryecho-spring-boot-4-starter` | 의존성과 설정 추가 |

SDK는 Java 17·21에서 검증했습니다. Spring Starter는 각각 Boot 3.5.6과 4.1.0 기준입니다. Spring 사용 가이드는 Spring Boot 애플리케이션을 대상으로 합니다.

필요한 하위 라이브러리는 함께 설치되므로 Core, JDBC, HTTP Transport를 따로 추가할 필요가 없습니다. Boot 3용과 Boot 4용 Starter를 동시에 추가하지 않습니다.

## 공통 준비

### 1. QueryEcho 서버 실행

Docker와 Docker Compose를 준비하고 QueryEcho 저장소 루트에서 실행합니다. 아래 명령은 현재 저장소 코드로 서버를 빌드합니다.

```bash
cp .env.example .env
```

PowerShell에서는 다음 명령을 사용합니다.

```powershell
Copy-Item .env.example .env
```

이미 `.env`가 있다면 복사하지 않고 기존 파일을 수정합니다. 다음 두 값을 원하는 값으로 변경합니다.

```dotenv
QUERYECHO_DB_PASSWORD=사용할-저장용-DB-비밀번호
QUERYECHO_INGEST_API_KEY=사용할-수집-API-키
```

```bash
docker compose -f docker-compose.yml -f compose.dev.yml up -d --build
docker compose ps
```

`postgres`와 `queryecho`가 healthy 상태가 되면 준비가 끝납니다. 브라우저에서 [대시보드](http://localhost:8080)를 엽니다.

이 PostgreSQL은 QueryEcho의 수집 데이터 저장용입니다. 모니터링할 애플리케이션의 업무용 DB와 접속 설정은 기존 구성을 사용합니다. 공개 서버 이미지로 실행하는 방법은 [서버 사용 가이드](docs/GETTING_STARTED.md)를 참고하세요.

### 2. SDK 의존성 준비

아래 SDK 예시는 현재 저장소의 `0.1.0-SNAPSHOT`을 로컬 Maven 저장소에 설치해 사용하는 방식입니다. Maven Central에서 해당 버전을 바로 내려받을 수 있다는 의미는 아닙니다.

JDK 21을 준비하고 QueryEcho 저장소 루트에서 한 번 실행합니다.

```bash
./gradlew sdkPublishToMavenLocal -PVERSION_NAME=0.1.0-SNAPSHOT
```

```powershell
.\gradlew.bat sdkPublishToMavenLocal -PVERSION_NAME=0.1.0-SNAPSHOT
```

SDK를 설치한 것과 같은 컴퓨터·사용자 계정에서 타깃 프로젝트를 빌드합니다. 다른 컴퓨터나 CI에서도 이 버전을 사용하려면 해당 환경에 동일 버전의 SDK를 설치해야 합니다.

공개 릴리스를 사용할 때는 실제 배포 버전으로 변경하고, 아래 Gradle 예시의 `mavenLocal()`을 제거합니다.

### 3. 수집 API 키 설정

SDK를 실행할 터미널에 서버 `.env`와 같은 키를 설정합니다.

```bash
export QUERYECHO_INGEST_API_KEY='서버-.env와-같은-키'
```

```powershell
$env:QUERYECHO_INGEST_API_KEY = '서버-.env와-같은-키'
```

IDE로 실행한다면 실행 구성의 환경변수에 같은 값을 추가합니다. 서버의 `.env`가 별도 애플리케이션에 자동 적용되지는 않습니다.

## Java SDK 사용 가이드

Spring 없이 JDBC DataSource를 사용하는 애플리케이션에 적용합니다. 기존 DB 드라이버와 DataSource 또는 커넥션 풀은 그대로 사용합니다.

### 1. 의존성 추가

타깃 애플리케이션의 `build.gradle`에 추가합니다.

```groovy
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation 'io.github.queryecho:queryecho-java-sdk:0.1.0-SNAPSHOT'
}
```

Gradle Kotlin DSL에서는 다음과 같이 추가합니다.

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("io.github.queryecho:queryecho-java-sdk:0.1.0-SNAPSHOT")
}
```

Maven에서는 `pom.xml`에 추가합니다. 공통 준비에서 설치한 로컬 Maven 저장소를 사용합니다.

```xml
<dependency>
    <groupId>io.github.queryecho</groupId>
    <artifactId>queryecho-java-sdk</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 2. 기존 DataSource에 SDK 연결

아래 예시의 `run()`에 애플리케이션에서 이미 사용하는 DataSource를 전달합니다. `dbType`은 실제 대상 DB에 맞게 변경합니다.

```java
import com.queryecho.sdk.QueryEchoClient;
import com.queryecho.sdk.QueryEchoConfig;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;

public class QueryEchoExample {
    public static void run(DataSource originalDataSource) throws Exception {
        QueryEchoConfig config = new QueryEchoConfig();
        config.setAppName("orders-java");
        config.setEnvironment("local");
        config.setInstanceId("local-instance");
        config.setDbType("postgresql");
        config.setCollectorUrl("http://localhost:8080");
        config.setApiKey(System.getenv("QUERYECHO_INGEST_API_KEY"));

        try (QueryEchoClient client = new QueryEchoClient(config)) {
            DataSource monitored = client.wrap(originalDataSource, "main");

            try (Connection connection = monitored.getConnection();
                 PreparedStatement statement =
                     connection.prepareStatement("select ?")) {
                statement.setInt(1, 42);

                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        System.out.println(result.getInt(1));
                    }
                }
            }
        }
    }
}
```

위 코드를 실행하면 SQL 측정 결과가 QueryEcho 서버로 전송됩니다. 대시보드에서 `orders-java`를 확인합니다.

실제 서비스에서는 Client와 감싼 DataSource를 애플리케이션 시작 시 생성해 재사용합니다. 쿼리마다 위 메서드를 실행해 Client를 새로 만들지 않습니다. 설정은 Client 생성 전에 완료합니다.

기존 DB 접근 코드가 `monitored`를 사용하도록 연결해야 합니다. 원본 DataSource를 계속 사용하는 쿼리는 이 SDK로 수집되지 않습니다. `"main"`은 대시보드에서 DataSource를 구분하는 이름입니다.

### 3. 트랜잭션 수집 추가하기

SQL 수집만 필요하면 앞 단계까지 적용합니다. 트랜잭션 결과도 함께 확인하려면 실제 JDBC 트랜잭션을 시작한 뒤 SDK 관측 범위를 엽니다.

아래 메서드에는 앞에서 만든 Client와 감싼 DataSource를 전달합니다.

```java
import com.queryecho.sdk.QueryEchoClient;
import com.queryecho.sdk.QueryEchoTransaction;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;

public class TransactionExample {
    public static void run(QueryEchoClient client, DataSource monitored)
            throws SQLException {
        try (Connection connection = monitored.getConnection()) {
            connection.setAutoCommit(false);

            try (QueryEchoTransaction tx =
                     client.beginTransaction("createOrder")) {
                try {
                    try (PreparedStatement statement =
                             connection.prepareStatement("select ?")) {
                        statement.setInt(1, 42);
                        statement.executeQuery().close();
                    }

                    connection.commit();
                } catch (SQLException | RuntimeException failure) {
                    try {
                        connection.rollback();
                    } catch (SQLException rollbackFailure) {
                        failure.addSuppressed(rollbackFailure);
                        throw failure;
                    }

                    tx.rolledBack(failure);
                    throw failure;
                }

                tx.committed();
            }
        }
    }
}
```

- 실제 DB 커밋이 성공한 뒤 `tx.committed()`를 호출합니다.
- 실제 DB 롤백이 성공한 뒤 `tx.rolledBack(failure)`를 호출합니다.
- 결과를 지정하지 않고 관측 범위를 닫으면 `UNKNOWN`으로 기록됩니다.
- 같은 스레드에서 관측 범위를 열고 완료합니다. 다른 스레드로 정보가 자동 전달되지 않습니다.

SDK의 완료 메서드가 DB를 커밋하거나 롤백해 주지는 않습니다.

### 4. 설정 변경과 종료

필요한 경우 Client를 생성하기 전에 전송 설정을 변경합니다.

```java
config.getBuffer().setCapacity(10_000);
config.getBuffer().setBatchSize(200);
config.getBuffer().setFlushIntervalMs(1_000);
config.getBuffer().setRequestTimeoutMs(3_000);
```

파라미터 값과 트랜잭션 실패 메시지는 기본적으로 전송하지 않습니다. 일반 적용에서는 기본값으로 시작합니다.

애플리케이션 종료 시 업무 처리를 끝낸 뒤 `client.close()`를 호출합니다. 원본 DataSource 또는 커넥션 풀은 애플리케이션에서 별도로 닫습니다.

SDK 적용을 끄려면 Client를 만들기 전에 `config.setEnabled(false)`를 설정합니다.

## Spring SDK 사용 가이드

Spring Boot 애플리케이션에서 Spring 빈으로 관리하는 DataSource에 적용합니다. 기존 JdbcTemplate 또는 JPA 사용 코드는 유지하고 의존성과 SDK 설정을 추가합니다.

### 1. Boot 버전에 맞는 의존성 추가

타깃 애플리케이션의 `build.gradle`에 추가합니다. 아래 두 SDK 중 사용 중인 Boot 버전에 맞는 하나만 선택합니다.

```groovy
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    // Spring Boot 3.5인 경우
    implementation 'io.github.queryecho:queryecho-spring-boot-3-starter:0.1.0-SNAPSHOT'

    // Spring Boot 4.1인 경우 위 의존성 대신 다음을 사용합니다.
    // implementation 'io.github.queryecho:queryecho-spring-boot-4-starter:0.1.0-SNAPSHOT'
}
```

Gradle Kotlin DSL에서는 다음과 같이 추가합니다.

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    // Boot 4.1은 artifact 이름을 queryecho-spring-boot-4-starter로 변경합니다.
    implementation("io.github.queryecho:queryecho-spring-boot-3-starter:0.1.0-SNAPSHOT")
}
```

Maven에서는 `pom.xml`에 추가합니다. Boot 4.1은 artifactId를 `queryecho-spring-boot-4-starter`로 변경합니다.

```xml
<dependency>
    <groupId>io.github.queryecho</groupId>
    <artifactId>queryecho-spring-boot-3-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

DB를 이미 사용하는 앱이라면 기존 드라이버·DataSource·트랜잭션 설정을 유지합니다. SDK가 업무용 DB를 생성하거나 접속 정보를 설정하지는 않습니다.

### 2. application.properties 설정

타깃 애플리케이션의 `src/main/resources/application.properties`에 추가합니다. Boot 3과 Boot 4의 SDK 설정은 같습니다.

```properties
queryecho.sdk.enabled=true
queryecho.sdk.transport=HTTP
queryecho.sdk.collector-url=http://localhost:8080
queryecho.sdk.api-key=${QUERYECHO_INGEST_API_KEY}
queryecho.sdk.app-name=orders-spring
queryecho.sdk.environment=local
queryecho.sdk.instance-id=${HOSTNAME:local-instance}
queryecho.sdk.db-type=postgresql
```

| 설정 | 입력할 값 |
|---|---|
| collector-url | SDK 실행 환경에서 접근 가능한 QueryEcho 서버 주소 |
| api-key | 서버에 설정한 수집 API 키 |
| app-name | 대시보드에서 구분할 서비스 이름 |
| environment | local, dev, production 등 실행 환경 |
| instance-id | 서버 또는 컨테이너별 식별값 |
| db-type | 실제 업무용 DB 종류: postgresql, mysql, h2 등 |

원격 서버로 전송하려면 `transport=HTTP`를 반드시 지정합니다. 생략 시 기본값은 LOCAL이며 별도 QueryEcho 서버로 전송되지 않습니다.

타깃 웹 애플리케이션도 8080 포트를 사용한다면 다른 포트로 설정합니다.

```properties
server.port=8081
```

SDK의 collector-url은 QueryEcho 서버 주소인 `http://localhost:8080`을 유지합니다.

### 3. 애플리케이션 실행과 쿼리 발생

API 키 환경변수를 설정한 터미널에서 타깃 애플리케이션을 실행합니다.

```bash
./gradlew bootRun
```

```powershell
.\gradlew.bat bootRun
```

Maven 프로젝트는 Maven Wrapper로 실행할 수 있습니다.

```bash
./mvnw spring-boot:run
```

```powershell
.\mvnw.cmd spring-boot:run
```

기존 API 중 DB를 사용하는 API를 호출합니다. 예를 들어 애플리케이션에 `GET /orders`가 있다면 해당 경로를 호출하고 대시보드에서 `orders-spring`을 확인합니다.

직접 QueryEchoClient를 생성하거나 기존 DataSource를 수동으로 감쌀 필요는 없습니다.

### 4. 트랜잭션 수집 확인

기존 `@Transactional` 메서드를 다른 Spring 빈에서 호출하면 SQL과 트랜잭션 결과를 함께 확인할 수 있습니다. 트랜잭션 관리자가 구성되어 있어야 합니다.

JdbcTemplate을 사용하는 앱에서는 다음 서비스로 간단히 확인할 수 있습니다. 애플리케이션의 컴포넌트 스캔 대상 패키지에 추가합니다.

```java
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QueryEchoDemoService {
    private final JdbcTemplate jdbcTemplate;

    public QueryEchoDemoService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Integer checkConnection() {
        return jdbcTemplate.queryForObject("select 1", Integer.class);
    }
}
```

기존 Controller 등 다른 빈에서 주입받은 `QueryEchoDemoService`의 `checkConnection()`을 호출합니다. 대시보드에서 쿼리와 COMMIT 결과를 확인합니다. 같은 클래스 안에서 자기 메서드를 직접 호출하는 방식으로는 트랜잭션 자동 관측이 적용되지 않을 수 있습니다.

Servlet MVC 앱에서는 요청 경로·요청 ID 등도 함께 수집됩니다. WebFlux·R2DBC 자동 계측과 비동기 스레드 간 정보 전달은 지원하지 않습니다.

### 5. 설정 변경과 비활성화

필요한 경우 `application.properties`에서 전송량을 조정합니다.

```properties
queryecho.sdk.buffer.capacity=10000
queryecho.sdk.buffer.batch-size=200
queryecho.sdk.buffer.flush-interval-ms=1000
queryecho.sdk.buffer.request-timeout-ms=3000
```

특정 DataSource를 제외하려면 실제 Spring 빈 이름을 입력합니다.

```properties
queryecho.sdk.excluded-data-source-beans[0]=collectorDataSource
```

SDK를 끄려면 아래 설정을 적용하고 재시작합니다.

```properties
queryecho.sdk.enabled=false
```

일반적인 Spring 애플리케이션 종료 시 SDK도 함께 종료됩니다. 별도 Client 종료 코드를 추가할 필요는 없습니다.

## 수집 결과 확인

1. Java 예제를 실행하거나 Spring 앱의 DB 사용 API를 호출합니다.
1. [대시보드](http://localhost:8080)를 엽니다.
1. SDK에 설정한 앱 이름과 실행 환경으로 해당 데이터를 확인합니다.
1. SQL 실행시간과 성공 여부를 확인합니다. 트랜잭션 관측을 적용했다면 트랜잭션 상태와 연관된 SQL도 확인합니다.

기본 전송 설정은 이전 전송 작업이 끝난 뒤 1초를 기다립니다. 네트워크·저장 처리에 따라 표시가 늦어질 수 있습니다. 파라미터 값이 표시되지 않는 것은 기본 설정에 따른 정상 동작입니다.

수집 대상은 SDK를 적용한 DataSource를 통해 실행한 JDBC 쿼리입니다. DBeaver·CLI 등 다른 클라이언트의 쿼리는 SDK 수집 대상에 포함되지 않습니다. SQL 실행시간에는 이후 결과 전체를 읽고 객체로 변환하는 시간까지 포함된다고 보장하지 않습니다.

전송 큐가 가득 차거나 전송에 실패하면 지표가 유실될 수 있습니다. SDK는 실패한 이벤트를 재시도하거나 디스크에 보관하지 않습니다.

## 문제 해결

### 대시보드에 접속할 수 없음

QueryEcho 저장소 루트에서 확인합니다.

```bash
docker compose ps
docker compose logs --tail 100 queryecho
```

8080 포트가 사용 중이면 서버 `.env`의 `QUERYECHO_PORT`를 변경하고 서버를 다시 실행합니다. 대시보드와 SDK의 Collector 주소도 변경한 포트에 맞춥니다.

### SDK 의존성을 찾을 수 없음

공통 준비의 로컬 Maven 설치 명령이 성공했는지, SDK 버전이 `0.1.0-SNAPSHOT`으로 일치하는지 확인합니다. Gradle은 `mavenLocal()`이 필요하며, 설치와 타깃 빌드는 같은 사용자의 로컬 저장소를 사용해야 합니다.

### HTTP 401 또는 데이터가 표시되지 않음

- 서버와 SDK의 수집 API 키가 같은지 확인합니다.
- Java는 감싼 DataSource로 SQL을 실행했는지 확인합니다.
- Spring은 DataSource가 Spring 빈인지, enabled=true와 transport=HTTP인지 확인합니다.
- 앱 이름·환경과 대시보드 조회 시간 범위가 맞는지 확인합니다.
- 실행 로그에 전송 실패가 있는지 확인합니다. 순수 Java에서 로그를 확인하려면 애플리케이션에 SLF4J 2 호환 로깅 구현이 필요합니다.

### Docker 앱에서 localhost로 연결되지 않음

컨테이너 안의 localhost는 해당 컨테이너 자신입니다. Collector 위치에 맞는 주소를 사용합니다.

| Collector 위치 | SDK의 Collector 주소 예시 |
|---|---|
| SDK와 같은 호스트에서 실행, SDK는 컨테이너 밖 | http://localhost:8080 |
| Docker Desktop 호스트에서 접근 가능한 서버, SDK는 컨테이너 안 | http://host.docker.internal:8080 |
| 같은 Compose 네트워크의 queryecho 서비스 | http://queryecho:8080 |

Java에서는 `config.setCollectorUrl()`, Spring에서는 `queryecho.sdk.collector-url`을 변경합니다.

### 쿼리는 있지만 트랜잭션이 없음

Java는 같은 스레드에서 `beginTransaction()`으로 관측 범위를 열고 완료했는지 확인합니다. Spring은 `@Transactional` 메서드를 다른 Spring 빈에서 호출했는지 확인합니다. 직접 JDBC commit/rollback과 TransactionTemplate 호출만으로 Spring Starter의 트랜잭션 관측이 자동 적용되지는 않습니다.

### Spring에서 DataSource 타입 오류가 발생함

DataSource를 주입받을 때 HikariDataSource 같은 구체 클래스 대신 `javax.sql.DataSource` 타입을 사용합니다.

## 추가 문서

- [서버 설치와 실행](docs/GETTING_STARTED.md)
- [SDK 구조 이해 가이드](docs/SDK_ARCHITECTURE.md)
- [배포 준비](docs/PUBLISHING.md)

## 라이선스

Apache License 2.0
