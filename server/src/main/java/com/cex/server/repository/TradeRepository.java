package com.cex.server.repository;

import com.cex.server.dto.TradeEventRequest;
import com.cex.server.dto.TradeResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class TradeRepository {

    private final JdbcTemplate jdbc;

    public TradeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 멱등성의 핵심. trade_id가 엔진 발급값 그대로 PK이므로 같은 이벤트가 두 번 오면
     * 여기서 PK 충돌이 나고 false를 돌려준다 -> 잔고는 손대지 않는다.
     *
     * 트랜잭션 안에서 이 예외를 잡아도 되는 이유: JdbcTemplate + DataSourceTransactionManager는
     * 예외가 @Transactional 경계를 벗어날 때만 롤백한다. MySQL도 중복키 실패는 해당 statement만 되돌린다.
     */
    public boolean insertIfAbsent(TradeEventRequest e) {
        try {
            jdbc.update("""
                    INSERT INTO trade
                        (trade_id, symbol, buy_order_id, sell_order_id, price, qty, taker_side, ts_ms)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    e.tradeId(), e.symbol(), e.buyOrderId(), e.sellOrderId(),
                    e.price(), e.qty(), e.takerSide(), e.tsMs());
            return true;
        } catch (DuplicateKeyException ex) {
            return false;
        }
    }

    public List<TradeResponse> findRecent(String symbol, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT trade_id, symbol, buy_order_id, sell_order_id, price, qty, taker_side, ts_ms
                  FROM trade
                 WHERE 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        if (symbol != null && !symbol.isBlank()) {
            sql.append(" AND symbol = ? ");
            args.add(symbol);
        }
        sql.append(" ORDER BY trade_id DESC LIMIT ? ");
        args.add(limit);

        return jdbc.query(sql.toString(), (rs, i) -> new TradeResponse(
                rs.getLong("trade_id"),
                rs.getString("symbol"),
                rs.getLong("buy_order_id"),
                rs.getLong("sell_order_id"),
                rs.getLong("price"),
                rs.getLong("qty"),
                rs.getString("taker_side"),
                rs.getLong("ts_ms")), args.toArray());
    }
}
