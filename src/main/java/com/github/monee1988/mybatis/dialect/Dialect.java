package com.github.monee1988.mybatis.dialect;

import org.apache.ibatis.session.RowBounds;

/**
 * 定义分页插件方言基础类
 * @author monee1988
 */
public interface Dialect {

	/**
	 * 让子类方言实现
	 * @return 是否支持分页 默认不支持
	 */
	public boolean supportPageable();


	/**
	 *
	 * 获取分页SQL语句
	 * @param originalSql 要分页的sql语句
	 * @param rowBounds 偏移量
	 * @return 分页SQL语句
	 */
	public String getDialectPageSql(String originalSql, RowBounds rowBounds);

}