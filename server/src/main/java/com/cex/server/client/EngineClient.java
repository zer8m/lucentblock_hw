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
        var factory = new org.springframework.http.client.JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofMillis(1000));

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
            String body = String.format(
                    "{\"order_id\":%d,\"symbol\":\"%s\",\"side\":\"%s\",\"price\":%d,\"qty\":%d,\"ts_ms\":%d}",
                    req.orderId(), req.symbol(), req.side(), req.price(), req.qty(), req.tsMs());

            var headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            var entity = new org.springframework.http.HttpEntity<>(body, headers);

            EngineOrderResponse res = restTemplate.postForObject(
                    baseUrl + "/engine/orders", entity, EngineOrderResponse.class);
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