package com.cex.server.service;

import com.cex.server.client.EngineClient;
import com.cex.server.common.Symbols;
import com.cex.server.dto.CreateOrderRequest;
import com.cex.server.dto.CreateOrderResponse;
import com.cex.server.dto.EngineOrderRequest;
import com.cex.server.dto.OrderListResponse;
import com.cex.server.error.ApiException;
import com.cex.server.error.ErrorCode;
import com.cex.server.repository.BalanceRepository;
import com.cex.server.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final BalanceRepository balanceRepository;
    private final EngineClient engineClient;

    public OrderService(OrderRepository orderRepository,
                        BalanceRepository balanceRepository,
                        EngineClient engineClient) {
        this.orderRepository = orderRepository;
        this.balanceRepository = balanceRepository;
        this.engineClient = engineClient;
    }

    /**
     * 검증 -> 잔고 lock -> orders INSERT(OPEN) -> 엔진 전달, 전부 한 트랜잭션.
     * 엔진 전달이 실패하면 예외를 던져 lock과 INSERT를 통째로 롤백하고 503을 반환한다.
     */
    @Transactional
    public CreateOrderResponse create(CreateOrderRequest req) {
        validate(req);

        String base = Symbols.base(req.symbol());
        String quote = Symbols.quote(req.symbol());
        boolean isBuy = "BUY".equals(req.side());

        // 매수면 현금을, 매도면 코인을 잠근다.
        String lockAsset = isBuy ? quote : base;
        long lockAmount = isBuy ? req.price() * req.qty() : req.qty();

        if (balanceRepository.lock(req.accountId(), lockAsset, lockAmount) == 0) {
            throw new ApiException(ErrorCode.INSUFFICIENT_BALANCE,
                    lockAsset + " 잔고가 부족합니다. 필요=" + lockAmount);
        }

        LocalDateTime createdAt = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        long orderId = orderRepository.insertOpen(req, createdAt);

        // 엔진 호출은 트랜잭션 안에서 일어난다. 명세대로 롤백을 보장하려면 이 자리가 맞지만,
        // 그동안 balance/orders 행 잠금을 쥐고 있으므로 타임아웃을 짧게 잡아둔다(EngineClient).
        boolean accepted = engineClient.sendOrder(new EngineOrderRequest(
                orderId, req.symbol(), req.side(), req.price(), req.qty(), System.currentTimeMillis()));

        if (!accepted) {
            throw new ApiException(ErrorCode.ENGINE_UNAVAILABLE,
                    "매칭 엔진이 주문을 받지 못했습니다. 주문은 접수되지 않았습니다.");
        }

        log.info("주문 접수 완료. order_id={}, account_id={}, {} {} qty={} price={}",
                orderId, req.accountId(), req.symbol(), req.side(), req.qty(), req.price());

        return new CreateOrderResponse(orderId, "OPEN",
                createdAt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());    }

    @Transactional(readOnly = true)
    public OrderListResponse list(long accountId, String status, Integer limit) {
        int capped = (limit == null || limit <= 0) ? 50 : Math.min(limit, 200);
        if (status != null && !status.isBlank()
                && !List.of("OPEN", "PARTIALLY_FILLED", "FILLED", "CANCELED").contains(status)) {
            throw new ApiException(ErrorCode.INVALID_PARAM, "status 값이 올바르지 않습니다: " + status);
        }
        return new OrderListResponse(orderRepository.findByAccount(accountId, status, capped));
    }

    private void validate(CreateOrderRequest req) {
        if (req == null || req.accountId() == null) {
            throw new ApiException(ErrorCode.INVALID_PARAM, "account_id는 필수입니다.");
        }
        if (!Symbols.isSupported(req.symbol())) {
            throw new ApiException(ErrorCode.INVALID_PARAM, "지원하지 않는 symbol입니다: " + req.symbol());
        }
        if (!"BUY".equals(req.side()) && !"SELL".equals(req.side())) {
            throw new ApiException(ErrorCode.INVALID_PARAM, "side는 BUY 또는 SELL이어야 합니다.");
        }
        if (req.price() == null || req.price() <= 0) {
            throw new ApiException(ErrorCode.INVALID_PARAM, "price는 0보다 큰 정수여야 합니다.");
        }
        if (req.qty() == null || req.qty() <= 0) {
            throw new ApiException(ErrorCode.INVALID_PARAM, "qty는 0보다 큰 정수여야 합니다.");
        }
        if (!balanceRepository.accountExists(req.accountId())) {
            throw new ApiException(ErrorCode.INVALID_PARAM, "존재하지 않는 account_id입니다: " + req.accountId());
        }
    }
}
