package com.cex.server.dto;

/** 서버 -> 엔진 주문 전달. POST /engine/orders */
public record EngineOrderRequest(
        long orderId,
        String symbol,
        String side,
        long price,
        long qty,
        long tsMs
) {}
