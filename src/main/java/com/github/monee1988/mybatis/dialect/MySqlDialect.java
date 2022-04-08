package com.github.monee1988.mybatis.dialect;

/**
 * Mysql分页方言
 * @author monee1988
 */
public class MySqlDialect extends BaseDialect {


	public MySqlDialect() {
	}

	/**
	 * @return 是否支持Mysql分页
	 */
	@Override
	public boolean supportLimit() {
		return true;
	}

	/**
	 * 获取分页SQL语句
	 * @param sql 要分页的sql语句
	 * @param offset 偏移量
	 * @param limit 分页容量
	 * @return 分页SQL语句
	 */
	@Override
	public String getLimitString(String sql, int offset, int limit) {
		return getLimitString(sql, offset, String.valueOf(offset), limit, String.valueOf(limit));
	}

	/**
	 * @param sql 要分页的sql语句
	 * @param offset 偏移量
	 * @param offsetPlaceholder 偏移量占位符
	 * @param limit 分页容量
	 * @param limitPlaceholder 分页容量占位符
	 * @return 分页SQL语句
	 */
	@Override
    public String getLimitString(String sql, int offset, String offsetPlaceholder, int limit, String limitPlaceholder) {
		if (offset > 0) {
			return sql + " limit " + offsetPlaceholder + "," + limitPlaceholder;
		} else {
			return sql + " limit " + limitPlaceholder;
		}
	}

}
