package com.github.monee1988.mybatis.entity;

import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 与具体ORM实现无关的分页参数及查询结果封装.
 * @author monee1988
 * @param <T> Page中记录的类型.
 */
public class Page<T> implements Serializable{

    /**
     * 正排序
     */
    public static final String ASC = "ASC";

    /**
     * 倒排序
     */
    public static final String DESC = "DESC";

    /**
     * 默认分页容量
     */
    public static final int DEFAULT_PAGESIZE =20;

    /**
     * 默认的页码
     */
    public static final int DEFAULT_PAGE_NO= 1;

    /**
     * 当前页码
     */
    protected int pageNo = 1;

    /**
     * 页码数据容量
     */
    protected int pageSize = -1;

    /**
     * 显示的页码列表的起始索引
     */
    private int startPageIndex;

    /**
     *  显示的页码列表的结束索引
     */
    private int endPageIndex;

    /**
     * 总页数据
     */
    private int pageCount;

    /**
     * page扩展信息
     */
    private Map<String, Object> extend;

    /**
     * 返回结果
     */
    private List<T> list = new ArrayList<>();

    /**
     * 总数量
     */
    private long totalCount = 0;

    /**
     * sql 语句
     */
    private String sql;

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

    /**
     * 分页参数访问函数
     * @param request request请求参数
     */
    public Page(HttpServletRequest request) {
    	request.getParameterMap();
    	String pageNo = request.getParameter("pageNo");
		String pageSize = request.getParameter("pageSize");
		if (!StringUtils.isBlank(pageNo)) {
			this.pageNo = Integer.parseInt(pageNo);
		}else{
			this.pageNo = DEFAULT_PAGE_NO;
		}
		if (!StringUtils.isBlank(pageSize)) {
			this.pageSize = Integer.parseInt(pageSize);
		}else{
			this.pageSize = DEFAULT_PAGESIZE;
		}
	}

    /**
     * 返回Page对象自身的setPageNo函数,可用于连续设置
     */
    public Page<T> pageNo(int pageNo) {
        setPageNo(pageNo);
        return this;
    }

    /**
     * 返回Page对象自身的setPageSize函数,可用于连续设置
     */
    public Page<T> pageSize(int pageSize) {
        setPageSize(pageSize);
        return this;
    }
    
	/**
     * 获得当前页的页号
     */
    public int getPageNo() {
        if(pageNo > getTotalPages()){
            setPageNo(Long.valueOf(getTotalPages()).intValue());
        }
        if(pageNo <= 0){
            setPageNo(DEFAULT_PAGE_NO);
        }
        return this.pageNo;
    }

    /**
     * 设置当前页的页号
     */
    public void setPageNo(int pageNo) {
        this.pageNo = pageNo<DEFAULT_PAGE_NO?DEFAULT_PAGE_NO:(pageNo>getTotalCount()?Long.valueOf(getTotalPages()).intValue():pageNo);
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
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    /**
     * 根据pageNo和pageSize计算当前页第1条记录在结果集中的位置.
     */
    public int getFirst() {
        return ((pageNo - 1) * pageSize) + 1;
    }

    /**
     * 获得页内的记录列数据
     */
    public List<T> getList() {
        return list;
    }

    /**
     * 设置页内的记录列数据
     */
    public void setList(List<T> list) {
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
    public void setTotalCount(long totalCount) {
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
        return ((getPageNo() - 1) * pageSize);
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
