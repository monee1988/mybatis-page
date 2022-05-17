package com.github.monee1988.mybatis.dialect.support;

import com.github.monee1988.mybatis.dialect.Dialect;
import org.apache.ibatis.session.RowBounds;

/**
 * Mysql分页方言
 * @author monee1988
 */
public class MySqlDialect implements Dialect {


	public MySqlDialect() {
	}

	@Override
	public boolean supportPageable() {
		return true;
	}

	@Override
	public String getDialectPageSql(String originalSql, RowBounds rowBounds) {

		return originalSql + " LIMIT " + rowBounds.getOffset() + "," + rowBounds.getLimit();
	}
}
