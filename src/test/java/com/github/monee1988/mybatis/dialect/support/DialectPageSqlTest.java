package com.github.monee1988.mybatis.dialect.support;

import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分页 SQL 生成测试，重点覆盖锁子句语句的 LIMIT 位置与 Oracle 的显式拒绝。
 *
 * <p>MySQL / PostgreSQL 语法要求分页子句写在锁子句之前，
 * 若把 LIMIT 直接追加到 {@code FOR UPDATE} 之后会得到非法 SQL。</p>
 *
 * @author monee1988
 */
class DialectPageSqlTest {

    private static final RowBounds SECOND_PAGE = new RowBounds(10, 10);

    @Test
    @DisplayName("MySQL：普通语句在末尾追加 LIMIT")
    void mysqlAppendsLimit() {
        assertEquals("SELECT a FROM t ORDER BY a LIMIT 10,10",
                new MySqlDialect().getDialectPageSql("SELECT a FROM t ORDER BY a", SECOND_PAGE));
    }

    @Test
    @DisplayName("MySQL：FOR UPDATE 语句把 LIMIT 插到锁子句之前")
    void mysqlInsertsLimitBeforeForUpdate() {
        String sql = new MySqlDialect()
                .getDialectPageSql("SELECT a FROM t WHERE id = 1 FOR UPDATE", SECOND_PAGE);
        assertEquals("SELECT a FROM t WHERE id = 1 LIMIT 10,10 FOR UPDATE", sql);
        assertFalse(sql.contains("FOR UPDATE LIMIT"), "LIMIT 不能出现在 FOR UPDATE 之后");
    }

    @Test
    @DisplayName("PostgreSQL：FOR UPDATE 语句把 LIMIT/OFFSET 插到锁子句之前")
    void postgresInsertsLimitBeforeForUpdate() {
        String sql = new PostgreSqlDialect()
                .getDialectPageSql("SELECT a FROM t WHERE id = 1 FOR UPDATE", SECOND_PAGE);
        assertEquals("SELECT a FROM t WHERE id = 1 LIMIT 10 OFFSET 10 FOR UPDATE", sql);
        assertFalse(sql.contains("FOR UPDATE LIMIT"), "LIMIT 不能出现在 FOR UPDATE 之后");
    }

    @Test
    @DisplayName("PostgreSQL：普通语句在末尾追加 LIMIT/OFFSET")
    void postgresAppendsLimitOffset() {
        assertEquals("SELECT a FROM t LIMIT 10 OFFSET 10",
                new PostgreSqlDialect().getDialectPageSql("SELECT a FROM t", SECOND_PAGE));
    }

    @Test
    @DisplayName("MySQL：FOR SHARE / LOCK IN SHARE MODE 同样把 LIMIT 插到锁子句之前")
    void mysqlHandlesEveryLockClauseVariant() {
        assertEquals("SELECT a FROM t LIMIT 10,10 FOR SHARE",
                new MySqlDialect().getDialectPageSql("SELECT a FROM t FOR SHARE", SECOND_PAGE));
        assertEquals("SELECT a FROM t LIMIT 10,10 LOCK IN SHARE MODE",
                new MySqlDialect().getDialectPageSql("SELECT a FROM t LOCK IN SHARE MODE", SECOND_PAGE));
        assertEquals("SELECT a FROM t LIMIT 10,10 FOR UPDATE SKIP LOCKED",
                new MySqlDialect().getDialectPageSql("SELECT a FROM t FOR UPDATE SKIP LOCKED", SECOND_PAGE));
    }

    @Test
    @DisplayName("PostgreSQL：FOR NO KEY UPDATE 把 LIMIT/OFFSET 插到锁子句之前")
    void postgresHandlesNoKeyUpdate() {
        assertEquals("SELECT a FROM t LIMIT 10 OFFSET 10 FOR NO KEY UPDATE",
                new PostgreSqlDialect()
                        .getDialectPageSql("SELECT a FROM t FOR NO KEY UPDATE", SECOND_PAGE));
    }

    @Test
    @DisplayName("Oracle：锁子句语句不静默丢锁，直接抛 PersistenceException")
    void oracleRejectsLockClause() {
        OracleDialect dialect = new OracleDialect();
        PersistenceException ex = assertThrows(PersistenceException.class,
                () -> dialect.getDialectPageSql("SELECT a FROM t WHERE id = 1 FOR UPDATE", SECOND_PAGE));
        assertTrue(ex.getMessage().contains("ORA-02014"), "应说明 Oracle 的 ORA-02014 限制");
        assertTrue(ex.getMessage().contains("FOR UPDATE"));
    }

    @Test
    @DisplayName("Oracle：无锁子句时正常生成 ROWNUM 分页")
    void oraclePaginatesPlainQuery() {
        String sql = new OracleDialect()
                .getDialectPageSql("SELECT a FROM t ORDER BY a", SECOND_PAGE);
        assertTrue(sql.startsWith("SELECT * FROM (SELECT ROW_.*, ROWNUM ROWNUM_ FROM ("));
        assertTrue(sql.endsWith("ORDER BY a"), "排序应在最外层");
        assertFalse(sql.contains("FOR UPDATE"));
    }
}
