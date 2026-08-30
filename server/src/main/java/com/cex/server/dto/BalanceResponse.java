package com.cex.server.dto;

import java.util.List;

public record BalanceResponse(
        long account_id,
        List<Item> balances
) {
    public record Item(String asset, long available, long locked) {}
}