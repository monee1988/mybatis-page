package com.github.monee1988.mybatis.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * count SQL 推导工具。
 *
 * <p>实现思路：先把字符串字面量、注释、引号标识符"脱敏"为等长空格，再按<b>括号深度</b>
 * 定位顶层关键字，从而正确区分主查询与子查询。例如
 * {@code SELECT (SELECT ... FROM ...) AS c FROM t} 能正确识别到外层 FROM，
 * {@code WHERE name = 'from x'} 也不会误判。</p>
 *
 * <p>含 DISTINCT、GROUP BY、HAVING、UNION、WITH 的语句无法通过改写 select 子句
 * 得到正确的总数（前者会把重复行计入、分组查询会让 count 返回多行），
 * {@link #buildCountSql(String)} 对这类语句自动降级为
 * {@code SELECT COUNT(*) FROM (原SQL) MYBATIS_PAGE_COUNT} 包裹式子查询。</p>
 *
 * @author monee1988
 */
public final class SqlUtils {

    /** 包裹式子查询的固定别名 */
    private static final String WRAP_ALIAS = "MYBATIS_PAGE_COUNT";

    /**
     * 顶层锁子句正则，覆盖：
     * {@code FOR UPDATE}、{@code FOR SHARE}、{@code FOR NO KEY UPDATE}、{@code FOR KEY SHARE}
     * （MySQL 8 / PostgreSQL），以及 MySQL 旧版 {@code LOCK IN SHARE MODE}。
     *
     * <p>这些子句在 MySQL / PostgreSQL 中只能出现在语句最末尾，因此：</p>
     * <ul>
     *   <li>拼接分页子句时必须插到它们<b>之前</b>；</li>
     *   <li>推导 count SQL 时必须整体剥掉——聚合查询带锁子句会被 PostgreSQL 直接拒绝，
     *       MySQL 则会把扫描行全部加锁。</li>
     * </ul>
     *
     * <p>外层用非捕获组包住，保证 {@link #indexOfTopLevel} 拼接的前后向断言作用于整个分支。</p>
     */
    private static final String LOCK_CLAUSE =
            "(?:FOR\\s+(?:UPDATE|SHARE|NO\\s+KEY\\s+UPDATE|KEY\\s+SHARE)|LOCK\\s+IN\\s+SHARE\\s+MODE)";

    private static final Pattern LEADING_WITH = Pattern.compile("^\\s*WITH\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEADING_DISTINCT =
            Pattern.compile("^\\s*SELECT\\s+DISTINCT\\b", Pattern.CASE_INSENSITIVE);

    private SqlUtils() {
    }

    /**
     * 推导 count SQL（优先使用"改写 select 子句"的轻量方式）。
     *
     * @param originalSql 原始查询 SQL
     * @return count SQL
     * @throws IllegalArgumentException 原 SQL 不含顶层 FROM，无法推导
     */
    public static String buildCountSql(String originalSql) {
        String sql = stripTrailingSemicolon(originalSql);
        String masked = mask(sql);
        int[] depth = computeDepth(masked);

        if (needsWrap(masked, depth)) {
            return buildWrappedCountSql(sql);
        }

        String core = stripTopLevelTailClauses(sql);
        String coreMasked = mask(core);
        int[] coreDepth = computeDepth(coreMasked);

        int from = indexOfTopLevel(coreMasked, coreDepth, "FROM");
        if (from < 0) {
            throw new IllegalArgumentException(
                    "无法推导 count SQL：原 SQL 中找不到顶层 FROM 关键字 -> " + originalSql);
        }
        return "SELECT COUNT(*) " + core.substring(from).trim();
    }

    /**
     * 恒包裹的 count SQL：{@code SELECT COUNT(*) FROM (原SQL) MYBATIS_PAGE_COUNT}。
     * 用于原 SQL 含有无法安全剥离的子句时。
     */
    public static String buildWrappedCountSql(String originalSql) {
        String core = stripTopLevelTailClauses(stripTrailingSemicolon(originalSql));
        return "SELECT COUNT(*) FROM (" + core + ") " + WRAP_ALIAS;
    }

    /**
     * 去掉顶层的 ORDER BY / LIMIT / OFFSET / 锁子句（FOR UPDATE 等）等尾部子句。
     * 子查询内部同名的子句不受影响（因为按括号深度判定）。
     */
    public static String stripTopLevelTailClauses(String sql) {
        if (sql == null || sql.isEmpty()) {
            return sql;
        }
        String masked = mask(sql);
        int[] depth = computeDepth(masked);

        int cut = -1;
        int p = indexOfTopLevel(masked, depth, "ORDER\\s+BY");
        cut = minPositive(cut, p);
        p = indexOfTopLevel(masked, depth, LOCK_CLAUSE);
        cut = minPositive(cut, p);
        p = indexOfTopLevel(masked, depth, "\\bLIMIT\\b");
        cut = minPositive(cut, p);
        p = indexOfTopLevel(masked, depth, "\\bOFFSET\\b");
        cut = minPositive(cut, p);
        p = indexOfTopLevel(masked, depth, "FETCH\\s+(?:FIRST|NEXT)");
        cut = minPositive(cut, p);

        return cut > 0 ? sql.substring(0, cut).trim() : sql.trim();
    }

    /** 统计 SQL 中的占位符 {@code ?} 个数（忽略字符串字面量与注释中的问号） */
    public static int countPlaceholders(String sql) {
        if (sql == null) {
            return 0;
        }
        String masked = mask(sql);
        int count = 0;
        for (int i = 0; i < masked.length(); i++) {
            if (masked.charAt(i) == '?') {
                count++;
            }
        }
        return count;
    }

    /**
     * 提取顶层 ORDER BY 子句（含关键字），没有则返回 null。
     *
     * <p>子句到 {@code LIMIT} 或锁子句（{@code FOR UPDATE} 等）为止——
     * 锁子句不能进入 {@code OVER()} 或最外层排序位置，否则会生成非法 SQL。</p>
     */
    public static String extractTopLevelOrderBy(String sql) {
        if (sql == null || sql.isEmpty()) {
            return null;
        }
        String masked = mask(sql);
        int[] depth = computeDepth(masked);
        int pos = indexOfTopLevel(masked, depth, "ORDER\\s+BY");
        if (pos < 0) {
            return null;
        }
        int end = sql.length();
        int limit = indexOfTopLevel(masked, depth, "\\bLIMIT\\b");
        if (limit > pos && limit < end) {
            end = limit;
        }
        int lock = indexOfTopLevel(masked, depth, LOCK_CLAUSE);
        if (lock > pos && lock < end) {
            end = lock;
        }
        return sql.substring(pos, end).trim();
    }

    /**
     * 顶层锁子句（{@code FOR UPDATE} / {@code FOR SHARE} / {@code LOCK IN SHARE MODE} 等）
     * 的起始位置，没有则返回 -1。
     *
     * <p>MySQL / PostgreSQL 的语法要求分页子句写在锁子句之前，
     * 方言拼接 {@code LIMIT} 时据此位置插入，避免生成
     * {@code ... FOR UPDATE LIMIT 0,10} 这类非法 SQL。</p>
     *
     * @param sql 原始 SQL
     * @return 顶层锁子句起始索引，不存在时返回 -1
     */
    public static int indexOfTopLevelLockClause(String sql) {
        if (sql == null || sql.isEmpty()) {
            return -1;
        }
        String masked = mask(sql);
        return indexOfTopLevel(masked, computeDepth(masked), LOCK_CLAUSE);
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    /** 判定是否需要降级为包裹式 count */
    private static boolean needsWrap(String masked, int[] depth) {
        if (LEADING_WITH.matcher(masked).find()) {
            return true;
        }
        if (LEADING_DISTINCT.matcher(masked).find()) {
            return true;
        }
        if (indexOfTopLevel(masked, depth, "UNION") >= 0) {
            return true;
        }
        // 顶层 GROUP BY / HAVING 会让 count 查询返回多行
        if (indexOfTopLevel(masked, depth, "GROUP\\s+BY") >= 0) {
            return true;
        }
        if (indexOfTopLevel(masked, depth, "HAVING") >= 0) {
            return true;
        }
        return false;
    }

    private static int minPositive(int current, int candidate) {
        if (candidate <= 0) {
            return current;
        }
        return current < 0 ? candidate : Math.min(current, candidate);
    }

    /**
     * 脱敏：把字符串字面量、行注释、块注释、反引号/双引号标识符的内容替换为空格。
     * 输出长度与输入完全一致，因此位置索引可直接复用。
     */
    static String mask(String sql) {
        int n = sql.length();
        char[] out = sql.toCharArray();
        int i = 0;
        while (i < n) {
            char c = sql.charAt(i);
            if (c == '\'' || c == '"' || c == '`') {
                char quote = c;
                out[i] = ' ';
                i++;
                while (i < n) {
                    if (quote == '\'' && sql.charAt(i) == '\'' && i + 1 < n && sql.charAt(i + 1) == '\'') {
                        // '' 转义的单引号
                        out[i] = ' ';
                        out[i + 1] = ' ';
                        i += 2;
                        continue;
                    }
                    if (sql.charAt(i) == quote) {
                        out[i] = ' ';
                        i++;
                        break;
                    }
                    out[i] = ' ';
                    i++;
                }
            } else if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                while (i < n && sql.charAt(i) != '\n') {
                    out[i] = ' ';
                    i++;
                }
            } else if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                out[i] = ' ';
                out[i + 1] = ' ';
                i += 2;
                while (i < n) {
                    if (sql.charAt(i) == '*' && i + 1 < n && sql.charAt(i + 1) == '/') {
                        out[i] = ' ';
                        out[i + 1] = ' ';
                        i += 2;
                        break;
                    }
                    out[i] = ' ';
                    i++;
                }
            } else {
                i++;
            }
        }
        return new String(out);
    }

    /** 计算每个位置之前尚未闭合的左括号数量 */
    static int[] computeDepth(String masked) {
        int[] depth = new int[masked.length()];
        int current = 0;
        for (int i = 0; i < masked.length(); i++) {
            depth[i] = current;
            char c = masked.charAt(i);
            if (c == '(') {
                current++;
            } else if (c == ')') {
                current = current > 0 ? current - 1 : 0;
            }
        }
        return depth;
    }

    /** 查找括号深度为 0 的关键字首次出现位置，找不到返回 -1 */
    static int indexOfTopLevel(String masked, int[] depth, String keywordRegex) {
        Pattern pattern = Pattern.compile(
                "(?<![A-Za-z0-9_$])" + keywordRegex + "(?![A-Za-z0-9_$])",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(masked);
        while (matcher.find()) {
            if (depth[matcher.start()] == 0) {
                return matcher.start();
            }
        }
        return -1;
    }

    private static String stripTrailingSemicolon(String sql) {
        if (sql == null) {
            return "";
        }
        String trimmed = sql.trim();
        while (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return trimmed;
    }
}
