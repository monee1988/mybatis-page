package com.github.monee1988.mybatis.entity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 与具体 ORM 实现无关的分页参数及查询结果封装。
 *
 * <p>典型用法：查询实体中声明名为 {@code page} 的 {@link Page} 属性（或直接以 Page
 * 作为 Mapper 方法参数），分页拦截器即可识别并完成物理分页。
 * {@link #end()} 负责总页数与页码条区间的计算，幂等、可重复调用。</p>
 *
 * <p>Web 层从请求参数取值时，可使用 {@link #of(Integer, Integer)} 或 {@link #ofParams(Map)}
 * 容错构造，参数缺失或非法时自动回退默认值。</p>
 *
 * @param <T> Page 中记录的类型
 * @author monee1988
 */
public class Page<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 正排序 */
    public static final String ASC = "ASC";

    /** 倒排序 */
    public static final String DESC = "DESC";

    /** 默认每页容量 */
    public static final int DEFAULT_PAGESIZE = 20;

    /** 默认页码 */
    public static final int DEFAULT_PAGE_NO = 1;

    /** 页码条显示的最大页码个数 */
    public static final int PAGE_BAR_SIZE = 10;

    /** 当前页码 */
    protected int pageNo = DEFAULT_PAGE_NO;

    /** 每页记录数 */
    protected int pageSize = DEFAULT_PAGESIZE;

    /** 页码条起始索引（从 1 开始），由 {@link #end()} 计算 */
    private int startPageIndex;

    /** 页码条结束索引（含），由 {@link #end()} 计算 */
    private int endPageIndex;

    /** 总页数，由 {@link #end()} 计算 */
    private int pageCount;

    /** 扩展信息，业务可自行塞入 */
    private Map<String, Object> extend;

    /** 当前页数据 */
    private List<T> list = new ArrayList<>();

    /** 总记录数，由分页拦截器回填 */
    private long totalCount = 0L;

    /** 记录本次实际执行的（分页前）SQL，仅作调试用途，不参与分页逻辑 */
    private String sql;

    public Page() {
    }

    public Page(int pageSize) {
        setPageSize(pageSize);
    }

    public Page(int pageNo, int pageSize) {
        setPageNo(pageNo);
        setPageSize(pageSize);
    }

    public Page(int pageNo, int pageSize, long totalCount) {
        setPageNo(pageNo);
        setPageSize(pageSize);
        this.totalCount = totalCount;
    }

    /**
     * 容错构造：参数为 null 或非正数时使用默认值。
     * 适用于 Web 层从请求参数取值的场景。
     *
     * @param pageNo   页码，null 或小于 1 时取 {@value #DEFAULT_PAGE_NO}
     * @param pageSize 每页条数，null 或小于 1 时取 {@value #DEFAULT_PAGESIZE}
     * @return Page 实例
     */
    public static <T> Page<T> of(Integer pageNo, Integer pageSize) {
        return new Page<>(pageNo == null ? DEFAULT_PAGE_NO : pageNo,
                pageSize == null ? DEFAULT_PAGESIZE : pageSize);
    }

    /**
     * 从参数 Map 构造，取 {@code pageNo} / {@code pageSize} 两个 key，
     * 兼容 {@code request.getParameterMap()} 的 String[] 值。
     *
     * @param params 参数 Map，可为 null
     * @return Page 实例，缺省时页码 1、每页 {@value #DEFAULT_PAGESIZE} 条
     */
    public static <T> Page<T> ofParams(Map<String, ?> params) {
        return new Page<>(toInt(params == null ? null : params.get("pageNo"), DEFAULT_PAGE_NO),
                toInt(params == null ? null : params.get("pageSize"), DEFAULT_PAGESIZE));
    }

    private static int toInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof Object[]) {
            Object[] array = (Object[]) value;
            return array.length == 0 ? defaultValue : toInt(array[0], defaultValue);
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 计算总页数与页码条区间。
     *
     * <p>分页拦截器在回填 totalCount 后会自动调用本方法，
     * 因此业务代码通常不需要再手动调用；手动构造 Page 用于非拦截器场景时才需要。</p>
     *
     * @return this，便于链式调用
     */
    public Page<T> end() {
        // 1. 校正页码（越界回退到最后一页）
        if (pageSize <= 0) {
            pageSize = DEFAULT_PAGESIZE;
        }
        if (pageNo < DEFAULT_PAGE_NO) {
            pageNo = DEFAULT_PAGE_NO;
        }
        long totalPages = getTotalPages();
        if (totalPages > 0 && pageNo > totalPages) {
            pageNo = (int) totalPages;
        }

        // 2. 总页数
        pageCount = (int) Math.max(totalPages, 0);

        // 3. 页码条区间：总数不超过 10 页时全显示；否则当前页前 4 后 5，共 10 个
        if (pageCount <= PAGE_BAR_SIZE) {
            startPageIndex = 1;
            endPageIndex = pageCount;
        } else {
            startPageIndex = pageNo - 4;
            endPageIndex = pageNo + 5;
            if (startPageIndex < 1) {
                startPageIndex = 1;
                endPageIndex = PAGE_BAR_SIZE;
            } else if (endPageIndex > pageCount) {
                endPageIndex = pageCount;
                startPageIndex = pageCount - PAGE_BAR_SIZE + 1;
            }
        }
        return this;
    }

    /** 链式设置页码 */
    public Page<T> pageNo(int pageNo) {
        setPageNo(pageNo);
        return this;
    }

    /** 链式设置每页条数 */
    public Page<T> pageSize(int pageSize) {
        setPageSize(pageSize);
        return this;
    }

    /**
     * 获得当前页码。totalCount 已回填且页码越界时，返回最后一页。
     * 不修改字段值，避免查询前调用产生副作用。
     */
    public int getPageNo() {
        if (pageNo < DEFAULT_PAGE_NO) {
            return DEFAULT_PAGE_NO;
        }
        long totalPages = getTotalPages();
        if (totalPages > 0 && pageNo > totalPages) {
            return (int) totalPages;
        }
        return pageNo;
    }

    /** 设置当前页码，只做下限保护 */
    public void setPageNo(int pageNo) {
        this.pageNo = pageNo < DEFAULT_PAGE_NO ? DEFAULT_PAGE_NO : pageNo;
    }

    /** 每页记录数 */
    public int getPageSize() {
        return pageSize;
    }

    /** 设置每页记录数，非正数时回退为默认值 */
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize <= 0 ? DEFAULT_PAGESIZE : pageSize;
    }

    /** 当前页第 1 条记录在结果集中的位置（从 1 开始） */
    public int getFirst() {
        return (getPageNo() - 1) * pageSize + 1;
    }

    public List<T> getList() {
        return list;
    }

    public void setList(List<T> list) {
        this.list = list == null ? new ArrayList<T>() : list;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(long totalCount) {
        this.totalCount = totalCount;
    }

    /** 按 pageSize 与 totalCount 实时计算总页数；totalCount 为负时返回 -1 */
    public long getTotalPages() {
        if (totalCount < 0) {
            return -1;
        }
        if (pageSize <= 0) {
            return 0;
        }
        long count = totalCount / pageSize;
        if (totalCount % pageSize > 0) {
            count++;
        }
        return count;
    }

    public boolean isHasNext() {
        return getPageNo() + 1 <= getTotalPages();
    }

    /** 下一页页码，已是尾页时返回尾页 */
    public int getNextPage() {
        return isHasNext() ? getPageNo() + 1 : getPageNo();
    }

    public boolean isHasPre() {
        return getPageNo() - 1 >= 1;
    }

    /** 上一页页码，已是首页时返回首页 */
    public int getPrePage() {
        return isHasPre() ? getPageNo() - 1 : getPageNo();
    }

    /** MySQL / PostgreSQL 用的偏移量 */
    public int getOffset() {
        return (getPageNo() - 1) * pageSize;
    }

    /** Oracle 用：起始行号（从 1 开始） */
    public int getStartRow() {
        return getOffset() + 1;
    }

    /** Oracle 用：结束行号（含） */
    public int getEndRow() {
        return pageSize * getPageNo();
    }

    public int getStartPageIndex() {
        return startPageIndex;
    }

    public void setStartPageIndex(int startPageIndex) {
        this.startPageIndex = startPageIndex;
    }

    public int getEndPageIndex() {
        return endPageIndex;
    }

    public void setEndPageIndex(int endPageIndex) {
        this.endPageIndex = endPageIndex;
    }

    public int getPageCount() {
        return pageCount;
    }

    public void setPageCount(int pageCount) {
        this.pageCount = pageCount;
    }

    public Map<String, Object> getExtend() {
        return extend;
    }

    public void setExtend(Map<String, Object> extend) {
        this.extend = extend;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    @Override
    public String toString() {
        return "Page{pageNo=" + getPageNo()
                + ", pageSize=" + pageSize
                + ", totalCount=" + totalCount
                + ", pageCount=" + pageCount
                + ", listSize=" + (list == null ? 0 : list.size()) + '}';
    }
}
