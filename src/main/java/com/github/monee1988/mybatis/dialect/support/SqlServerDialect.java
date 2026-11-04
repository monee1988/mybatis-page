package com.github.monee1988.mybatis.dialect.support;

import com.github.monee1988.mybatis.dialect.Dialect;
import com.github.monee1988.mybatis.util.SqlUtils;
import org.apache.ibatis.session.RowBounds;

/**
 * SQL Server 分页方言，采用 ROW_NUMBER 包裹写法，适用于 SQL Server 2005 及以上。
 *
 * <p>生成形如：</p>
 *
 * <pre>
 * SELECT * FROM (
 *   SELECT ROW_NUMBER() OVER (ORDER BY ...) AS MYBATIS_PAGE_RN_, t_.* FROM (原SQL) t_
 * ) tt_ WHERE tt_.MYBATIS_PAGE_RN_ &gt; offset AND tt_.MYBATIS_PAGE_RN_ &lt;= offset+limit
 * </pre>
 *
 * <p><b>注意</b>：结果集会多出一列 {@code MYBATIS_PAGE_RN_}。
 * 使用 resultType=实体类时 MyBatis 按属性名映射，会自动忽略该列；
 * 使用 resultType=map 时会多一个 key，如需避免请在 SQL 中显式列出字段并改用
 * resultType 映射。</p>
 *
 * @author monee1988
 */
public class SqlServerDialect implements Dialect {

    private static final String RN_COLUMN = "MYBATIS_PAGE_RN_";

    @Override
    public boolean supportPageable() {
        return true;
    }

    @Override
    public String getDialectPageSql(String originalSql, RowBounds rowBounds) {
        String orderBy = SqlUtils.extractTopLevelOrderBy(originalSql);
        String core = SqlUtils.stripTopLevelTailClauses(originalSql);
        // ROW_NUMBER 必须带 OVER 子句；原 SQL 没有排序时给出稳定的空排序
        String over = orderBy != null ? orderBy : "ORDER BY (SELECT NULL)";

        long offset = rowBounds.getOffset();
        long end = offset + rowBounds.getLimit();

        StringBuilder sql = new StringBuilder(core.length() + 160);
        sql.append("SELECT * FROM (SELECT ROW_NUMBER() OVER (")
                .append(over)
                .append(") AS ")
                .append(RN_COLUMN)
                .append(", t_.* FROM (")
                .append(core)
                .append(") t_) tt_ WHERE tt_.")
                .append(RN_COLUMN)
                .append(" > ")
                .append(offset)
                .append(" AND tt_.")
                .append(RN_COLUMN)
                .append(" <= ")
                .append(end);
        return sql.toString();
    }
}
