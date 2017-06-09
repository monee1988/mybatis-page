package com.github.monee1988.mybatis;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

import org.apache.ibatis.executor.CachingExecutor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.MappedStatement.Builder;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ReflectionUtils;

import com.alibaba.fastjson.JSON;
import com.github.monee1988.mybatis.dialect.Dialect;
import com.github.monee1988.mybatis.entity.Page;

/**
 * mybatis 拦截器扩展
 * 
 * @author pangweixin
 *
 */
@Intercepts({ @Signature(type = Executor.class, method = "query", args = { MappedStatement.class, Object.class,
		RowBounds.class, ResultHandler.class }) })	
public class MybatisInterceptor  implements Interceptor {

	private static final Logger logger = LoggerFactory.getLogger(MybatisInterceptor.class);

	protected static final String PAGE = "page";

	protected static int MAPPED_STATEMENT_INDEX = 0;

	protected static int PARAMETER_INDEX = 1;

	protected static int ROWBOUNDS_INDEX = 2;

	protected static int RESULT_HANDLER_INDEX = 3;

	protected Dialect dialect;

	public Object intercept(Invocation invocation) throws Throwable {

		processMybatisIntercept(invocation);

		return invocation.proceed();
	}

	private void processMybatisIntercept(Invocation invocation) {

		Object[] queryArgs = invocation.getArgs();

		MappedStatement ms = (MappedStatement) queryArgs[MAPPED_STATEMENT_INDEX];
		Object parameter = queryArgs[PARAMETER_INDEX];

		Page<?> page = null;

		if (parameter == null) {
			logger.debug("普通的SQL查询");
			return;
		}
		

		page = convertParameter(page, parameter);

		if (dialect.supportsLimitOffset() && page != null) {
			logger.debug("分页查询==>>");
			
			BoundSql boundSql = ms.getBoundSql(parameter);
			String sql = boundSql.getSql().trim();

			final RowBounds rowBounds = (RowBounds) queryArgs[ROWBOUNDS_INDEX];
			int offset = rowBounds.getOffset();
			int limit = rowBounds.getLimit();
			offset = page.getOffset();
			limit = page.getPageSize();

			CachingExecutor executor = (CachingExecutor) invocation.getTarget();

			Transaction transaction = executor.getTransaction();
			try {
				Connection connection = transaction.getConnection();
				/**
				 * 查询总记录数
				 */
				this.setTotalRecord(page, ms, connection, parameter);
				
			} catch (SQLException e) {
				e.printStackTrace();
			}
			if (dialect.supportsLimitOffset()) {

				sql = dialect.getLimitString(sql, offset, limit);
				offset = RowBounds.NO_ROW_OFFSET;

			} else {

				sql = dialect.getLimitString(sql, 0, limit);

			}
			limit = RowBounds.NO_ROW_LIMIT;

			queryArgs[ROWBOUNDS_INDEX] = new RowBounds(offset, limit);

			BoundSql newBoundSql = copyFromBoundSql(ms, boundSql, sql);

			MappedStatement newMs = copyFromMappedStatement(ms, new BoundSqlSqlSource(newBoundSql));

			queryArgs[MAPPED_STATEMENT_INDEX] = newMs;
			

		}else{
			logger.debug("普通的SQL查询");
		}

	}

	private void setTotalRecord(Page<?> page, MappedStatement mappedStatement, Connection connection,
			Object parameterObject) {
		BoundSql boundSql = mappedStatement.getBoundSql(parameterObject);
		String sql = boundSql.getSql();
		String countSql = removeBreakingWhitespace(dialect.getCountSql(sql));
		List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
		BoundSql countBoundSql = new BoundSql(mappedStatement.getConfiguration(), countSql, parameterMappings,
				parameterObject);
		ParameterHandler parameterHandler = new DefaultParameterHandler(mappedStatement, parameterObject,
				countBoundSql);
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		parameterHandler.getParameterObject();
		logger.debug("Preparing: {} ", countSql.toString());
		logger.debug("Parameters: {} ", JSON.toJSONString(parameterObject));

		try {
			pstmt = connection.prepareStatement(countSql);
			parameterHandler.setParameters(pstmt);
			rs = pstmt.executeQuery();
			if (rs.next()) {
				int totalRecord = rs.getInt(1);
				logger.debug("Total :{}", totalRecord);
				page.setTotalCount(totalRecord);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				if (rs != null)
					rs.close();
				if (pstmt != null)
					pstmt.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}

	private String removeBreakingWhitespace(String countSql) {
		StringTokenizer whitespaceStripper = new StringTokenizer(countSql);
		StringBuilder builder = new StringBuilder();
		while (whitespaceStripper.hasMoreTokens()) {
			builder.append(whitespaceStripper.nextToken());
			builder.append(" ");
		}
		return builder.toString();
	}

	private BoundSql copyFromBoundSql(MappedStatement ms, BoundSql boundSql, String sql) {
		BoundSql newBoundSql = new BoundSql(ms.getConfiguration(), sql, boundSql.getParameterMappings(),
				boundSql.getParameterObject());
		for (ParameterMapping mapping : boundSql.getParameterMappings()) {
			String prop = mapping.getProperty();
			if (boundSql.hasAdditionalParameter(prop)) {
				newBoundSql.setAdditionalParameter(prop, boundSql.getAdditionalParameter(prop));
			}
		}
		return newBoundSql;
	}

	private MappedStatement copyFromMappedStatement(MappedStatement ms, SqlSource newSqlSource) {
		Builder builder = new MappedStatement.Builder(ms.getConfiguration(), ms.getId(), newSqlSource,ms.getSqlCommandType());
		builder.resource(ms.getResource());
		builder.fetchSize(ms.getFetchSize());
		builder.statementType(ms.getStatementType());
		builder.keyGenerator(ms.getKeyGenerator());
		// builder.keyProperty(ms.getKeyProperty());
		builder.timeout(ms.getTimeout());
		builder.parameterMap(ms.getParameterMap());
		builder.resultMaps(ms.getResultMaps());
		builder.cache(ms.getCache());
		MappedStatement newMs = builder.build();
		return newMs;
	}

	private Page<?> convertParameter(Page<?> page, Object parameter) {
		if (parameter instanceof Page<?>) {
			return (Page<?>) parameter;
		}
		if (parameter instanceof Map<?, ?>) {
			return ((Map<?, ?>) parameter).containsKey("page")?(Page<?>) ((Map<?, ?>) parameter).get(PAGE):null;
		}

		return currentGetFiled(parameter, PAGE);
	}

	private Page<?> currentGetFiled(Object object, String param) {
		Field pageField = ReflectionUtils.findField(object.getClass(), param);
		try {
			boolean accessible = pageField.isAccessible();
			pageField.setAccessible(Boolean.TRUE);
			Page<?> page = (Page<?>) pageField.get(object);
			pageField.setAccessible(accessible);
			if (page != null) {
				return page;
			}
		} catch (Exception e) {
			return null;
		}
		return null;
	}

	public Object plugin(Object target) {
		return Plugin.wrap(target, this);
	}

	public void setProperties(Properties properties) {
	}

	/**
	 * @param dialectClass
	 *            the dialectClass to set
	 */
	public void setDialectClass(String dialectClass) {
		try {
			dialect = (Dialect) Class.forName(dialectClass).newInstance();
		} catch (Exception e) {
			throw new RuntimeException("cannot create dialect instance by dialectClass:" + dialectClass, e);
		}
		logger.debug(this.getClass().getSimpleName()+ ".dialect=[{}]", dialect.getClass().getSimpleName());
	}

	public static class BoundSqlSqlSource implements SqlSource {
		BoundSql boundSql;

		public BoundSqlSqlSource(BoundSql boundSql) {
			this.boundSql = boundSql;
		}

		public BoundSql getBoundSql(Object parameterObject) {
			return boundSql;
		}
	}

}
