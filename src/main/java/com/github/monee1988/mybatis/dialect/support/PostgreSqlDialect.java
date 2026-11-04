package com.github.monee1988.mybatis.dialect.support;

import com.github.monee1988.mybatis.dialect.Dialect;
import org.apache.ibatis.session.RowBounds;

/**
 * PostgreSQL 分页方言：{@code 原SQL LIMIT size OFFSET offset}。
 *
 * <p>H2、人大金仓 KingbaseES、OpenGauss 等兼容 LIMIT/OFFSET 语法的数据库亦可复用本方言。</p>
 *
 * @author monee1988
 */
public class PostgreSqlDialect implements Dialect {

    @Override
    public boolean supportPageable() {
        return true;
    }

    @Override
    public String getDialectPageSql(String originalSql, RowBounds rowBounds) {
        StringBuilder sql = new StringBuilder(originalSql.length() + 32);
        sql.append(originalSql)
                .append(" LIMIT ")
                .append(rowBounds.getLimit())
                .append(" OFFSET ")
                .append(rowBounds.getOffset());
        return sql.toString();
    }
}
