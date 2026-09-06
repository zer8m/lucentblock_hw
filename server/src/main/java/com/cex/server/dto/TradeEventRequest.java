package com.cex.server.dto;

/**
 * 엔진 -> 서버 체결 이벤트. POST /internal/trades
 *
 * price / qty는 팀 확정대로 정수. 소수가 들어오면 파싱 단계에서 바로 실패한다(= 잘못된 값이 조용히
 * 반올림돼서 저장되는 일이 없다).
 */
public record TradeEventRequest(
        Long tradeId,
        String symbol,
        Long buyOrderId,
        Long sellOrderId,
        Long price,
        Long qty,
        String takerSide,
        Long tsMs
) {}
