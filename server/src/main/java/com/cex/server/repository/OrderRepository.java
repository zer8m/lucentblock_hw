package com.cex.server.repository;

import com.cex.server.dto.CreateOrderRequest;
import com.cex.server.dto.OrderResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class OrderRepository {

    private final JdbcTemplate jdbc;

    public OrderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** OPEN 상태로 INSERT 하고 AUTO_INCREMENT로 생성된 order_id를 돌려준다. */
    public long insertOpen(CreateOrderRequest req, LocalDateTime createdAt) {
        String sql = """
                INSERT INTO orders
                    (account_id, symbol, side, price, qty, filled_qty, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 0, 'OPEN', ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, req.accountId());
            ps.setString(2, req.symbol());
            ps.setString(3, req.side());
            ps.setLong(4, req.price());
            ps.setLong(5, req.qty());
            ps.setTimestamp(6, Timestamp.valueOf(createdAt));
            ps.setTimestamp(7, Timestamp.valueOf(createdAt));
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("order_id 채번 실패");
        }
        return key.longValue();
    }

    /**
     * 정산 중 같은 주문에 두 체결이 동시에 들어오는 것을 막기 위한 행 잠금.
     * 호출부에서 order_id 오름차순으로 부르면 데드락을 피할 수 있다.
     */
    public void lockForUpdate(long orderId) {
        jdbc.queryForList("SELECT order_id FROM orders WHERE order_id = ? FOR UPDATE",
                Long.class, orderId);
    }

    public Optional<OrderRow> findById(long orderId) {
        List<OrderRow> rows = jdbc.query("""
                SELECT order_id, account_id, symbol, side, price, qty, filled_qty, status, created_at
                  FROM orders WHERE order_id = ?
                """, ORDER_ROW_MAPPER, orderId);
        return rows.stream().findFirst();
    }

    /**
     * 체결 수량 반영.
     *
     * 주의: MySQL은 SET 절을 왼쪽부터 순서대로 평가하고, 뒤쪽 식은 이미 갱신된 값을 본다.
     * 그래서 status를 filled_qty보다 반드시 먼저 써야 예전 filled_qty 기준으로 판정된다.
     * 순서를 바꾸면 체결량이 두 번 더해진 값으로 FILLED 판정이 나버린다.
     *
     * 이미 CANCELED된 주문에는 반영하지 않는다 -> 0 반환.
     */
    public int applyFill(long orderId, long fillQty) {
        return jdbc.update("""
                UPDATE orders
                   SET status = CASE WHEN filled_qty + ? >= qty THEN 'FILLED' ELSE 'PARTIALLY_FILLED' END,
                       filled_qty = filled_qty + ?,
                       updated_at = NOW(3)
                 WHERE order_id = ?
                   AND status IN ('OPEN', 'PARTIALLY_FILLED')
                """, fillQty, fillQty, orderId);
    }

    public List<OrderResponse> findByAccount(long accountId, String status, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT order_id, account_id, symbol, side, price, qty, filled_qty, status, created_at
                  FROM orders
                 WHERE account_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(accountId);
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ? ");
            args.add(status);
        }
        sql.append(" ORDER BY order_id DESC LIMIT ? ");
        args.add(limit);

        return jdbc.query(sql.toString(), (rs, i) -> new OrderResponse(
                rs.getLong("order_id"),
                rs.getLong("account_id"),
                rs.getString("symbol"),
                rs.getString("side"),
                rs.getLong("price"),
                rs.getLong("qty"),
                rs.getLong("filled_qty"),
                rs.getString("status"),
                rs.getTimestamp("created_at").getTime()), args.toArray());
    }

    private static final RowMapper<OrderRow> ORDER_ROW_MAPPER =
            (rs, i) -> new OrderRow(
                    rs.getLong("order_id"),
                    rs.getLong("account_id"),
                    rs.getString("symbol"),
                    rs.getString("side"),
                    rs.getLong("price"),
                    rs.getLong("qty"),
                    rs.getLong("filled_qty"),
                    rs.getString("status"),
                    rs.getTimestamp("created_at").toLocalDateTime());
}
