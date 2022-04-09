package com.github.monee1988.mybatis;

import com.github.monee1988.mybatis.dialect.BaseDialect;
import com.github.monee1988.mybatis.entity.Page;
import org.apache.ibatis.executor.*;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.MappedStatement.Builder;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

/**
 *  mybatis 拦截器扩展
 *  @author monee1988
 */
@Intercepts({ @Signature(type = Executor.class, method = "query", args = { MappedStatement.class, Object.class,
		RowBounds.class, ResultHandler.class }) })	
public class MybatisInterceptor  implements Interceptor {

	private static final Logger logger = LoggerFactory.getLogger(MybatisInterceptor.class);

	protected static final String PAGE = "page";

	protected static int MAPPED_STATEMENT_INDEX = 0;

	protected static int PARAMETER_INDEX = 1;

	protected static int ROW_BOUNDS_INDEX = 2;

	private BaseDialect dialect;

	private String dialectClassName;

	/**
	 * 设置分页方言
	 * @param dialectClassName 方言类名
	 */
	public void setDialectClassName(String dialectClassName) {
		try {
			this.dialectClassName = dialectClassName;
			dialect = (BaseDialect) Class.forName(dialectClassName).getDeclaredConstructor().newInstance();
		} catch (Exception e) {
			throw new RuntimeException("cannot create dialect instance by dialectClass:" + dialectClassName, e);
		}
		logger.debug(this.getClass().getSimpleName()+ ".dialect=[{}]", dialect.getClass().getSimpleName());

	}

	@Override
	public Object intercept(Invocation invocation) throws Throwable {

		processMybatisIntercept(invocation);

		return invocation.proceed();
	}

	private void processMybatisIntercept(Invocation invocation) {

		Object[] queryArgs = invocation.getArgs();

		MappedStatement ms = (MappedStatement) queryArgs[MAPPED_STATEMENT_INDEX];
		Object parameter = queryArgs[PARAMETER_INDEX];


		if (parameter == null) {
			logger.debug("普通的SQL查询");
			return;
		}
		Page<?> page = convertParameter(parameter);

		if (dialect !=null && dialect.supportLimitOffset() && page != null) {
			logger.debug("分页查询==>>");
			
			BoundSql boundSql = ms.getBoundSql(parameter);
			String sql = boundSql.getSql().trim();
			int offset = page.getOffset();
			int limit = page.getPageSize();
			Object executorObject = invocation.getTarget();
			Executor executor;
			if(executorObject instanceof CachingExecutor){
				executor = (CachingExecutor) invocation.getTarget();
			}else if(executorObject instanceof ReuseExecutor){
				executor = (ReuseExecutor) invocation.getTarget();
			}else if(executorObject instanceof BatchExecutor){
				executor = (BatchExecutor) invocation.getTarget();
			}else{
				executor = (SimpleExecutor) invocation.getTarget();
			}

			logger.debug("Executor impl by  "+executorObject.getClass().getSimpleName());

			Transaction transaction = executor.getTransaction();

			try {
				Connection connection = transaction.getConnection();
				//查询总记录数
				this.setTotalRecord(page, ms, connection, parameter);
				
			} catch (SQLException e) {
				e.printStackTrace();
			}
			sql = dialect.getLimitString(sql, offset, limit);
			offset = RowBounds.NO_ROW_OFFSET;
			limit = RowBounds.NO_ROW_LIMIT;

			queryArgs[ROW_BOUNDS_INDEX] = new RowBounds(offset, limit);

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
		PreparedStatement preparedStatement = null;
		ResultSet resultSet = null;
		parameterHandler.getParameterObject();
		logger.debug("Preparing: {} ", countSql);

		try {
			preparedStatement = connection.prepareStatement(countSql);
			parameterHandler.setParameters(preparedStatement);
			resultSet = preparedStatement.executeQuery();
			if (resultSet.next()) {
				int totalRecord = resultSet.getInt(1);
				logger.debug("Total :{}", totalRecord);
				page.setTotalCount(totalRecord);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				if(resultSet != null){
					resultSet.close();
				}
				if(preparedStatement != null){
					preparedStatement.close();
				}
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
		builder.timeout(ms.getTimeout());
		builder.parameterMap(ms.getParameterMap());
		builder.resultMaps(ms.getResultMaps());
		builder.cache(ms.getCache());
		return builder.build();
	}

	private Page<?> convertParameter(Object parameter) {

		if (parameter instanceof Page<?>) {
			return (Page<?>) parameter;
		}
		if (parameter instanceof Map<?, ?>) {
			return ((Map<?, ?>) parameter).containsKey(PAGE)?(Page<?>) ((Map<?, ?>) parameter).get(PAGE):null;
		}
		Field pageField = ReflectionUtils.findField(parameter.getClass(), PAGE);
		try {
			boolean accessible = pageField.isAccessible();
			pageField.setAccessible(Boolean.TRUE);
			Page<?> page = (Page<?>) pageField.get(parameter);
			pageField.setAccessible(accessible);
			return page;
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public Object plugin(Object target) {
		return Plugin.wrap(target, this);
	}



	@Override
	public void setProperties(Properties properties) {
	}

	public static class BoundSqlSqlSource implements SqlSource {
		BoundSql boundSql;

		public BoundSqlSqlSource(BoundSql boundSql) {
			this.boundSql = boundSql;
		}

		@Override
		public BoundSql getBoundSql(Object parameterObject) {
			return boundSql;
		}
	}

}
