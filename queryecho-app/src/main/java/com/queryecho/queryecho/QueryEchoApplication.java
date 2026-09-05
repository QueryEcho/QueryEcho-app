package com.queryecho.queryecho;

import com.queryecho.queryecho.collector.config.QueryEchoCollectorProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * QueryEcho 수집 서버 + 대시보드 애플리케이션.
 *
 * 왜 scanBasePackages로 스캔 대상을 일일이 나열했는가 (= sdk 패키지를 일부러 뺐는가)?
 *  - 이 클래스가 com.queryecho.queryecho 에 있어서, 아무 설정도 안 하면 컴포넌트 스캔이
 *    com.queryecho.queryecho.sdk 까지 덮어버린다. 그러면 SDK가 "스캔되어" 동작하기 때문에,
 *    정작 SDK를 라이브러리로 가져다 쓰는 외부 애플리케이션(패키지가 다르므로 스캔이 안 됨)에서
 *    동작하지 않는다는 사실을 이 프로젝트 안에서는 영영 발견할 수 없다. 실제로 그렇게
 *    "우연히 동작하던" 상태였다.
 *  - 스캔에서 빼두면 이 애플리케이션도 외부 타깃 앱과 똑같이 자동 구성
 *    ({@link com.queryecho.spring.boot4.QueryEchoAutoConfiguration})을 통해서만
 *    SDK를 켜게 된다. 즉 이 앱을 띄워보는 것 자체가 "라이브러리로 붙였을 때 잘 되는가"에 대한
 *    검증이 된다.
 *
 * QueryEchoSdkProperties가 @EnableConfigurationProperties 목록에서 빠진 것도 같은 이유다.
 * SDK 설정 바인딩은 SDK 자동 구성이 스스로 책임진다.
 *
 * (Spring Boot 4의 @SpringBootApplication에는 excludeFilters 속성이 없어서, 제외 대신
 *  포함할 패키지를 나열하는 방식을 썼다. 이 클래스가 있는 com.queryecho.queryecho 자체는
 *  나열하지 않았는데, 메인 클래스는 스캔과 무관하게 설정 클래스로 등록되고 그 패키지에
 *  직접 놓인 다른 컴포넌트는 없기 때문이다. 새 최상위 패키지를 추가하면 여기에도 넣어야 한다.)
 */
@SpringBootApplication(scanBasePackages = {
        "com.queryecho.queryecho.collector",
        "com.queryecho.queryecho.dashboard",
        "com.queryecho.queryecho.demo",
        "com.queryecho.queryecho.config"
})
@EnableConfigurationProperties(QueryEchoCollectorProperties.class)
public class QueryEchoApplication {

    public static void main(String[] args) {
        SpringApplication.run(QueryEchoApplication.class, args);
    }

}
