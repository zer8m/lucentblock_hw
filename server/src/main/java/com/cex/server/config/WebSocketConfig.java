package com.cex.server.config;

import com.cex.server.ws.TradeBroadcaster;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final TradeBroadcaster tradeBroadcaster;

    public WebSocketConfig(TradeBroadcaster tradeBroadcaster) {
        this.tradeBroadcaster = tradeBroadcaster;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 프론트 dev 서버(:5173)가 직접 붙는다. CorsConfig와 같은 개발용 전체 허용 정책.
        registry.addHandler(tradeBroadcaster, "/ws").setAllowedOrigins("*");
    }
}
