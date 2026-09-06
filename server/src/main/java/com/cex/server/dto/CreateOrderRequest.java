package com.cex.server.dto;

/** 클라이언트 -> 서버 주문 접수. POST /api/v1/orders */
public record CreateOrderRequest(
        Long accountId,
        String symbol,
        String side,
        Long price,
        Long qty
) {}
