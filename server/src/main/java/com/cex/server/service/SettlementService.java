package com.cex.server.service;

import com.cex.server.common.Symbols;
import com.cex.server.dto.TradeEventRequest;
import com.cex.server.error.ApiException;
import com.cex.server.error.ErrorCode;
import com.cex.server.repository.BalanceRepository;
import com.cex.server.repository.OrderRepository;
import com.cex.server.repository.OrderRow;
import com.cex.server.repository.TradeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 체결 이벤트 정산.
 *
 * 매수자: 코인 +, 현금 -
 * 매도자: 코인 -, 현금 +
 * 잔고는 주문 접수 때 이미 locked로 잡아뒀으므로, 여기서는
 * "locked에서 빼고 상대 자산의 available에 더한다"가 전부다.
 * 모든 금액/수량은 정수(long). 곱셈은 price * qty 한 번뿐이라 오버플로 여유가 충분하다.
 */
@Service
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);

    private final TradeRepository tradeRepository;
    private final OrderRepository orderRepository;
    private final BalanceRepository balanceRepository;

    public SettlementService(TradeRepository tradeRepository,
                             OrderRepository orderRepository,
                             BalanceRepository balanceRepository) {
        this.tradeRepository = tradeRepository;
        this.orderRepository = orderRepository;
        this.balanceRepository = balanceRepository;
    }

    /** @return 실제로 정산했으면 true, 중복 이벤트라 건너뛰었으면 false */
    @Transactional
    public boolean settle(TradeEventRequest e) {
        validate(e);

        // 1) 멱등성 게이트. 중복이면 여기서 끝. 잔고는 손대지 않는다.
        if (!tradeRepository.insertIfAbsent(e)) {
            log.info("중복 체결 이벤트라 정산 건너뜀. trade_id={}", e.tradeId());
            return false;
        }

        // 2) 두 주문 행을 order_id 오름차순으로 잠근다 (데드락 방지)
        long low = Math.min(e.buyOrderId(), e.sellOrderId());
        long high = Math.max(e.buyOrderId(), e.sellOrderId());
        orderRepository.lockForUpdate(low);
        if (high != low) {
            orderRepository.lockForUpdate(high);
        }

        OrderRow buy = orderRepository.findById(e.buyOrderId())
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND,
                        "매수 주문 없음. order_id=" + e.buyOrderId()));
        OrderRow sell = orderRepository.findById(e.sellOrderId())
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND,
                        "매도 주문 없음. order_id=" + e.sellOrderId()));

        String base = Symbols.base(e.symbol());   // BTC
        String quote = Symbols.quote(e.symbol()); // KRW

        long qty = e.qty();
        long cash = e.price() * qty; // 실제 체결 대금

        // 3) 매수자 정산
        // 주문 접수 때 잠근 금액은 "주문가 x 수량"이다. 체결가가 주문가보다 좋으면(더 싸면)
        // 차액이 남으므로 그만큼 available로 돌려준다. 이걸 빼먹으면 현금이 locked에 갇힌다.
        long lockedUsed = buy.price() * qty;
        long refund = lockedUsed - cash;
        if (refund < 0) {
            // 지정가 매수라면 체결가 <= 주문가여야 정상. 음수면 엔진 쪽 값이 이상한 것.
            log.warn("체결가가 매수 주문가보다 높습니다. trade_id={}, buy_price={}, trade_price={}",
                    e.tradeId(), buy.price(), e.price());
            refund = 0;
        }
        balanceRepository.apply(buy.accountId(), quote, -lockedUsed, refund); // 현금 locked -, 차액 환급
        balanceRepository.apply(buy.accountId(), base, 0, qty);               // 코인 available +

        // 4) 매도자 정산
        balanceRepository.apply(sell.accountId(), base, -qty, 0);   // 코인 locked -
        balanceRepository.apply(sell.accountId(), quote, 0, cash);  // 현금 available +

        // 5) 주문 체결량/상태 갱신
        if (orderRepository.applyFill(buy.orderId(), qty) == 0) {
            log.warn("이미 종료된 매수 주문에 체결이 들어왔습니다. order_id={}, status={}",
                    buy.orderId(), buy.status());
        }
        if (orderRepository.applyFill(sell.orderId(), qty) == 0) {
            log.warn("이미 종료된 매도 주문에 체결이 들어왔습니다. order_id={}, status={}",
                    sell.orderId(), sell.status());
        }

        // 6) 안전망: 음수 잔고가 생기면 전부 롤백
        balanceRepository.assertNonNegative(buy.accountId(), quote);
        balanceRepository.assertNonNegative(buy.accountId(), base);
        balanceRepository.assertNonNegative(sell.accountId(), quote);
        balanceRepository.assertNonNegative(sell.accountId(), base);

        log.info("정산 완료. trade_id={}, {} qty={} price={}", e.tradeId(), e.symbol(), qty, e.price());
        return true;
    }

    private void validate(TradeEventRequest e) {
        if (e == null || e.tradeId() == null || e.buyOrderId() == null || e.sellOrderId() == null) {
            throw new ApiException(ErrorCode.INVALID_PARAM, "trade_id / buy_order_id / sell_order_id 필수");
        }
        if (e.price() == null || e.price() <= 0) {
            throw new ApiException(ErrorCode.INVALID_PARAM, "price는 0보다 큰 정수여야 합니다.");
        }
        if (e.qty() == null || e.qty() <= 0) {
            throw new ApiException(ErrorCode.INVALID_PARAM, "qty는 0보다 큰 정수여야 합니다.");
        }
        Symbols.base(e.symbol()); // 지원 종목인지 검증
    }
}
