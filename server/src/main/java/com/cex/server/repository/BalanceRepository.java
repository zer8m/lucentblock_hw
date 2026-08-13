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
}