package com.github.monee1988.mybatis.dialect.support;

import com.github.monee1988.mybatis.dialect.Dialect;
import org.apache.ibatis.session.RowBounds;

import java.util.StringJoiner;

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
		StringJoiner pagingSelect = new StringJoiner("");

		pagingSelect.add("SELECT * FROM ( SELECT row_.*, rownum rownum_ FROM ( ");
		pagingSelect.add(originalSql);
		pagingSelect.add(" ) row_ ) WHERE rownum_ > ");
		pagingSelect.add(String.valueOf(rowBounds.getOffset()));
		pagingSelect.add(" AND rownum_ <= ");
		pagingSelect.add(String.valueOf(rowBounds.getOffset() + rowBounds.getLimit()));

		return pagingSelect.toString();
	}


}