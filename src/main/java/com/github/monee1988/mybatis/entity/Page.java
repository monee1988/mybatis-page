package com.github.monee1988.mybatis.entity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;

/**
 * 与具体ORM实现无关的分页参数及查询结果封装.
 * <p/>
 *
 * @param <T> Page中记录的类型.
 */
public class Page<T> implements Serializable{
    //-- 公共变量 --//
    public static final String ASC = "asc";
    public static final String DESC = "desc";
    public static final int DEFAULT_PAGESIZE =10;
    public static final int DEFAULT_PAGENO= 1;

    //-- 分页参数 --//
    protected int pageNo = 1;
    protected int pageSize = -1;
    protected String orderBy = null;
    protected String order = null;
    protected boolean autoCount = true;

    private int startPageIndex; // 显示的页码列表的起始索引
    private int endPageIndex; // 显示的页码列表的结束索引
    private int pageCount; //总页数据

    private Map<String, Object> extend; // page扩展信息

    //-- 返回结果 --//
    private List<T> list = new ArrayList<T>();
    private long totalCount = 0;
    private String sql;

    //-- 构造函数 --//
    public Page() {
    }

    public Page<T> end() {
        // 1, 总页数
        pageCount = ((int) this.totalCount + pageSize - 1) / pageSize;
        // 2, startPageIndex（显示的页码列表的开始索引）与endPageIndex（显示的页码列表的结束索引）
        // a, 总页码不大于10的时候
        if (pageCount <= 10) {
            startPageIndex = 1;
            endPageIndex = pageCount;
        }
        // b, 总码大于10的时候
        else {
            // 在中间，显示前面4个，后面5
            startPageIndex = pageNo - 4;
            endPageIndex = pageNo + 5;

            // 前面不足4个时，显示前10个页
            if (startPageIndex < 1) {
                startPageIndex = 1;
                endPageIndex = 10;
            }
            // 后面不足5个时，显示后10个页
            else if (endPageIndex > pageCount) {
                endPageIndex = pageCount;
                startPageIndex = pageCount - 10 + 1;
            }
        }
        return this;
    }

    public Page(int pageSize) {
        this.pageSize = pageSize;
    }

    public Page(int pageNo, int pageSize) {
        this.pageNo = pageNo;
        this.pageSize = pageSize;
    }

    public Page(int pageNo, int pageSize, int totalCount) {
        this.pageNo = pageNo;
        this.pageSize = pageSize;
        this.totalCount = totalCount;
    }

    //-- 分页参数访问函数 --//

    public Page(HttpServletRequest request, HttpServletResponse response) {
    	request.getParameterMap();
    	String pageNoStr = request.getParameter("pageNo");
		String pageSizeStr = request.getParameter("pageSize");
		if (!StringUtils.isBlank(pageNoStr)) {
			this.pageNo = Integer.valueOf(pageNoStr);
		}else{
			this.pageNo = DEFAULT_PAGENO;
		}
		if (!StringUtils.isBlank(pageSizeStr)) {
			this.pageSize = Integer.valueOf(pageSizeStr);
		}else{
			this.pageSize = DEFAULT_PAGESIZE;
		}
	}

	/**
     * 获得当前页的页号
     */
    public int getPageNo() {
        return pageNo;
    }

    /**
     * 设置当前页的页号
     */
    public void setPageNo(final int pageNo) {
        this.pageNo = pageNo;

        if (pageNo < 1) {
            this.pageNo = 1;
        }
    }

    /**
     * 返回Page对象自身的setPageNo函数,可用于连续设置
     */
    public Page<T> pageNo(final int thePageNo) {
        setPageNo(thePageNo);
        return this;
    }

    /**
     * 获得每页的记录数 .
     */
    public int getPageSize() {
        return pageSize;
    }

    /**
     * 设置每页的记录数
     */
    public void setPageSize(final int pageSize) {
        this.pageSize = pageSize;
    }

    /**
     * 返回Page对象自身的setPageSize函数,可用于连续设置
     */
    public Page<T> pageSize(final int thePageSize) {
        setPageSize(thePageSize);
        return this;
    }

    /**
     * 根据pageNo和pageSize计算当前页第1条记录在结果集中的位置.
     */
    public int getFirst() {
        return ((pageNo - 1) * pageSize) + 1;
    }

    /**
     * 获得排序字段, 多个排序字段时用','分隔.
     */
    public String getOrderBy() {
        return orderBy;
    }

    /**
     * 设置排序字段,多个排序字段时用','分隔.
     */
    public void setOrderBy(final String orderBy) {
        this.orderBy = orderBy;
    }

    /**
     * 返回Page对象自身的setOrderBy函数
     */
    public Page<T> orderBy(final String theOrderBy) {
        setOrderBy(theOrderBy);
        return this;
    }

    /**
     * 获得排序方向
     */
    public String getOrder() {
        return order;
    }

    /**
     * 设置排序方式
     *
     * @param order 值为desc或asc,多个排序字段时用','分隔.
     */
    public void setOrder(final String order) {
        String lowcaseOrder = StringUtils.lowerCase(order);

        String[] orders = StringUtils.split(lowcaseOrder, ',');
        for (String orderStr : orders) {
            if (!StringUtils.equals(DESC, orderStr) && !StringUtils.equals(ASC, orderStr)) {
                throw new IllegalArgumentException("  " + orderStr + " ");
            }
        }
        this.order = lowcaseOrder;
    }

    /**
     * 返回Page对象自身的setOrder函数
     */
    public Page<T> order(final String theOrder){
    	setOrder(theOrder);
        return this;
    }

    /**
     * 是否已设置排序字
     */
    public boolean isOrderBySetted() {
        return (StringUtils.isNotBlank(orderBy) && StringUtils.isNotBlank(order));
    }

    /**
     * 获得查询对象时是否先自动执行count查询获取总记录数, 默认为false.
     */
    public boolean isAutoCount() {
        return autoCount;
    }

    /**
     * 设置查询对象时是否自动先执行count查询获取总记录数.
     */
    public void setAutoCount(final boolean autoCount) {
        this.autoCount = autoCount;
    }

    /**
     * 返回Page对象自身的setAutoCount函数
     */
    public Page<T> autoCount(final boolean theAutoCount) {
        setAutoCount(theAutoCount);
        return this;
    }

    //-- 访问查询结果函数 --//

    /**
     * 获得页内的记录列数据
     */
    public List<T> getList() {
        return list;
    }

    /**
     * 设置页内的记录列数据
     */
    public void setList(final List<T> list) {
        this.list = list;
    }

    /**
     * 获得总记录数, 默认值为-1.
     */
    public long getTotalCount() {
        return totalCount;
    }

    /**
     * 设置总记录数.
     */
    public void setTotalCount(final long totalCount) {
        this.totalCount = totalCount;
    }

    /**
     * 根据pageSize与totalCount计算总页数.
     */
    public long getTotalPages() {
        if (totalCount < 0) {
            return -1;
        }

        long count = totalCount / pageSize;
        if (totalCount % pageSize > 0) {
            count++;
        }
        return count;
    }

    /**
     * 是否还有下一页
     */
    public boolean isHasNext() {
        return (pageNo + 1 <= getTotalPages());
    }

    /**
     * 取得下页的页码 .
     * 当前页为尾页时仍返回尾页序号.
     */
    public int getNextPage() {
        if (isHasNext()) {
            return pageNo + 1;
        } else {
            return pageNo;
        }
    }

    /**
     * 是否还有上一页
     */
    public boolean isHasPre() {
        return (pageNo - 1 >= 1);
    }

    /**
     * 取得上页的页码.
     * 当前页为首页时返回首页
     */
    public int getPrePage() {
        if (isHasPre()) {
            return pageNo - 1;
        } else {
            return pageNo;
        }
    }


    /**
     * 用于Mysql,Hibernate.
     */
    public int getOffset() {
        return ((pageNo - 1) * pageSize);
    }

    /**
     * 用于Oracle.
     */
    public int getStartRow() {
        return getOffset() + 1;
    }

    /**
     * 用于Oracle.
     */
    public int getEndRow() {
        return pageSize * pageNo;
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


}
