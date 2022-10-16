package nextstep.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import nextstep.utils.DataAccessUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DataSourceUtils;

public class JdbcTemplate {

    private static final Logger log = LoggerFactory.getLogger(JdbcTemplate.class);

    private final DataSource dataSource;

    public JdbcTemplate(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void update(final String sql, Object... parameters) {
        execute(sql, pstmt -> {
            setParameter(pstmt, parameters);
            return pstmt.executeUpdate();
        });
    }

    public <T> List<T> query(final String sql, final RowMapper<T> rse, final Object... parameters) {
        return execute(sql, pstmt -> {
            setParameter(pstmt, parameters);
            final List<T> results = extractInstance(rse, pstmt);
            validateNotNull(results);
            return results;
        });
    }

    public <T> T queryForObject(final String sql, final RowMapper<T> rse, final Object... conditions) {
        return DataAccessUtils.nullableSingleResult(query(sql, rse, conditions));
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    private <T> T execute(final String sql, final PreparedStatementCallback<T> callback) {
        validateSql(sql);

        try {
            final Connection connection = DataSourceUtils.getConnection(dataSource);
            final PreparedStatement pstmt = connection.prepareStatement(sql);
            log.debug("query : {}", sql);
            final T result = callback.doPreparedStatement(pstmt);
            pstmt.close();
            return result;
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            throw new DataAccessException(e);
        }
    }

    private void validateSql(String sql) {
        if (sql == null) {
            throw new IllegalArgumentException("SQL must not be null");
        }
    }

    private <T> void validateNotNull(List<T> result) {
        if (result.isEmpty()) {
            throw new DataAccessException("No Such Result");
        }
    }

    private void setParameter(PreparedStatement pstmt, Object... conditions) throws SQLException {
        for (int i = 0; i < conditions.length; i++) {
            pstmt.setObject(i + 1, conditions[i]);
        }
    }

    private <T> List<T> extractInstance(RowMapper<T> rse, PreparedStatement pstmt) throws SQLException {
        List<T> results = new ArrayList<>();
        int rowNum = 0;
        try (final ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                results.add(rse.mapRow(rs, rowNum++));
            }
            return results;
        }
    }
}
