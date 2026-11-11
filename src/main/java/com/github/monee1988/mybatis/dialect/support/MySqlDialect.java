package com.github.monee1988.mybatis.dialect.support;

import com.github.monee1988.mybatis.dialect.Dialect;
import com.github.monee1988.mybatis.util.SqlUtils;
import org.apache.ibatis.session.RowBounds;

/**
 * MySQL 分页方言：{@code 原SQL LIMIT offset, size}。
 *
 * <p>offset / limit 均为 int，不存在 SQL 注入风险。</p>
 *
 * <p>原 SQL 若带顶层 {@code FOR UPDATE}，{@code LIMIT} 会插入到锁子句之前——
 * MySQL 语法要求分页子句在 {@code FOR UPDATE} 前面，直接追加到末尾会报语法错误。</p>
 *
 * @author monee1988
 */
public class MySqlDialect implements Dialect {

    @Override
    public boolean supportPageable() {
        return true;
    }

    @Override
    public String getDialectPageSql(String originalSql, RowBounds rowBounds) {
        String limit = " LIMIT " + rowBounds.getOffset() + ',' + rowBounds.getLimit();
        int forUpdate = SqlUtils.indexOfTopLevelForUpdate(originalSql);
        if (forUpdate > 0) {
            return originalSql.substring(0, forUpdate).trim()
                    + limit + ' ' + originalSql.substring(forUpdate).trim();
        }
        return originalSql + limit;
    }
}
