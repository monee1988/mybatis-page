package com.github.monee1988.mybatis.dialect;

/**
 * 定义分页插件方言基础类
 * @author monee1988
 */
public abstract class BaseDialect {

	/**
	 * 让子类方言实现
	 * @return 是否支持分页 默认不支持
	 */
	public boolean supportLimit(){
		return false;
	}

	/**
	 * @return 是否支持分页
	 */
	public boolean supportLimitOffset() {
		return supportLimit();
	}

	/**
	 *
	 * 获取分页SQL语句
	 * @param sql 要分页的sql语句
	 * @param offset 偏移量
	 * @param limit 分页容量
	 * @return 分页SQL语句
	 */
	public String getLimitString(String sql, int offset, int limit) {
		return getLimitString(sql, offset, Integer.toString(offset), limit, Integer.toString(limit));
	}

	/**
	 * 获取分页SQL语句
	 * @param sql 要分页的sql语句
	 * @param offset 偏移量
	 * @param offsetPlaceholder 偏移量占位符
	 * @param limit 分页容量
	 * @param limitPlaceholder 分页容量占位符
	 * @return 分页SQL语句
	 */
	public abstract String getLimitString(String sql, int offset, String offsetPlaceholder, int limit, String limitPlaceholder);

	/**
	 * @param sql 需要查询总量的SQL
	 * @return 总数据量SQL
	 */
	public String getCountSql(String sql) {

		if(sql != null){
			return "select count(1) from (" + sql + ") AS total";
		}else{
			throw new UnsupportedOperationException("countSql does not exist");
		}
	}
}