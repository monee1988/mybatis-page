package com.github.monee1988.mybatis.dialect;

import org.apache.ibatis.session.RowBounds;

/**
 * 分页方言接口。实现类需提供无参构造器（拦截器按类名反射实例化）。
 *
 * <p>扩展方式：实现本接口并在配置中指定实现类的全限定名，
 * 或使用 {@code mybatis-page.dialect} 中已注册的别名。</p>
 *
 * @author monee1988
 */
public interface Dialect {

    /**
     * 是否支持物理分页。返回 false 时拦截器会跳过该方言（等价于关闭分页）。
     */
    boolean supportPageable();

    /**
     * 生成分页 SQL。
     *
     * @param originalSql 原查询 SQL（尚未分页）
     * @param rowBounds   分页边界，{@code getOffset()} 为起始偏移，{@code getLimit()} 为每页条数
     * @return 分页后的 SQL
     */
    String getDialectPageSql(String originalSql, RowBounds rowBounds);
}
