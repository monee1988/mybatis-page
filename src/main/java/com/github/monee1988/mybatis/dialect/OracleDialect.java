package com.github.monee1988.mybatis.dialect;

import org.apache.ibatis.session.RowBounds;

/**
 * ORACLE分页方言
 * @author monee1988
 */
public class OracleDialect implements Dialect {

	public OracleDialect() {
	}


	@Override
	public boolean supportPageable() {
		return true;
	}

	@Override
    public String getDialectPageSql(String originalSql, RowBounds rowBounds) {

		originalSql = originalSql.trim();
		StringBuffer pagingSelect = new StringBuffer(originalSql.length() + 100);
		
		pagingSelect.append("SELECT * FROM ( SELECT row_.*, rownum rownum_ from ( ");
		pagingSelect.append(originalSql);
		pagingSelect.append(" ) row_ ) WHERE rownum_ > ");
		pagingSelect.append(rowBounds.getOffset());
		pagingSelect.append(" AND rownum_ <= ");
		pagingSelect.append(rowBounds.getOffset() + rowBounds.getLimit());

		return pagingSelect.toString();
	}

}