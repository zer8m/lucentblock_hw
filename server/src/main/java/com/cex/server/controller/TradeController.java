package com.cex.server.controller;

import com.cex.server.dto.TradeListResponse;
import com.cex.server.repository.TradeRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trades")
public class TradeController {

    private final TradeRepository tradeRepository;

    public TradeController(TradeRepository tradeRepository) {
        this.tradeRepository = tradeRepository;
    }

    @GetMapping
    public TradeListResponse list(
            @RequestParam(value = "symbol", required = false) String symbol,
            @RequestParam(value = "limit", required = false) Integer limit) {
        int capped = (limit == null || limit <= 0) ? 50 : Math.min(limit, 200);
        return new TradeListResponse(tradeRepository.findRecent(symbol, capped));
    }
}
