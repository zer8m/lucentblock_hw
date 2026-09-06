package com.cex.server.controller;

import com.cex.server.dto.BalanceResponse;
import com.cex.server.repository.BalanceRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class BalanceController {

    private final BalanceRepository balanceRepository;

    public BalanceController(BalanceRepository balanceRepository) {
        this.balanceRepository = balanceRepository;
    }

    @GetMapping("/balances")
    public BalanceResponse getBalances(@RequestParam long account_id) {
        return new BalanceResponse(
                account_id,
                balanceRepository.findByAccountId(account_id)
        );
    }
}