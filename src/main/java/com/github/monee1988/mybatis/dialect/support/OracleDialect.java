package com.github.monee1988.mybatis.dialect.support;

import com.github.monee1988.mybatis.dialect.Dialect;
import com.github.monee1988.mybatis.util.SqlUtils;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.RowBounds;

import static com.github.monee1988.mybatis.MybatisInterceptor.LOG_PREFIX;

/**
 * Oracle 分页方言，采用 ROWNUM 三层包裹写法，兼容 Oracle 8i ~ 11g（12c+ 亦可）。
 *
 * <p>生成形如：</p>
 *
 * <pre>
 * SELECT * FROM (SELECT ROW_.*, ROWNUM ROWNUM_ FROM (原SQL) ROW_ WHERE ROWNUM &lt;= end)
 *          WHERE ROWNUM_ &gt; offset
 * </pre>
 *
 * <p>顶层 ORDER BY 会提取到最外层执行，保证 ROWNUM 分页下行序稳定，
 * 这是 Oracle ROWNUM 分页的标准写法。</p>
 *
 * <p><b>不支持锁子句</b>：Oracle 不允许对含 ROWNUM / 内联视图的结果集加 {@code FOR UPDATE}
 * （ORA-02014），ROWNUM 分页与悲观锁天然冲突。此时本方言直接抛
 * {@link PersistenceException}，而不是悄悄把锁子句剥掉——后者会让调用方误以为行已锁定，
 * 并发下静默产生丢失更新。</p>
 *
 * @author monee1988
 */
public class OracleDialect implements Dialect {

    @Override
    public boolean supportPageable() {
        return true;
    }

    @Override
    public String getDialectPageSql(String originalSql, RowBounds rowBounds) {
        if (SqlUtils.indexOfTopLevelLockClause(originalSql) >= 0) {
            throw new PersistenceException(LOG_PREFIX + "Oracle 方言不支持在分页语句中使用锁子句"
                    + "（FOR UPDATE / FOR SHARE / LOCK IN SHARE MODE）。Oracle 对含 ROWNUM 的结果集加锁会报 ORA-02014，"
                    + "本插件不会为了让语句跑通而丢弃锁。请改为：先分页查询主键列表，再按主键加锁查询明细；"
                    + "或该语句不传 Page 对象。原 SQL: " + originalSql);
        }
        String orderBy = SqlUtils.extractTopLevelOrderBy(originalSql);
        String core = SqlUtils.stripTopLevelTailClauses(originalSql);

        long offset = rowBounds.getOffset();
        long end = offset + rowBounds.getLimit();

        StringBuilder sql = new StringBuilder(core.length() + 128);
        sql.append("SELECT * FROM (SELECT ROW_.*, ROWNUM ROWNUM_ FROM (")
                .append(core)
                .append(") ROW_ WHERE ROWNUM <= ")
                .append(end)
                .append(") WHERE ROWNUM_ > ")
                .append(offset);
        if (orderBy != null) {
            sql.append(' ').append(orderBy);
        }
        return sql.toString();
    }
}
