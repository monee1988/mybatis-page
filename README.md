# mybatis-page

MyBatis 物理分页插件。挂在 `StatementHandler#prepare` 上，在 SQL 预编译前把原语句改写成带分页的语句，并自动执行一次 count 查询回填总数与页数。

支持 MySQL、Oracle、SQL Server、PostgreSQL 四种方言，也可以实现 `Dialect` 接口扩展其他数据库。

| 项 | 内容 |
|---|---|
| Maven 坐标 | `com.github.monee1988:mybatis-page:1.1.1-RELEASE` |
| 运行环境 | JDK 8+，MyBatis 3.5.x |
| 许可 | Apache License 2.0 |

Spring Boot 项目建议直接引入配套 starter `com.github.monee1988:mybatis-page-spring-boot-starter`，免去手工注册拦截器。

## 特性

- **零侵入**：Mapper XML 里写普通 SQL，不写 `LIMIT`，也不用改现有接口签名。
- **自动统计**：分页 SQL 执行前后各一次查询，`totalCount`、总页数、上下页标记全部由拦截器填好。
- **count SQL 智能推导**：按括号深度定位顶层关键字，`DISTINCT`、`GROUP BY`、`UNION`、`WITH ... AS` 以及含子查询的语句都能正确统计；无法安全改写时自动降级为 `SELECT COUNT(*) FROM (原SQL)` 包裹。
- **事务内统计**：count 查询复用当前 `prepare` 传进来的连接，与主查询同事务、同数据源。
- **无 Spring 依赖**：core 模块只用 MyBatis 自带的 `SystemMetaObject`，可作为纯 MyBatis 插件使用。

## 快速开始（纯 MyBatis）

### 1. 引入依赖

```xml
<dependency>
    <groupId>com.github.monee1988</groupId>
    <artifactId>mybatis-page</artifactId>
    <version>1.1.1-RELEASE</version>
</dependency>
```

MyBatis 为 `provided`，请由使用方自行提供版本；**日志不引入任何依赖**，直接使用 MyBatis 自带的 `LogFactory`（详见下文「日志」）。

### 2. 注册拦截器

`mybatis-config.xml`：

```xml
<plugins>
    <plugin interceptor="com.github.monee1988.mybatis.MybatisInterceptor">
        <property name="dialectClassName"
                  value="com.github.monee1988.mybatis.dialect.support.MySqlDialect"/>
        <property name="countEnabled" value="true"/>
    </plugin>
</plugins>
```

也可以用代码注册：

```java
MybatisInterceptor interceptor = new MybatisInterceptor(new MySqlDialect());
configuration.addInterceptor(interceptor);
```

### 3. 查询对象里加一个 `page` 字段

字段名**必须是 `page`**，类型为 `Page<T>`：

```java
public class Country {
    private String code;
    private String name;
    private Page<Country> page;
}
```

### 4. Service 里挂上分页参数

```java
public Page<Country> page(Country query, Page<Country> page) {
    query.setPage(page);                         // 1. 挂上去
    page.setList(countryMapper.findList(query));  // 2. 调 Mapper
    return page;                                 // 3. 总数与页数已由拦截器填好
}
```

### 5. Mapper XML 写普通 SQL

```xml
<select id="findList" resultType="Country">
    SELECT code, name FROM country
    WHERE 1 = 1
    <if test="code != null and code != ''">
        AND code LIKE CONCAT('%', #{code}, '%')
    </if>
    ORDER BY code ASC
</select>
```

实际执行时会被改写成：

```sql
SELECT code, name FROM country WHERE 1 = 1 AND code LIKE CONCAT('%', ?, '%') ORDER BY code ASC LIMIT 20, 10
```

## Page 的构造方式

| 写法 | 说明 |
|---|---|
| `new Page<>()` | 第 1 页，每页 20 条（`DEFAULT_PAGESIZE`） |
| `new Page<>(pageNo, pageSize)` | 指定页码与每页条数 |
| `Page.of(pageNo, pageSize)` | 同上，但自动容错 `null`、0、负数 |
| `Page.ofParams(map)` | 从参数 Map 中读取 `pageNo` / `pageSize`，适合 Controller 直接传 `request.getParameterMap()` |

常用属性与方法：

| 成员 | 说明 |
|---|---|
| `pageNo` / `pageSize` | 当前页码、每页条数 |
| `list` | 当前页数据，业务代码调完 Mapper 后 `setList` |
| `totalCount` | 总记录数，拦截器回填 |
| `totalPages` / `pageCount` | 总页数 |
| `offset` / `startRow` / `endRow` | 偏移量与行号区间 |
| `hasNext` / `nextPage` / `hasPre` / `prePage` | 翻页标记与相邻页码 |
| `startPageIndex` / `endPageIndex` | 页码条区间（`PAGE_BAR_SIZE` 默认 10） |
| `end()` | 校正页码并计算页码区间，拦截器已自动调用 |

## 拦截器如何找到 Page 对象

按顺序尝试三种方式，都找不到则视为普通查询、不做分页：

| 方式 | 接口写法 | 判定依据 |
|---|---|---|
| 实体字段 | `List<T> findList(Country q)`，`Country` 含 `page` 字段 | 反射查属性 `page` |
| Map 参数 | `List<T> findList(@Param("page") Page<T> p)` | Map 中含 key `page` |
| 分页对象本身 | `List<T> findList(Page<T> p)` | 参数即 `Page` 实例 |

## 配置项

以 `<property>` 形式写在拦截器上，或通过 `setProperties` 传入：

| 属性 | 默认 | 说明 |
|---|---|---|
| `dialectClassName` | 无 | 方言实现类全限定名，必填（也可用别名 `dialect`） |
| `countEnabled` | `true` | `false` 时不统计总数，`totalCount` 恒为 0，只做 SQL 级分页 |

## 内置方言

| 数据库 | 实现类 | 分页写法 |
|---|---|---|
| MySQL / MariaDB | `MySqlDialect` | `LIMIT offset, size`（带 `FOR UPDATE` 时 `LIMIT` 插在锁子句前） |
| PostgreSQL / KingbaseES / openGauss | `PostgreSqlDialect` | `LIMIT size OFFSET offset`（同上支持 `FOR UPDATE`） |
| Oracle | `OracleDialect` | 三层 `ROWNUM`，顶层 `ORDER BY` 提到最外层 |
| SQL Server 2005+ | `SqlServerDialect` | `ROW_NUMBER() OVER (...)` 包裹 |

扩展新数据库只需实现 `com.github.monee1988.mybatis.dialect.Dialect`：

```java
public interface Dialect {
    /** 是否支持物理分页 */
    boolean supportPageable();

    /** 生成分页 SQL，rowBounds.getOffset() 为偏移量，getLimit() 为每页条数 */
    String getDialectPageSql(String originalSql, RowBounds rowBounds);
}
```

> SQL Server 方言的包裹查询会多出一列 `MYBATIS_PAGE_RN_`。`resultType` 为实体类时按属性名映射会自动忽略；`resultType="map"` 时会多一个 key，介意的话请在 SQL 中显式列出字段。

## 日志

本库**不引入任何日志依赖**，日志全部走 MyBatis 自带的 `org.apache.ibatis.logging.LogFactory`：

- 按 `slf4j → commons-logging → log4j2 → log4j → JUL` 顺序自动探测，用户项目里有什么就用什么；全都没有时静默，**不会抛 `NoClassDefFoundError`**。
- 与 MyBatis 全局 `<setting name="logImpl" .../>` 统一：配一次，MyBatis 与本插件同时生效。纯 MyBatis 项目无需额外加 slf4j。
- 所有输出统一前缀 `mybatis-page: `（常量 `MybatisInterceptor.LOG_PREFIX`）。

| 日志 | 级别 | 用途 |
|---|---|---|
| `mybatis-page: 使用方言 XxxDialect` | DEBUG | 方言加载确认 |
| `mybatis-page: count SQL: ...` | DEBUG | 实际执行的 count 语句 |
| `mybatis-page: 分页 pageNo=..., pageSize=..., total=...` | DEBUG | 改写后的分页 SQL |
| `mybatis-page: 参数 X 中未找到 page 对象…` | DEBUG | 参数可能承载分页但没找到 page；**同类参数只提示一次**，`STDOUT_LOGGING` 下也不会按行刷屏 |

排查「为什么没分页」：把日志级别开到 debug

```yaml
logging:
  level:
    com.github.monee1988.mybatis: debug
```

纯 MyBatis 项目则配置 MyBatis 的 `logImpl`（如 `STDOUT_LOGGING` 或 `SLF4J`）。

能看到 `mybatis-page: 分页` 说明已识别到 `Page` 并改写了 SQL；没有这行就是没找到 `Page` 对象，按上面「三种方式」检查参数结构。

## 1.1.1 变更

- 移除 `slf4j-api` 依赖，日志改用 MyBatis 自带 `LogFactory`：纯 MyBatis 项目无 slf4j 也能运行（此前会 `NoClassDefFoundError`）
- 日志前缀抽为公共常量 `MybatisInterceptor.LOG_PREFIX`，starter 复用同一常量
- 修复 `STDOUT_LOGGING` 下「参数中没有 page」按调用次数刷屏：简单类型参数静默，Map / 实体同类只提示一次
- 修复带 `FOR UPDATE` 的语句分页时生成 `... FOR UPDATE LIMIT 0,10` 的语法错误（MySQL / PostgreSQL 方言把 `LIMIT` 插到锁子句之前）
- 无法推导 count SQL 的语句（无顶层 `FROM`）统一抛 `PersistenceException`，与统计失败的异常契约一致

## 1.1.0 变更

- 移除 `javax.servlet` 依赖，可用于 Spring Boot 3 / Jakarta EE 9+（`Page(HttpServletRequest)` 构造器由 `Page.of` / `Page.ofParams` 替代）
- 移除对 Spring 的依赖，core 可作为纯 MyBatis 插件运行
- count SQL 由字符串截取改为括号深度解析，`DISTINCT` / `GROUP BY` / `UNION` / `WITH` 统计结果正确
- count 查询改用当前事务连接，修正多数据源与事务外查询的问题
- `pageSize` 默认值改为 20，非法值自动回退
- 拦截器自动调用 `end()`，`pageCount` 不再为 0
- 新增 `PostgreSqlDialect`；`OracleDialect` 改为标准 `ROWNUM` 三层写法；`SqlServerDialect` 改用 `ROW_NUMBER`
- 移除 `MybatisMapperDynamicLoader`（XML 热加载），Spring Boot 下请使用 `spring-boot-devtools`

## 许可

[Apache License 2.0](LICENSE)
