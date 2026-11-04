package com.github.monee1988.mybatis.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * count SQL 推导测试。重点覆盖 DISTINCT、GROUP BY、UNION、WITH、子查询等边界场景。
 */
class SqlUtilsTest {

    @Test
    @DisplayName("普通查询：直接改写 select 子句")
    void simpleSelect() {
        String count = SqlUtils.buildCountSql("SELECT code, name FROM country");
        assertEquals("SELECT COUNT(*) FROM country", count);
    }

    @Test
    @DisplayName("带 where + order by：order by 被剥离")
    void whereAndOrderBy() {
        String count = SqlUtils.buildCountSql("SELECT code FROM country WHERE code > 10 ORDER BY code ASC");
        assertEquals("SELECT COUNT(*) FROM country WHERE code > 10", count);
    }

    @Test
    @DisplayName("select 子句里有子查询：不会截到内层 from")
    void subqueryInSelectList() {
        String count = SqlUtils.buildCountSql(
                "SELECT (SELECT COUNT(*) FROM city c WHERE c.code = t.code) AS cnt, t.code FROM country t ORDER BY t.code");
        assertEquals("SELECT COUNT(*) FROM country t", count);
    }

    @Test
    @DisplayName("字符串字面量里的 from / order by 不影响解析")
    void literalsAreMasked() {
        String count = SqlUtils.buildCountSql(
                "SELECT code FROM country WHERE name = 'from x order by y' AND memo = 'it''s from'");
        assertEquals("SELECT COUNT(*) FROM country WHERE name = 'from x order by y' AND memo = 'it''s from'", count);
    }

    @Test
    @DisplayName("注释里的 order by 不影响解析")
    void commentsAreMasked() {
        String count = SqlUtils.buildCountSql(
                "SELECT code FROM country /* order by 1 */ -- order by code\n WHERE id = 1");
        assertTrue(count.startsWith("SELECT COUNT(*) FROM country"));
        assertTrue(count.contains("WHERE id = 1"));
    }

    @Test
    @DisplayName("DISTINCT 降级为包裹式，避免把重复行计入总数")
    void distinctFallsBackToWrap() {
        String count = SqlUtils.buildCountSql("SELECT DISTINCT code FROM country ORDER BY code");
        assertEquals("SELECT COUNT(*) FROM (SELECT DISTINCT code FROM country) MYBATIS_PAGE_COUNT", count);
    }

    @Test
    @DisplayName("GROUP BY 降级为包裹式")
    void groupByFallsBackToWrap() {
        String count = SqlUtils.buildCountSql(
                "SELECT province, COUNT(*) AS c FROM country GROUP BY province ORDER BY c DESC");
        assertEquals("SELECT COUNT(*) FROM (SELECT province, COUNT(*) AS c FROM country GROUP BY province) MYBATIS_PAGE_COUNT",
                count);
    }

    @Test
    @DisplayName("UNION 降级为包裹式")
    void unionFallsBackToWrap() {
        String count = SqlUtils.buildCountSql(
                "SELECT code FROM country UNION ALL SELECT code FROM city ORDER BY code");
        assertEquals("SELECT COUNT(*) FROM (SELECT code FROM country UNION ALL SELECT code FROM city) MYBATIS_PAGE_COUNT",
                count);
    }

    @Test
    @DisplayName("CTE（WITH）降级为包裹式")
    void withFallsBackToWrap() {
        String count = SqlUtils.buildCountSql(
                "WITH t AS (SELECT code FROM country) SELECT * FROM t");
        assertEquals("SELECT COUNT(*) FROM (WITH t AS (SELECT code FROM country) SELECT * FROM t) MYBATIS_PAGE_COUNT",
                count);
    }

    @Test
    @DisplayName("子查询里的 group by 不触发降级（count 只看外层行数）")
    void innerGroupByDoesNotWrap() {
        String count = SqlUtils.buildCountSql(
                "SELECT t.code FROM country t WHERE t.code IN (SELECT code FROM city GROUP BY code)");
        assertEquals("SELECT COUNT(*) FROM country t WHERE t.code IN (SELECT code FROM city GROUP BY code)", count);
    }

    @Test
    @DisplayName("FOR UPDATE 被剥离")
    void forUpdateStripped() {
        String count = SqlUtils.buildCountSql("SELECT code FROM country WHERE id = 1 FOR UPDATE");
        assertEquals("SELECT COUNT(*) FROM country WHERE id = 1", count);
    }

    @Test
    @DisplayName("结尾分号被剥离")
    void trailingSemicolon() {
        assertEquals("SELECT COUNT(*) FROM country", SqlUtils.buildCountSql("SELECT code FROM country ; "));
    }

    @Test
    @DisplayName("没有顶层 FROM 时抛异常")
    void noFromThrows() {
        assertThrows(IllegalArgumentException.class, () -> SqlUtils.buildCountSql("SELECT 1"));
    }

    @Test
    @DisplayName("占位符计数忽略字符串内的问号")
    void countPlaceholders() {
        assertEquals(2, SqlUtils.countPlaceholders("SELECT a FROM t WHERE a = ? AND b = ?"));
        assertEquals(0, SqlUtils.countPlaceholders("SELECT '?' FROM t WHERE b LIKE '%?%'"));
    }

    @Test
    @DisplayName("提取顶层 order by")
    void extractOrderBy() {
        assertEquals("ORDER BY a DESC",
                SqlUtils.extractTopLevelOrderBy("SELECT a FROM t WHERE x IN (SELECT a FROM y ORDER BY a) ORDER BY a DESC"));
        assertNull(SqlUtils.extractTopLevelOrderBy("SELECT a FROM t"));
    }

    @Test
    @DisplayName("别名大写：包裹式 count 使用固定别名")
    void wrapAlias() {
        assertEquals("SELECT COUNT(*) FROM (SELECT a FROM t) MYBATIS_PAGE_COUNT",
                SqlUtils.buildWrappedCountSql("SELECT a FROM t ORDER BY a"));
    }
}
