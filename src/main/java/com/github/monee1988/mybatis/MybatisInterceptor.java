package com.github.monee1988.mybatis;

import com.github.monee1988.mybatis.dialect.Dialect;
import com.github.monee1988.mybatis.entity.Page;
import com.github.monee1988.mybatis.util.SqlUtils;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.RowBounds;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * MyBatis 物理分页拦截器。
 *
 * <p>挂在 {@link StatementHandler#prepare(Connection, Integer)} 上，在 SQL 预编译前改写 SQL：
 * 先执行 count 语句统计总数并回填 {@link Page#setTotalCount(long)}，再按方言生成分页 SQL，
 * 最后计算总页数与页码条区间（等效于调用 {@link Page#end()}），业务层无需手动处理。</p>
 *
 * <p>count 查询复用 {@code prepare} 传入的当前数据库连接，与业务查询处于同一事务中；
 * count SQL 由 {@link SqlUtils} 从原 SQL 推导，遇到 DISTINCT、GROUP BY、UNION、WITH 等
 * 无法安全改写的语句时自动降级为包裹式子查询。统计失败时包装为
 * {@link org.apache.ibatis.exceptions.PersistenceException} 抛出；
 * 确实无法统计总数的语句（存储过程、无 FROM 子句等），可将 {@code countEnabled}
 * 设为 {@code false} 跳过统计。</p>
 *
 * <h2>分页参数的解析</h2>
 * <p>按顺序尝试三种方式，都找不到则视为普通查询、不做分页：</p>
 * <ol>
 *   <li>参数对象本身就是 {@link Page}</li>
 *   <li>参数是 Map 且含有 key {@code "page"}</li>
 *   <li>参数是实体 Bean 且含有名为 {@code page} 的属性</li>
 * </ol>
 *
 * @author monee1988
 */
@Intercepts({
        @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
})
public class MybatisInterceptor implements Interceptor {


    /**
     * 日志走 MyBatis 自带的 {@link org.apache.ibatis.logging.LogFactory}：
     * 自动探测 slf4j / commons-logging / log4j2 / log4j / JUL，全部缺失时静默，
     * 且与 MyBatis 全局 logImpl 配置统一，插件自身不绑定任何日志实现。
     */
    private static final Log log = LogFactory.getLog(MybatisInterceptor.class);

    /**
     * 日志与异常信息的统一前缀，便于用户在日志中检索本插件的输出。
     * 声明为 public：starter 等周边模块直接引用，保证全家族日志前缀一致。
     */
    public static final String LOG_PREFIX = "mybatis-page: ";

    /** 约定的分页参数名 */
    private static final String PAGE = "page";

    /** 缓存"某个类是否有 page 属性"，避免每次查询都做反射 */
    private static final ConcurrentMap<Class<?>, Boolean> PAGE_PROPERTY_CACHE = new ConcurrentHashMap<>();

    /**
     * 已提示过"参数中没有 page"的参数类型。
     *
     * <p>只用于 debug 提示去重：STDOUT_LOGGING 等实现的 isDebugEnabled() 恒为 true，
     * 若按调用次数打印，一页 N 条记录的嵌套查询就会刷 N 行噪音。
     * 同一类型只提示一次，既保留"忘了给查询 Bean 挂 page"的诊断线索，又不刷屏。</p>
     */
    private static final ConcurrentMap<Class<?>, Boolean> MISSING_PAGE_REPORTED = new ConcurrentHashMap<>();

    private Dialect dialect;

    private String dialectClassName;

    /** 是否执行 count 查询。关闭后 totalCount 保持 0，仅做 SQL 分页 */
    private boolean countEnabled = true;

    public MybatisInterceptor() {
    }

    public MybatisInterceptor(Dialect dialect) {
        this.dialect = dialect;
    }

    // ------------------------------------------------------------------
    // 配置入口
    // ------------------------------------------------------------------

    /** 链式设置方言（按类名） */
    public MybatisInterceptor setDialect(String dialectClassName) {
        setDialectClassName(dialectClassName);
        return this;
    }

    /** 直接注入方言实例 */
    public MybatisInterceptor setDialect(Dialect dialect) {
        this.dialect = dialect;
        return this;
    }

    /** 按类名反射创建方言实例 */
    public void setDialectClassName(String dialectClassName) {
        if (dialectClassName == null || dialectClassName.trim().isEmpty()) {
            throw new IllegalArgumentException(LOG_PREFIX + "dialectClassName 不能为空");
        }
        try {
            this.dialectClassName = dialectClassName.trim();
            Class<?> clazz = Class.forName(this.dialectClassName);
            if (!Dialect.class.isAssignableFrom(clazz)) {
                throw new IllegalArgumentException(
                        LOG_PREFIX + this.dialectClassName + " 未实现 " + Dialect.class.getName());
            }
            this.dialect = (Dialect) clazz.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(
                    LOG_PREFIX + "无法实例化方言类 " + dialectClassName + "，请确认存在无参构造器", e);
        }
        log.debug(LOG_PREFIX + "使用方言 " + this.dialect.getClass().getSimpleName());
    }

    public String getDialectClassName() {
        return dialectClassName;
    }

    public boolean isCountEnabled() {
        return countEnabled;
    }

    public void setCountEnabled(boolean countEnabled) {
        this.countEnabled = countEnabled;
    }

    public Dialect getDialect() {
        return dialect;
    }

    // ------------------------------------------------------------------
    // 拦截
    // ------------------------------------------------------------------

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
        Connection connection = resolveConnection(invocation);

        if (dialect != null && dialect.supportPageable()) {
            Object parameterObject = statementHandler.getParameterHandler().getParameterObject();
            Page<?> page = resolvePage(parameterObject);
            if (page != null) {
                processPage(statementHandler, connection, page);
            } else {
                hintMissingPage(parameterObject);
            }
        }
        return invocation.proceed();
    }

    /**
     * 参数里没有 page 时给一条 debug 提示，但必须避免刷屏。
     *
     * <p>两道闸：</p>
     * <ol>
     *   <li>只有"可能承载 page"的参数才提示——{@link Map}（key={@code page} 约定）
     *       或用户实体 Bean（page 属性约定）。{@code Long}/{@code String} 等简单类型、
     *       集合、数组、JDK 类型根本不可能分页，静默跳过；</li>
     *   <li>同一参数类型只提示一次。STDOUT_LOGGING 的 isDebugEnabled() 恒为 true，
     *       不去重的话嵌套查询会按行数刷屏。</li>
     * </ol>
     */
    private void hintMissingPage(Object parameterObject) {
        if (parameterObject == null || !couldCarryPage(parameterObject.getClass())) {
            return;
        }
        if (MISSING_PAGE_REPORTED.putIfAbsent(parameterObject.getClass(), Boolean.TRUE) == null) {
            log.debug(LOG_PREFIX + "参数 " + parameterObject.getClass().getName()
                    + " 中未找到 " + PAGE + " 对象，按普通查询执行（同类参数仅提示一次）");
        }
    }

    /**
     * 该类型的参数是否"有可能"承载分页对象。
     *
     * <p>{@link Map} 返回 true（支持 key={@code page} 的约定）；
     * 简单类型、集合、数组、枚举以及 {@code java.*} / {@code javax.*} 下的类型返回 false。</p>
     */
    private static boolean couldCarryPage(Class<?> clazz) {
        if (clazz.isPrimitive() || clazz.isArray() || clazz.isEnum()) {
            return false;
        }
        if (java.util.Map.class.isAssignableFrom(clazz)) {
            return true;
        }
        String name = clazz.getName();
        return !name.startsWith("java.") && !name.startsWith("javax.");
    }

    private Connection resolveConnection(Invocation invocation) {
        Object[] args = invocation.getArgs();
        if (args != null && args.length > 0 && args[0] instanceof Connection) {
            return (Connection) args[0];
        }
        return null;
    }

    /**
     * 改写 SQL：查总数 -> 分页 -> 重置 RowBounds。
     */
    private void processPage(StatementHandler statementHandler, Connection connection, Page<?> page) {
        MetaObject meta = unwrap(statementHandler);
        if (!meta.hasGetter("delegate.boundSql")) {
            log.warn(LOG_PREFIX + "当前 StatementHandler 结构不支持分页（缺少 delegate.boundSql），跳过。类型="
                    + meta.getOriginalObject().getClass().getName());
            return;
        }

        BoundSql boundSql = (BoundSql) meta.getValue("delegate.boundSql");
        Object parameterObject = statementHandler.getParameterHandler().getParameterObject();
        String originalSql = boundSql.getSql();

        if (page.getPageSize() <= 0) {
            page.setPageSize(Page.DEFAULT_PAGESIZE);
        }

        // 1. 统计总数
        if (countEnabled) {
            if (connection == null) {
                throw new IllegalStateException(
                        LOG_PREFIX + "无法获取当前数据库连接，count 查询无法执行。可将 countEnabled 设为 false 跳过统计。");
            }
            page.setTotalCount(queryTotal(connection, meta, boundSql, parameterObject));
        }

        // 2. 校正页码并计算总页数 / 页码区间
        page.end();

        // 3. 改写 SQL
        RowBounds rowBounds = new RowBounds(page.getOffset(), page.getPageSize());
        String pageSql = dialect.getDialectPageSql(originalSql, rowBounds);
        meta.setValue("delegate.boundSql.sql", pageSql);

        // 4. 重置内存分页，避免物理分页之上再叠一层
        resetRowBounds(meta);

        if (log.isDebugEnabled()) {
            log.debug(LOG_PREFIX + "分页 pageNo=" + page.getPageNo()
                    + ", pageSize=" + page.getPageSize()
                    + ", total=" + page.getTotalCount()
                    + System.lineSeparator() + "  -> " + pageSql);
        }
    }

    private void resetRowBounds(MetaObject meta) {
        try {
            if (meta.hasSetter("delegate.rowBounds")) {
                meta.setValue("delegate.rowBounds", RowBounds.DEFAULT);
            }
        } catch (Exception e) {
            // RoutingStatementHandler#rowBounds 为 final，个别 JDK 版本下反射写入会失败。
            // 本插件的 Page 方案不依赖 RowBounds 传参，失败也不影响分页结果。
            log.debug(LOG_PREFIX + "重置 rowBounds 失败（可忽略）: " + e.toString());
        }
    }

    /**
     * 执行 count 查询，返回总记录数。
     */
    private long queryTotal(Connection connection, MetaObject meta, BoundSql boundSql, Object parameterObject) {
        Configuration configuration = (Configuration) meta.getValue("delegate.configuration");
        MappedStatement mappedStatement = (MappedStatement) meta.getValue("delegate.mappedStatement");
        String originalSql = boundSql.getSql();
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();

        String countSql;
        try {
            countSql = SqlUtils.buildCountSql(originalSql);
            // 若原 SQL 的 select 子句里含占位符（被截掉了），占位符数量会对不上，降级为包裹式
            if (SqlUtils.countPlaceholders(countSql) != parameterMappings.size()) {
                countSql = SqlUtils.buildWrappedCountSql(originalSql);
            }
        } catch (IllegalArgumentException e) {
            // 无顶层 FROM 的语句（存储过程、SELECT 1 等）无法推导 count SQL，
            // 统一包装为 PersistenceException，与「统计失败」的异常契约保持一致
            throw new org.apache.ibatis.exceptions.PersistenceException(
                    LOG_PREFIX + "无法推导 count SQL，可设置 countEnabled=false 跳过统计。原 SQL: " + originalSql, e);
        }

        BoundSql countBoundSql = new BoundSql(configuration, countSql, parameterMappings, parameterObject);
        // foreach / _parameter 等动态参数必须一并带过去，否则参数绑定会失败
        for (ParameterMapping mapping : parameterMappings) {
            String property = mapping.getProperty();
            if (boundSql.hasAdditionalParameter(property)) {
                countBoundSql.setAdditionalParameter(property, boundSql.getAdditionalParameter(property));
            }
        }

        ParameterHandler parameterHandler =
                new DefaultParameterHandler(mappedStatement, parameterObject, countBoundSql);

        if (log.isDebugEnabled()) {
            log.debug(LOG_PREFIX + "count SQL: " + countSql);
        }

        try (PreparedStatement ps = connection.prepareStatement(countSql)) {
            parameterHandler.setParameters(ps);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new org.apache.ibatis.exceptions.PersistenceException(
                    LOG_PREFIX + "执行 count SQL 失败，可设置 countEnabled=false 跳过统计。count SQL: " + countSql, e);
        }
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /**
     * 剥离 MyBatis 的多层插件代理，拿到最原始的 StatementHandler 对应的 MetaObject。
     */
    private MetaObject unwrap(Object target) {
        MetaObject meta = SystemMetaObject.forObject(target);
        while (meta.hasGetter("h")) {
            Object value = meta.getValue("h");
            if (value == null) {
                break;
            }
            meta = SystemMetaObject.forObject(value);
        }
        while (meta.hasGetter("target")) {
            Object value = meta.getValue("target");
            if (value == null) {
                break;
            }
            meta = SystemMetaObject.forObject(value);
        }
        return meta;
    }

    /**
     * 从参数对象中解析分页对象。
     */
    private Page<?> resolvePage(Object parameterObject) {
        if (parameterObject == null) {
            return null;
        }
        if (parameterObject instanceof Page) {
            return (Page<?>) parameterObject;
        }

        // 简单类型/集合/数组等不可能是分页载体，直接返回，避免无谓的反射开销
        if (!couldCarryPage(parameterObject.getClass())) {
            return null;
        }

        MetaObject meta = SystemMetaObject.forObject(parameterObject);
        if (meta.getOriginalObject() instanceof java.util.Map) {
            // MyBatis 的 MapperMethod$ParamMap 对不存在的 key 会抛 BindingException，
            // 必须先 containsKey 判断：多 @Param 且没有 page 参数的语句（如批量插入）应按普通 SQL 执行
            if (!((java.util.Map<?, ?>) meta.getOriginalObject()).containsKey(PAGE)) {
                return null;
            }
            Object value = meta.getValue(PAGE);
            return value instanceof Page ? (Page<?>) value : null;
        }

        Class<?> clazz = parameterObject.getClass();
        Boolean cached = PAGE_PROPERTY_CACHE.get(clazz);
        if (cached == null) {
            cached = meta.hasGetter(PAGE);
            PAGE_PROPERTY_CACHE.put(clazz, cached);
        }
        if (!Boolean.TRUE.equals(cached)) {
            return null;
        }
        Object value = meta.getValue(PAGE);
        return value instanceof Page ? (Page<?>) value : null;
    }

    @Override
    public Object plugin(Object target) {
        return Interceptor.super.plugin(target);
    }

    @Override
    public void setProperties(Properties properties) {
        if (properties == null || properties.isEmpty()) {
            return;
        }
        String dialectName = properties.getProperty("dialectClassName");
        if (dialectName == null) {
            dialectName = properties.getProperty("dialect");
        }
        if (dialectName != null) {
            setDialectClassName(dialectName);
        }
        String count = properties.getProperty("countEnabled");
        if (count != null) {
            setCountEnabled(Boolean.parseBoolean(count.trim()));
        }
    }
}
