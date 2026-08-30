package com.cex.server.repository;

import com.cex.server.dto.BalanceResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class BalanceRepository {

    private final JdbcTemplate jdbc;

    public BalanceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<BalanceResponse.Item> findByAccountId(long accountId) {
        String sql = "SELECT asset, available, locked FROM balance WHERE account_id = ?";
        return jdbc.query(sql, (rs, rowNum) -> new BalanceResponse.Item(
                rs.getString("asset"),
                rs.getLong("available"),
                rs.getLong("locked")
        ), accountId);
    }

    /** 주문 접수 시점: available -> locked. 잔고가 부족하면 0을 반환한다. */
    public int lock(long accountId, String asset, long amount) {
        String sql = """
                UPDATE balance
                   SET available = available - ?,
                       locked    = locked + ?
                 WHERE account_id = ?
                   AND asset = ?
                   AND available >= ?
                """;
        return jdbc.update(sql, amount, amount, accountId, asset, amount);
    }

    /** 정산용 잔고 증감. 두 델타 모두 음수 가능. 잔고 행이 없으면 새로 만든다. */
    public void apply(long accountId, String asset, long lockedDelta, long availableDelta) {
        String sql = """
                UPDATE balance
                   SET available = available + ?,
                       locked    = locked + ?
                 WHERE account_id = ?
                   AND asset = ?
                """;
        int updated = jdbc.update(sql, availableDelta, lockedDelta, accountId, asset);
        if (updated == 0) {
            throw new IllegalStateException(
                    "잔고 행이 없습니다. account_id=" + accountId + ", asset=" + asset);
        }
    }

    /** 정산 후 안전망. 음수 잔고가 나오면 예외를 던져 트랜잭션을 되돌린다. */
    public void assertNonNegative(long accountId, String asset) {
        String sql = """
                SELECT COUNT(*) FROM balance
                 WHERE account_id = ? AND asset = ? AND (available < 0 OR locked < 0)
                """;
        Integer bad = jdbc.queryForObject(sql, Integer.class, accountId, asset);
        if (bad != null && bad > 0) {
            throw new IllegalStateException(
                    "잔고가 음수가 됐습니다. account_id=" + accountId + ", asset=" + asset);
        }
    }

    /** 없는 계정으로 주문 넣는 것 막기 */
    public boolean accountExists(long accountId) {
        String sql = "SELECT COUNT(*) FROM account WHERE account_id = ?";
        Integer c = jdbc.queryForObject(sql, Integer.class, accountId);
        return c != null && c > 0;
    }
}