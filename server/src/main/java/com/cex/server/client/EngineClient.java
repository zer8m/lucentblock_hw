package com.cex.server.client;

import com.cex.server.dto.EngineOrderRequest;
import com.cex.server.dto.EngineOrderResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Component
public class EngineClient {

    private static final Logger log = LoggerFactory.getLogger(EngineClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final boolean enabled;

    public EngineClient(@Value("${engine.base-url:http://localhost:9000}") String baseUrl,
                        @Value("${engine.enabled:false}") boolean enabled) {
        // 트랜잭션 안에서 호출되므로(그동안 DB 행 잠금을 쥐고 있다) 타임아웃을 짧게 잡는다.
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofMillis(500).toMillis());
        factory.setReadTimeout((int) Duration.ofMillis(1000).toMillis());

        this.restTemplate = new RestTemplate(factory);
        this.baseUrl = baseUrl;
        this.enabled = enabled;
    }

    /**
     * @return 엔진이 accepted:true를 돌려줬으면 true. 통신 실패/거절이면 false.
     *
     * engine.enabled=false 면 엔진이 아직 없다고 보고 통과시킨다.
     * 엔진 팀 코드가 붙으면 application.yml에서 true로만 바꾸면 된다.
     */
    public boolean sendOrder(EngineOrderRequest req) {
        if (!enabled) {
            log.info("[engine.enabled=false] 엔진 호출 생략. order_id={}", req.orderId());
            return true;
        }
        try {
            EngineOrderResponse res = restTemplate.postForObject(
                    baseUrl + "/engine/orders", req, EngineOrderResponse.class);
            if (res == null || !res.accepted()) {
                log.warn("엔진이 주문을 거절했습니다. order_id={}", req.orderId());
                return false;
            }
            return true;
        } catch (RestClientException ex) {
            log.warn("엔진 통신 실패. order_id={}, reason={}", req.orderId(), ex.getMessage());
            return false;
        }
    }
}