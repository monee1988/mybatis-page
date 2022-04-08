package com.github.monee1988.mybatis.dialect;

/**
 * ORACLE分页方言
 * @author monee1988
 */
public class OracleDialect extends BaseDialect {

	public OracleDialect() {
	}

	/**
	 * 设置支持分页
	 * @return
	 */
	@Override
	public boolean supportLimit() {
		return true;
	}

	@Override
    public String getLimitString(String sql, int offset, int limit) {

		sql = sql.trim();
		boolean isForUpdate = false;
		if (sql.toLowerCase().endsWith(" for update")) {
			sql = sql.substring(0, sql.length() - 11);
			isForUpdate = true;
		}

		StringBuffer pagingSelect = new StringBuffer(sql.length() + 100);
		
		pagingSelect.append("select * from ( select row_.*, rownum rownum_ from ( ");
		
		pagingSelect.append(sql);
		
		pagingSelect.append(" ) row_ ) where rownum_ > "+offset+" and rownum_ <= "+(offset + limit));

		if (isForUpdate) {
			pagingSelect.append(" for update");
		}
		
		return pagingSelect.toString();
	}

	@Override
	public String getLimitString(String sql, int offset, String offsetPlaceholder, int limit, String limitPlaceholder) {
		return null;
	}
}