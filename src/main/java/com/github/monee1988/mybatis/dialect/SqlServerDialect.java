package com.github.monee1988.mybatis.dialect;

import org.apache.ibatis.session.RowBounds;

/**
 * @author monee1988
 */
public class SqlServerDialect implements Dialect{

    @Override
    public boolean supportPageable() {
        return true;
    }

    @Override
    public String getDialectPageSql(String originalSql, RowBounds rowBounds) {

        int orderStartIndex = originalSql.replaceAll("(?i)ORDER\\s+BY", "ORDER BY").lastIndexOf("ORDER BY");
        String orderStr = "ORDER BY n";
        // 有排序，且是最外层的排序
        if (orderStartIndex != -1 && originalSql.lastIndexOf(")") < orderStartIndex) {
            orderStr = originalSql.substring(orderStartIndex);
        }
        String pageSql = originalSql.replaceFirst("(?i)SELECT", "SELECT * FROM (SELECT ROW_NUMBER() OVER(" + orderStr
                + ") AS rowNumber,* FROM ( SELECT TOP " + (rowBounds.getOffset() + rowBounds.getLimit()) + " n=0,");
        pageSql += ")t )tt WHERE rowNumber> " + rowBounds.getOffset();
        return pageSql;
    }
}
