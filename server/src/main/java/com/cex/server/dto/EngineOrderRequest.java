package com.cex.server.dto;

/** 서버 -> 엔진 주문 전달. POST /engine/orders */
import com.fasterxml.jackson.annotation.JsonProperty;

public record EngineOrderRequest(
        @JsonProperty("order_id") Long orderId,
        String symbol,
        String side,
        Long price,
        Long qty,
        @JsonProperty("ts_ms") Long tsMs
) {}
