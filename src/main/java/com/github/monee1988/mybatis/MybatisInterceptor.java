package com.github.monee1988.mybatis;

import com.github.monee1988.mybatis.dialect.Dialect;
import com.github.monee1988.mybatis.entity.Page;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.DefaultObjectFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.DefaultObjectWrapperFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *  mybatis 拦截器扩展
 *  @author monee1988
 */
@Intercepts({
	@Signature(method = "prepare", type = StatementHandler.class, args = { Connection.class ,Integer.class}) }
)
public class MybatisInterceptor implements Interceptor {

	private static final Logger logger = LoggerFactory.getLogger(MybatisInterceptor.class);
	private static final ObjectFactory DEFAULT_OBJECT_FACTORY = new DefaultObjectFactory();
	private static final ObjectWrapperFactory DEFAULT_OBJECT_WRAPPER_FACTORY = new DefaultObjectWrapperFactory();
	private static final ReflectorFactory DEFAULT_REFLECTOR_FACTORY = new DefaultReflectorFactory();
	private static final String PAGE = "page";
	private static final Pattern PATTERN = Pattern.compile("ORDER\\s*by[\\w|\\W|\\s|\\S]*", Pattern.CASE_INSENSITIVE);

	private Dialect dialect;

	private String dialectClassName;

	/**
	 * 设置分页方言
	 * @param dialectClassName 方言类名
	 */
	public MybatisInterceptor setDialect(String dialectClassName) {
		setDialectClassName(dialectClassName);
		return this;
	}

	/**
	 * 设置分页方言
	 * @param dialectClassName 方言类名
	 */
	public void setDialectClassName(String dialectClassName) {
		try {
			this.dialectClassName = dialectClassName;
			dialect = (Dialect) Class.forName(dialectClassName).getDeclaredConstructor().newInstance();
		} catch (Exception e) {
			throw new RuntimeException("cannot create dialect instance by dialectClass:" + dialectClassName, e);
		}
		logger.debug(this.getClass().getSimpleName()+ ".dialect=[{}]", dialect.getClass().getSimpleName());

	}

	@Override
	public Object intercept(Invocation invocation) throws Throwable {

		if (invocation.getTarget() instanceof StatementHandler) {
			processMybatisIntercept(invocation);
		}

		return invocation.proceed();
	}
	private void processMybatisIntercept(Invocation invocation) {

		StatementHandler statementHandler = (StatementHandler) invocation.getTarget();

		MetaObject metaStatementHandler  = MetaObject.forObject(statementHandler, DEFAULT_OBJECT_FACTORY, DEFAULT_OBJECT_WRAPPER_FACTORY, DEFAULT_REFLECTOR_FACTORY);

		// 分离代理对象链(由于目标类可能被多个拦截器拦截，从而形成多次代理，通过下面的两次循环可以分离出最原始的的目标类)
		while (metaStatementHandler.hasGetter("h")) {
			Object object = metaStatementHandler.getValue("h");
			metaStatementHandler = MetaObject.forObject(object, DEFAULT_OBJECT_FACTORY, DEFAULT_OBJECT_WRAPPER_FACTORY, DEFAULT_REFLECTOR_FACTORY);
		}
		// 分离最后一个代理对象的目标类
		while (metaStatementHandler.hasGetter("target")) {
			Object object = metaStatementHandler.getValue("target");
			metaStatementHandler = MetaObject.forObject(object, DEFAULT_OBJECT_FACTORY, DEFAULT_OBJECT_WRAPPER_FACTORY, DEFAULT_REFLECTOR_FACTORY);
		}

		Object parameter = statementHandler.getParameterHandler().getParameterObject();
		Page<?> page = convertParameter(parameter);

		if (dialect !=null && dialect.supportPageable() && page != null) {

			// 将mybatis的内存分页，调整为物理分页
			BoundSql boundSql = (BoundSql) metaStatementHandler.getValue("delegate.boundSql");
			String originalSql = boundSql.getSql().trim();;
			logger.debug("分页查询==>>");
			//查询总记录数
			this.setTotalRecord(page, metaStatementHandler, parameter);
			// 重写sql
			String pageSql =dialect.getDialectPageSql(originalSql,new RowBounds(page.getOffset(),page.getPageSize()));

			metaStatementHandler.setValue("delegate.boundSql.sql", pageSql);
			// 采用物理分页后，就不需要mybatis的内存分页了，所以重置下面的两个参数
			metaStatementHandler.setValue("delegate.rowBounds", RowBounds.DEFAULT);
		}else{
			logger.debug("普通查询==>>");
		}
	}

	private String getCountSql(String sql) {

		return " SELECT count(*) "+ removeSelect(removeOrders(sql));
	}

	private String removeOrders(String sql) {

		Matcher m = PATTERN.matcher(sql);
		StringBuffer sb = new StringBuffer();
		while (m.find()) {
			m.appendReplacement(sb, "");
		}
		m.appendTail(sb);

		return sb.toString();
	}

	private void setTotalRecord(Page<?> page, MetaObject metaStatementHandler,Object parameterObject) {

		Configuration configuration = (Configuration) metaStatementHandler.getValue("delegate.configuration");
		MappedStatement mappedStatement = (MappedStatement) metaStatementHandler.getValue("delegate.mappedStatement");
		BoundSql boundSql = mappedStatement.getBoundSql(parameterObject);
		String sql = boundSql.getSql();
		String countSql = removeBreakingWhitespace(getCountSql(sql));
		List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
		BoundSql countBoundSql = new BoundSql(configuration, countSql, parameterMappings,parameterObject);
		ParameterHandler parameterHandler = new DefaultParameterHandler(mappedStatement, parameterObject,countBoundSql);
		PreparedStatement preparedStatement = null;
		ResultSet resultSet = null;
		logger.debug("Preparing: {} ", countSql);
		Connection connection = null;

		try {
			connection = configuration.getEnvironment().getDataSource().getConnection();
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
				if(connection != null){
					connection.close();
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}

	// 去除sql语句中select子句
	private static String removeSelect(String hql) {
		int beginPos = hql.toLowerCase().indexOf("from");
		if (beginPos < 0) {
			throw new IllegalArgumentException(" hql : " + hql + " must has a keyword 'from'");
		}
		return hql.substring(beginPos);
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

	private Page<?> convertParameter(Object parameter) {

		if (parameter instanceof Page<?>) {
			return (Page<?>) parameter;
		}
		if (parameter instanceof Map<?, ?>) {
			return ((Map<?, ?>) parameter).containsKey(PAGE)?(Page<?>) ((Map<?, ?>) parameter).get(PAGE):null;
		}
		Field pageField = ReflectionUtils.findField(parameter.getClass(), PAGE);

		if(ObjectUtils.isEmpty(pageField)){
			return null;
		}

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

		if(!properties.isEmpty()){
			for (String key : properties.stringPropertyNames()) {
				if(("dialect").equals(key)){
					setDialectClassName(properties.getProperty("dialect"));
					return;
				}
				if(("dialectClassName").equals(key)){
					setDialectClassName(properties.getProperty("dialectClassName"));
					return;
				}
			}
		}

	}

}

