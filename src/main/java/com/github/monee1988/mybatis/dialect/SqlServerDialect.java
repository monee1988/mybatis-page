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
        String pageSql = originalSql.replaceFirst("(?i)select", "select * from (select row_number() over(" + orderStr
                + ") as rownumber,* from ( select top " + (rowBounds.getOffset() + rowBounds.getLimit()) + " n=0,");
        pageSql += ")t )tt where rownumber> " + rowBounds.getOffset();
        return pageSql;
    }
}
