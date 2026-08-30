package com.cex.server.dto;

import java.time.LocalDateTime;

public record OrderResponse(
        long orderId,
        long accountId,
        String symbol,
        String side,
        long price,
        long qty,
        long filledQty,
        String status,
        long createdAt
) {}
