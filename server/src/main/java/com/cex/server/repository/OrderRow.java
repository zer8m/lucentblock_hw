package com.cex.server.repository;

import java.time.LocalDateTime;

/** orders 테이블 한 줄 (내부용) */
public record OrderRow(
        long orderId,
        long accountId,
        String symbol,
        String side,
        long price,
        long qty,
        long filledQty,
        String status,
        LocalDateTime createdAt
) {}
