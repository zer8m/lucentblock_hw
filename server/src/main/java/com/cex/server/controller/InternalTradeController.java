package com.cex.server.controller;

import com.cex.server.dto.TradeEventRequest;
import com.cex.server.dto.TradeEventResponse;
import com.cex.server.service.SettlementService;
import com.cex.server.ws.TradeBroadcaster;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 엔진 전용 엔드포인트. 클라이언트에게 노출하지 않는다. */
@RestController
@RequestMapping("/internal")
public class InternalTradeController {

    private final SettlementService settlementService;
    private final TradeBroadcaster tradeBroadcaster;

    public InternalTradeController(SettlementService settlementService, TradeBroadcaster tradeBroadcaster) {
        this.settlementService = settlementService;
        this.tradeBroadcaster = tradeBroadcaster;
    }

    /**
     * 중복 이벤트여서 정산을 건너뛴 경우에도 received:true를 돌려준다.
     * 엔진이 "못 받았나?" 하고 무한 재전송하는 걸 막기 위해서다.
     */
    @PostMapping("/trades")
    public TradeEventResponse onTrade(@RequestBody TradeEventRequest req) {
        // settle이 true를 돌려준 시점엔 트랜잭션이 커밋된 뒤라, 실제 반영된 체결만 방송된다.
        if (settlementService.settle(req)) {
            tradeBroadcaster.broadcast(toJson(req));
        }
        return new TradeEventResponse(true);
    }

    /** 프론트가 받는 스키마 = 엔진이 보낸 스키마 그대로(snake_case). 값이 전부 정수·고정 문자열이라 직접 조립한다. */
    private static String toJson(TradeEventRequest e) {
        return String.format(
                "{\"trade_id\":%d,\"symbol\":\"%s\",\"buy_order_id\":%d,\"sell_order_id\":%d,\"price\":%d,\"qty\":%d,\"taker_side\":\"%s\",\"ts_ms\":%d}",
                e.tradeId(), e.symbol(), e.buyOrderId(), e.sellOrderId(), e.price(), e.qty(), e.takerSide(), e.tsMs());
    }
}
