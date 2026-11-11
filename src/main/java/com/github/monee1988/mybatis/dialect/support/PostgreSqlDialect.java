package com.github.monee1988.mybatis.dialect.support;

import com.github.monee1988.mybatis.dialect.Dialect;
import com.github.monee1988.mybatis.util.SqlUtils;
import org.apache.ibatis.session.RowBounds;

/**
 * PostgreSQL 分页方言：{@code 原SQL LIMIT size OFFSET offset}。
 *
 * <p>H2、人大金仓 KingbaseES、OpenGauss 等兼容 LIMIT/OFFSET 语法的数据库亦可复用本方言。</p>
 *
 * <p>原 SQL 若带顶层锁子句（{@code FOR UPDATE} / {@code FOR SHARE} / {@code FOR NO KEY UPDATE} /
 * {@code FOR KEY SHARE}），{@code LIMIT/OFFSET} 会插入到锁子句之前——PostgreSQL 语法同样要求锁子句位于最后。</p>
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
        String limit = " LIMIT " + rowBounds.getLimit() + " OFFSET " + rowBounds.getOffset();
        int lockClause = SqlUtils.indexOfTopLevelLockClause(originalSql);
        if (lockClause > 0) {
            return originalSql.substring(0, lockClause).trim()
                    + limit + ' ' + originalSql.substring(lockClause).trim();
        }
        return originalSql + limit;
    }
}
