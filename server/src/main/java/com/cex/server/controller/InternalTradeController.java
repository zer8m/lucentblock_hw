package com.cex.server.controller;

import com.cex.server.dto.TradeEventRequest;
import com.cex.server.dto.TradeEventResponse;
import com.cex.server.service.SettlementService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 엔진 전용 엔드포인트. 클라이언트에게 노출하지 않는다. */
@RestController
@RequestMapping("/internal")
public class InternalTradeController {

    private final SettlementService settlementService;

    public InternalTradeController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    /**
     * 중복 이벤트여서 정산을 건너뛴 경우에도 received:true를 돌려준다.
     * 엔진이 "못 받았나?" 하고 무한 재전송하는 걸 막기 위해서다.
     */
    @PostMapping("/trades")
    public TradeEventResponse onTrade(@RequestBody TradeEventRequest req) {
        settlementService.settle(req);
        return new TradeEventResponse(true);
    }
}
