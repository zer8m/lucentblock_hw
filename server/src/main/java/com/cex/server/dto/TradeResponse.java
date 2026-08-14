package com.cex.server.dto;

public record TradeResponse(
        long tradeId,
        String symbol,
        long buyOrderId,
        long sellOrderId,
        long price,
        long qty,
        String takerSide,
        long tsMs
) {}
