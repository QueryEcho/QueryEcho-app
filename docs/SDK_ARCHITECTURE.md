# QueryEcho SDK 분리: 직접 이해하면서 읽는 가이드

## 1. 이번 작업은 무엇을 바꾼 것인가?

기존에는 `queryecho-sdk` 안에 JDBC 쿼리 측정, Spring 자동 설정, 트랜잭션 AOP,
HTTP 전송과 JSON 직렬화가 함께 있었다. 순수 Java 앱에 쿼리 측정만 붙이고 싶어도
Spring Boot 4 계열 의존성을 같이 가져와야 했다.

이번에는 **Java만 알아도 할 수 있는 일**과 **Spring을 알아야 할 수 있는 일**을 나눴다.
쿼리 이벤트의 JSON 필드와 Collector 수집 API 경로는 유지했다.

예를 들어 실행시간을 재는 코드는 Spring이 필요 없다.

```java
long start = System.nanoTime();
statement.executeQuery();
long durationUs = (System.nanoTime() - start) / 1_000;
```

반면 Spring이 만든 DataSource를 자동으로 찾아 감싸는 일에는 `BeanPostProcessor`가 필요하다.
그래서 앞의 측정 코드는 `jdbc`로, 자동 연결 코드는 `starter`로 옮겼다.

## 2. 각 모듈의 역할

아래 이름에는 모두 `queryecho-` 접두사가 붙는다.

| 모듈 | 쉽게 말하면 | 대표 코드 |
|---|---|---|
| `core` | 공통으로 사용하는 데이터 양식과 설정 | `QueryMetricEvent`, `TxMetricEvent`, `SdkOptions`, `SqlNormalizer` |
| `jdbc` | JDBC 호출을 가로채 시간을 재는 장치 | `QueryEchoDataSourceProxy`, `StatementInvocationHandler` |
| `http-transport` | 이벤트를 큐에 모아 Collector로 보내는 장치 | `HttpMetricEventPublisher`, `EventEncoder` |
| `java-sdk` | 위 부품들을 사용하기 쉽게 조립한 입구 | `QueryEchoClient`, `QueryEchoConfig`, `QueryEchoTransaction` |
| `spring-boot-3-starter` | Boot 3.5 앱에 부품을 자동으로 연결 | `QueryEchoAutoConfiguration`, `TransactionMetricsAspect` |
| `spring-boot-4-starter` | Boot 4.1 앱에 부품을 자동으로 연결 | 같은 역할의 Boot 4 전용 어댑터 |

각 모듈이 개별 서버로 실행되는 것은 아니다. 타깃 애플리케이션에 필요한 JAR들이
함께 들어가 **하나의 JVM 안에서 실행**된다. ECS 서비스를 여섯 개 만드는 구조가 아니다.

## 3. 의존성과 실행 흐름은 다르다

의존성은 “이 모듈이 컴파일·실행할 때 무엇을 필요로 하는가”이다.

```text
Boot 3 Starter ─┬─ Java SDK ─┬─ JDBC ─────────── Core
               │           └─ HTTP Transport ─ Core
Boot 4 Starter ┘

Collector ── Core
Dashboard ── Collector
QueryEcho App ── Boot 4 Starter + Collector + Dashboard
```

Starter는 내부 자동 구성에서 HTTP Transport도 직접 사용한다.
중요한 방향은 `JDBC → Spring` 의존성이 없다는 것이다.
그래서 JDBC 코드는 Spring 버전을 알 필요가 없다.

`MetricEventPublisher`는 `core`에 있는 작은 인터페이스다.

```java
public interface MetricEventPublisher {
    void publish(Object event);
}
```

JDBC는 이 인터페이스에 이벤트를 넘기기만 한다. HTTP로 보낼지,
테스트용 리스트에 담을지, Spring 이벤트로 보낼지는 외부에서 정한다.
**인터페이스를 공통 모듈에 둔 이유가 이 의존성을 끊기 위해서다.**

## 4. 쿼리 한 건을 따라가 보기

```text
애플리케이션이 DataSource.getConnection() 호출
→ QueryEchoDataSourceProxy가 실제 Connection을 감싸 반환
→ prepareStatement(sql) 호출
→ ConnectionInvocationHandler가 실제 PreparedStatement를 감싸 반환
→ executeQuery() 호출
→ StatementInvocationHandler가 실제 JDBC 호출 전후 시간 측정
→ QueryMetricEvent 생성
→ SanitizingMetricEventPublisher가 허용하지 않은 값 제거
→ HttpMetricEventPublisher의 제한된 메모리 큐에 추가
→ 전송 스레드가 배치로 꺼내 JSON 생성
→ POST /api/v1/ingest/queries
→ Collector가 비동기 저장
```

프록시는 SQL을 대신 실행하는 별도 DB가 아니다. 원래 JDBC 객체를 호출하면서
앞뒤에 측정 코드를 끼워 넣는 객체다. 드라이버의 SQLException도 원래 형태로 전달한다.

기존 정책인 기본 배치 200건, 기본 큐 10,000건, 전송 주기 1초를 유지했다.
큐가 차면 새 이벤트를 버리고, HTTP 실패도 재시도 없이 유실 건수에 반영한다.
따라서 `202 Accepted`는 DB 저장 완료가 아니라 Collector 접수 성공이다.

파라미터는 기본적으로 전송하지 않고, 명시적으로 허용한 패턴·위치의 값만 제한적으로 보낸다.
현재 JDBC 프록시 내부에서는 바인딩 값을 일시적으로 참조한 뒤 전송 전에 제거한다.
즉, “전송하지 않음”과 “프로세스 메모리에서도 전혀 다루지 않음”은 다르다.
SQL 정규화도 모든 DB 문법의 민감정보 제거를 보장하는 보안 필터는 아니다.

## 5. 순수 Java에서 사용하기

의존성은 `queryecho-java-sdk` 하나를 추가한다. Gradle/Maven이 하위 모듈을 함께 가져온다.
아래 코드는 애플리케이션이 이미 만든 `originalDataSource`를 감싼다.

```java
QueryEchoConfig config = new QueryEchoConfig();
config.setAppName("orders-api");
config.setEnvironment("load-test");
config.setInstanceId("worker-01");
config.setDbType("postgresql");
config.setCollectorUrl("http://localhost:8080");
config.setApiKey(System.getenv("QUERYECHO_INGEST_API_KEY"));

try (QueryEchoClient client = new QueryEchoClient(config)) {
    DataSource monitored = client.wrap(originalDataSource, "mainDataSource");
    try (Connection connection = monitored.getConnection();
         PreparedStatement statement = connection.prepareStatement("select 1")) {
        statement.executeQuery().close();
    }
}
```

실제 서버에서는 client를 요청마다 새로 만들지 않는다. 애플리케이션 시작 시 한 번 만들고
종료 시 닫는다. 원래 DataSource/커넥션 풀의 종료 책임은 애플리케이션에 남는다.
설정은 client 생성 전에 완료하고 실행 중에는 수정하지 않는다.

순수 Java에는 `@Transactional`이 없으므로 트랜잭션 관측 범위를 직접 알려준다.

```java
try (Connection connection = monitored.getConnection()) {
    connection.setAutoCommit(false);
    try (QueryEchoTransaction tx = client.beginTransaction("createOrder")) {
        try {
            // 이 안에서 monitored를 통해 실행한 쿼리는 tx.transactionId()와 연결된다.
            try (PreparedStatement statement = connection.prepareStatement("select 1")) {
                statement.executeQuery().close();
            }
            connection.commit();   // 실제 DB 커밋
            tx.committed();        // 성공한 결과를 SDK에 기록
        } catch (Exception failure) {
            try {
                connection.rollback();
                tx.rolledBack(failure);
            } catch (SQLException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
    }
}
```

`tx.committed()`가 DB에 COMMIT을 보내지는 않는다. DB 제어와 관측 결과 기록을 구분했다.
결과를 알리지 않고 범위를 닫으면 `UNKNOWN`으로 남긴다. 이 API는 생성한 스레드에서
완료해야 하며, 커밋 실패를 성공으로 기록해서는 안 된다.

## 6. Spring에서 달라지는 것은 자동 연결이다

사용 중인 Boot 버전에 맞는 Starter **하나만** 추가한다.

```groovy
// Boot 3.5
implementation 'io.github.queryecho:queryecho-spring-boot-3-starter:0.1.0-SNAPSHOT'

// 또는 Boot 4.1
implementation 'io.github.queryecho:queryecho-spring-boot-4-starter:0.1.0-SNAPSHOT'
```

이 좌표는 로컬 배포 산출물 기준이다. Maven Central에 공개됐다는 의미는 아니다.

```properties
queryecho.sdk.transport=HTTP
queryecho.sdk.collector-url=http://localhost:8080
queryecho.sdk.api-key=${QUERYECHO_INGEST_API_KEY}
queryecho.sdk.app-name=orders-api
queryecho.sdk.environment=load-test
queryecho.sdk.instance-id=worker-01
queryecho.sdk.db-type=postgresql
```

Starter가 하는 일은 다음과 같다.

1. `AutoConfiguration.imports`를 통해 자동 구성 클래스를 로드한다.
2. `QueryEchoProperties`가 `queryecho.sdk.*` 설정을 읽는다.
3. `BeanPostProcessor`가 DataSource를 찾아 공통 JDBC 프록시로 감싼다.
4. AOP가 `@Transactional` 호출을 관측하고 실제 완료 콜백에 결과 기록을 등록한다.
5. MVC 애플리케이션이면 요청 ID·경로 정보를 공통 컨텍스트에 전달한다.

`QueryEchoProperties`는 공통 `SdkOptions`를 상속하고 Spring 설정 바인딩만 추가한다.
전송·파라미터 정책에서 Spring 설정 클래스를 참조하면 다시 결합되기 때문에
공통 옵션을 `core`에 두었다.

Boot 3/4는 Spring Framework 의존성과 일부 자동 구성 패키지가 다르다.
그래서 Starter마다 BOM을 별도로 적용하고 같은 통합 테스트를 각각 실행한다.
공통 JDBC·JSON·전송 로직을 두 벌로 복사하지 않았다. Spring 어댑터는 버전별 소스이며,
향후 공통 수정이 있으면 두 Starter와 테스트를 함께 변경해야 한다.

## 7. 트랜잭션과 쿼리는 어떻게 연결되는가?

```text
외부 트랜잭션 A: UUID 생성 → TransactionContext.push(A)
  쿼리 1: transactionId=A
  REQUIRES_NEW 트랜잭션 B: UUID 생성 → push(B)
    쿼리 2: transactionId=B
  B 완료 → B 제거
  쿼리 3: transactionId=A
A 완료 → A 제거
```

동일 물리 트랜잭션에 참여하는 `REQUIRED`는 기존 synchronization marker를 발견하면
새 이벤트를 등록하지 않는다. `REQUIRES_NEW`는 별도 synchronization 목록을 가지므로
다른 ID를 생성한다. 이 관계를 통합 테스트에서 확인한다.

측정 시작은 Spring이 트랜잭션을 준비한 뒤 Aspect에 진입한 시점이다.
따라서 이 duration에는 트랜잭션 시작 전의 커넥션 획득 대기 전체가 포함된다고 보장할 수 없다.
직접 JDBC 트랜잭션과 `TransactionTemplate`의 자동 계측, 비동기 스레드 간 컨텍스트 전파는
이번 분리 작업의 지원 범위가 아니다. 같은 클래스의 내부 호출은 일반 Spring AOP 제약도 따른다.

## 8. JSON을 어떻게 분리했는가?

기존 HTTP 전송은 Jackson 3 ObjectMapper를 직접 만들었다.
현재는 `EventEncoder.encode(Object)` 인터페이스를 호출한다.

기본 `JsonEventEncoder`는 JDK로 SDK record의 필드를 JSON으로 쓰며,
UUID·Instant·enum은 문자열로, null은 null로 보낸다. 임의의 Java Bean 직렬화는 지원하지 않는다.
인코더는 실제 JSON 파서로 역검증하고 HTTP 서버 수신 테스트도 실행한다.

이 선택은 Jackson 2가 있는 Boot 3 앱과 Jackson 3가 있는 Boot 4 앱 모두에서 동일한
전송 코드를 쓰기 위한 것이다. 문자열 이스케이프 같은 JSON 구현의 유지보수 책임은
프로젝트에 남으므로 이벤트 형식을 확장할 때 직렬화 테스트를 함께 추가해야 한다.

## 9. Java 17 바이트코드는 무엇인가?

빌드 실행은 JDK 21을 쓰되 SDK 컴파일에는 `--release 17`을 적용한다.
그러면 Java 21 전용 API를 잘못 사용하면 컴파일 오류가 나고, 결과 클래스도 Java 17에서
읽을 수 있는 형식이 된다. 서버의 app/collector/dashboard는 Java 21을 유지한다.

이것과 실제 Java 17 실행 테스트는 별개다. `-PtestJavaVersion=17`은 SDK 테스트 JVM을
Java 17로 바꾸며, CI는 Boot 3/4 × Java 17/21 조합으로 실행한다.

```bash
# 전체 테스트와 서버 실행 JAR
./gradlew test :queryecho-app:bootJar

# SDK 테스트 + 클래스 버전 및 Spring/Jackson 런타임 의존성 경계 검사
./gradlew sdkCheck

# 실제 Java 17에서 SDK 테스트 (JDK 17, 빌드용 JDK 21 필요)
./gradlew sdkCheck -PtestJavaVersion=17

# 여섯 SDK 모듈을 로컬 Maven 저장소에 배포
./gradlew sdkPublishToMavenLocal -PVERSION_NAME=0.1.0-SNAPSHOT
```

Windows에서는 `./gradlew` 대신 `.\gradlew.bat`을 사용한다.
검증 결과와 남은 범위는 [SDK_VERIFICATION.md](SDK_VERIFICATION.md)에 기록한다.

## 10. 직접 코드를 읽는 순서

1. `queryecho-core/.../dto/QueryMetricEvent.java`: 어떤 값을 수집하는지 읽는다.
2. `queryecho-java-sdk/.../QueryEchoClient.java`: 부품을 어떻게 조립하는지 본다.
3. `queryecho-jdbc/.../QueryEchoDataSourceProxy.java`: Connection을 감싸는 부분을 본다.
4. `ConnectionInvocationHandler.java` → `StatementInvocationHandler.java`: 실행 호출을 따라간다.
5. `queryecho-java-sdk/.../SanitizingMetricEventPublisher.java`: 외부 전송 전에 값이 어떻게 줄어드는지 본다.
6. `queryecho-http-transport/.../HttpMetricEventPublisher.java`: 큐 삽입과 전송 스레드를 구분한다.
7. 자신이 쓰는 Starter의 `QueryEchoAutoConfiguration.java`: 수동 조립을 Spring이 어떻게 대신하는지 본다.
8. 같은 Starter의 `TransactionMetricsAspect.java`: 트랜잭션 ID와 완료 콜백을 읽는다.
9. `StarterIntegrationTest.java`, `QueryEchoClientTest.java`: 실제 사용 예와 기대 결과를 확인한다.

읽고 나서 “SQL 측정은 왜 Spring 없이 가능한가?”, “왜 Collector는 Core만 알면 되는가?”,
“순수 Java의 committed()와 JDBC commit()은 무엇이 다른가?”를 자기 말로 설명할 수 있으면
이번 분리의 핵심을 이해한 것이다.

## 11. 기존 사용자와 AWS 테스트에 미치는 영향

기존 `queryecho-sdk` 대신 Boot 버전에 맞는 Starter 좌표를 사용한다.
직접 SDK 클래스를 import하던 코드는 새 패키지로 변경해야 한다. Java 소스·바이너리 호환을
유지하는 별칭 모듈은 제공하지 않는다. Collector/Dashboard의 DTO import도 Core로 바뀌었다.
기존 DB 마이그레이션이나 JSON 필드에는 변경을 추가하지 않았다.

Collector 단독 부하테스트는 기존 JSON 계약을 그대로 사용할 수 있다.
E2E 부하테스트는 새 Starter를 붙인 타깃 앱으로 진행한다.
이 문서의 호환성 테스트는 AWS 처리량·유실률·SDK 성능을 입증하는 부하테스트가 아니다.
