package com.cex.server.dto;

public record CreateOrderResponse(
        long orderId,
        String status,
        long createdAt   // 유닉스 밀리초
) {}