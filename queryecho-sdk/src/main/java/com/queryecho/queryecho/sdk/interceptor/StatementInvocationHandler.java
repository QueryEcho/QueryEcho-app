package com.queryecho.queryecho.sdk.interceptor;

import com.queryecho.queryecho.sdk.util.SqlNormalizer;
import com.queryecho.queryecho.sdk.dto.QueryMetricEvent;
import com.queryecho.queryecho.sdk.publisher.MetricEventPublisher;
import com.queryecho.queryecho.sdk.transaction.TransactionContext;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Statement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

/**
 * Statement / PreparedStatement / CallableStatement 한 개를 감싸는 JDK 동적 프록시의
 * InvocationHandler.
 *
 * 왜 JDK Dynamic Proxy(java.lang.reflect.Proxy)를 썼는가?
 *  - Statement 계열은 드라이버(H2, MySQL, PostgreSQL 등)마다 구현체 클래스가 다르고
 *    실제 구현 클래스는 대부분 public이 아니어서 상속으로 감쌀 수 없다.
 *  - 반면 Statement/PreparedStatement/CallableStatement는 모두 표준 인터페이스이므로,
 *    인터페이스 기반 프록시(java.lang.reflect.Proxy)로 감싸면 어떤 드라이버가 와도
 *    동일한 코드 한 벌로 가로챌 수 있다. (P6Spy, Datasource-Proxy 같은 기존 도구들도
 *    동일한 전략을 쓴다.) CGLIB 등 바이트코드 조작 라이브러리를 추가로 끌어올 필요가 없다.
 *
 * 왜 파라미터 바인딩을 setXxx(int, ...) 시그니처로 가로채는가?
 *  - PreparedStatement의 바인딩 값은 JDBC 표준상 항상 "1부터 시작하는 int 인덱스"를
 *    첫 번째 인자로 받는 setXxx 메서드로 들어온다. 따라서 메서드 이름이 "set"으로 시작하고
 *    첫 인자가 Integer인 모든 호출을 값으로 흡수하면, setString/setLong/setObject 등
 *    수십 개 오버로드를 일일이 나열하지 않고도 바인딩 값을 전부 캡처할 수 있다.
 */
class StatementInvocationHandler implements InvocationHandler {

    // execute 계열만 "실행"으로 간주해서 시간을 측정한다.
    // (addBatch, close, getResultSet 같은 나머지 메서드는 실행 시간 측정 대상이 아니다.)
    private static final Set<String> EXECUTE_METHODS =
            Set.of("execute", "executeQuery", "executeUpdate", "executeLargeUpdate", "executeBatch", "executeLargeBatch");

    private final Statement target;
    // Connection.prepareStatement(sql) 시점에 이미 SQL 문자열을 알고 있는 경우(PreparedStatement)는
    // 여기 미리 저장해둔다. 반면 plain Statement는 execute(sql) 호출 시점에야 SQL을 알 수 있으므로 null로 둔다.
    private final String preparedSql;
    private final MetricEventPublisher publisher;
    private final QueryMetricSource source;

    // 왜 ConcurrentHashMap인가?
    //  - Statement 자체는 스레드 세이프하지 않지만, 커넥션 풀/비동기 프레임워크 조합에서
    //    호출 스레드가 항상 같다는 보장을 인터셉터 레벨에서 강제하고 싶지 않았다.
    //    별도 동기화 로직을 직접 짜는 대신 검증된 동시성 컬렉션에 위임해 버그 여지를 줄였다.
    private final Map<Integer, Object> boundParams = new ConcurrentHashMap<>();

    StatementInvocationHandler(Statement target, String preparedSql, MetricEventPublisher publisher,
                               QueryMetricSource source) {
        this.target = target;
        this.preparedSql = preparedSql;
        this.publisher = publisher;
        this.source = source;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();

        if (isParameterSetter(name, args)) {
            boundParams.put((Integer) args[0], args[1]);
        }

        if (EXECUTE_METHODS.contains(name)) {
            return timedExecute(method, args, name);
        }

        // 그 외 메서드(close, getResultSet, setFetchSize ...)는 그냥 원본으로 위임.
        // 여기서 일일이 위임 메서드를 작성하지 않아도 되는 것이 Proxy 방식의 장점이다.
        return invokeTarget(method, args);
    }

    private boolean isParameterSetter(String methodName, Object[] args) {
        return methodName.startsWith("set")
                && args != null
                && args.length >= 2
                && args[0] instanceof Integer;
    }

    private Object timedExecute(Method method, Object[] args, String methodName) throws Throwable {
        String executedSql = resolveSql(args, methodName);
        // 왜 System.nanoTime()인가?
        //  - System.currentTimeMillis()는 벽시계 시각이라 NTP 보정 등으로 역행할 수 있다.
        //    구간 시간(duration) 측정에는 단조 증가가 보장되는 nanoTime()을 쓰는 것이 표준 권장 방식이다.
        long start = System.nanoTime();
        boolean succeeded = false;
        String sqlState = null;
        try {
            Object result = invokeTarget(method, args);
            succeeded = true;
            return result;
        } catch (Throwable ex) {
            if (ex instanceof SQLException sqlException) {
                sqlState = sqlException.getSQLState();
            }
            throw ex;
        } finally {
            // 예외가 나도(쿼리 실패) finally에서 이벤트를 발행한다.
            // 실패한 쿼리도 "얼마나 걸려서 실패했는지"는 모니터링 관점에서 중요한 데이터이기 때문이다.
            //
            // 왜 밀리초가 아니라 마이크로초로 기록하는가?
            //  - nanoTime 차이를 1_000_000으로 정수 나눗셈하면 소수점이 버려진다. 그런데 실제
            //    쿼리 상당수는 1ms 미만이라(특히 인메모리 DB) 0.3ms도 0.9ms도 전부 0이 되어,
            //    평균/p95/추이 같은 집계가 통째로 무의미해진다.
            //  - 1_000으로 나누면 0.3ms가 300으로 남아 분포가 살아난다. long 마이크로초는
            //    수십만 년치 구간까지 표현 가능하므로 오버플로 걱정도 없다.
            //  - 표시 단위(ms)로의 변환은 값을 잃지 않는 방향이므로 대시보드 표시 계층에서 한다.
            long durationUs = (System.nanoTime() - start) / 1_000;
            List<Object> params = snapshotParams();
            publisher.publish(new QueryMetricEvent(
                    UUID.randomUUID(),
                    TransactionContext.currentId(),
                    source.appName(),
                    source.environment(),
                    source.instanceId(),
                    source.datasourceName(),
                    source.dbType(),
                    executedSql,
                    SqlNormalizer.normalize(executedSql),
                    params,
                    params.size(),
                    durationUs,
                    Instant.now(),
                    Thread.currentThread().getName(),
                    succeeded,
                    sqlState));
        }
    }

    private String resolveSql(Object[] args, String methodName) {
        if (preparedSql != null) {
            return preparedSql;
        }
        // plain Statement의 execute(sql), executeQuery(sql), executeUpdate(sql)은
        // 첫 인자로 SQL 문자열을 직접 받는다. executeBatch류는 인자가 없어 SQL을 알 수 없다.
        if (!"executeBatch".equals(methodName) && !"executeLargeBatch".equals(methodName)
                && args != null && args.length > 0 && args[0] instanceof String sql) {
            return sql;
        }
        return "UNKNOWN";
    }

    private List<Object> snapshotParams() {
        if (boundParams.isEmpty()) {
            return List.of();
        }
        // 왜 TreeMap으로 한번 감싸는가?
        //  - ConcurrentHashMap은 삽입/인덱스 순서를 보장하지 않는다.
        //    바인딩 파라미터는 "1번, 2번, 3번..." 순서로 노출되어야 로그를 읽는 사람이
        //    SQL의 '?' 순서와 매칭할 수 있으므로, 파라미터 인덱스를 키로 정렬해서 값만 뽑아낸다.
        return new ArrayList<>(new TreeMap<>(boundParams).values());
    }

    private Object invokeTarget(Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            // 리플렉션 호출은 실제 예외를 InvocationTargetException으로 한번 감싸버린다.
            // 그대로 던지면 호출부(JDBC 클라이언트, ORM 등)가 SQLException을 못 잡고
            // InvocationTargetException을 받게 되므로, 원래 예외를 꺼내서 다시 던져 투명성을 유지한다.
            throw e.getTargetException();
        }
    }
}
