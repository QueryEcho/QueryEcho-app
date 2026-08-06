package com.queryecho.queryecho.sdk.publisher;

/**
 * sdk 계층(interceptor, transaction)이 관측한 지표를 내보내는 유일한 출구.
 *
 * 왜 클래스가 아니라 인터페이스로 바꿨는가?
 *  - 원래는 Spring의 ApplicationEventPublisher를 감싼 클래스 하나였지만, 이제 전송 방식이
 *    두 가지가 됐다: 같은 JVM 안으로 보내는 방식({@link LocalMetricEventPublisher})과
 *    원격 Collector 서버로 HTTP 전송하는 방식({@link HttpMetricEventPublisher}).
 *  - 인터셉터/Aspect 코드는 "publish(event)를 한 번 호출한다"는 사실만 알면 되고, 그게
 *    인메모리 이벤트인지 네트워크 전송인지는 알 필요가 없다. 이 인터페이스가 그 경계다.
 *    덕분에 전송 방식을 추가/교체해도(예: 나중에 메시지 큐) 계측 코드는 한 줄도 바뀌지 않는다.
 *
 * 구현체가 반드시 지켜야 하는 계약:
 *  - publish()는 예외를 밖으로 던지지 않아야 하고, 호출 스레드를 오래 붙잡지 않아야 한다.
 *    이 메서드는 실제 애플리케이션의 쿼리 실행 경로 한복판에서 호출되기 때문에,
 *    여기서 블로킹하거나 예외가 새어나가면 모니터링이 곧 서비스 장애가 된다.
 */
public interface MetricEventPublisher {

    void publish(Object event);
}
