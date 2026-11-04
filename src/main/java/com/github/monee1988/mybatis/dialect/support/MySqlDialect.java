package com.github.monee1988.mybatis.dialect.support;

import com.github.monee1988.mybatis.dialect.Dialect;
import org.apache.ibatis.session.RowBounds;

/**
 * MySQL 分页方言：{@code 原SQL LIMIT offset, size}。
 *
 * <p>offset / limit 均为 int，不存在 SQL 注入风险。</p>
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
        StringBuilder sql = new StringBuilder(originalSql.length() + 32);
        sql.append(originalSql)
                .append(" LIMIT ")
                .append(rowBounds.getOffset())
                .append(',')
                .append(rowBounds.getLimit());
        return sql.toString();
    }
}
