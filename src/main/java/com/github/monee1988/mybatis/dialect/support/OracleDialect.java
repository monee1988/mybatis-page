package com.github.monee1988.mybatis.dialect.support;

import com.github.monee1988.mybatis.dialect.Dialect;
import com.github.monee1988.mybatis.util.SqlUtils;
import org.apache.ibatis.session.RowBounds;

/**
 * Oracle 分页方言，采用 ROWNUM 三层包裹写法，兼容 Oracle 8i ~ 11g（12c+ 亦可）。
 *
 * <p>生成形如：</p>
 *
 * <pre>
 * SELECT * FROM (SELECT ROW_.*, ROWNUM ROWNUM_ FROM (原SQL) ROW_ WHERE ROWNUM &lt;= end)
 *          WHERE ROWNUM_ &gt; offset
 * </pre>
 *
 * <p>顶层 ORDER BY 会提取到最外层执行，保证 ROWNUM 分页下行序稳定，
 * 这是 Oracle ROWNUM 分页的标准写法。</p>
 *
 * @author monee1988
 */
public class OracleDialect implements Dialect {

    @Override
    public boolean supportPageable() {
        return true;
    }

    @Override
    public String getDialectPageSql(String originalSql, RowBounds rowBounds) {
        String orderBy = SqlUtils.extractTopLevelOrderBy(originalSql);
        String core = SqlUtils.stripTopLevelTailClauses(originalSql);

        long offset = rowBounds.getOffset();
        long end = offset + rowBounds.getLimit();

        StringBuilder sql = new StringBuilder(core.length() + 128);
        sql.append("SELECT * FROM (SELECT ROW_.*, ROWNUM ROWNUM_ FROM (")
                .append(core)
                .append(") ROW_ WHERE ROWNUM <= ")
                .append(end)
                .append(") WHERE ROWNUM_ > ")
                .append(offset);
        if (orderBy != null) {
            sql.append(' ').append(orderBy);
        }
        return sql.toString();
    }
}
