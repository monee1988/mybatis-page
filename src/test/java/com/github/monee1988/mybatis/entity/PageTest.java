package com.github.monee1988.mybatis.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Page 分页参数与页码计算测试。
 */
class PageTest {

    @Test
    @DisplayName("无参构造：pageSize 默认 20")
    void defaultPageSize() {
        Page<Object> page = new Page<>();
        assertEquals(20, page.getPageSize());
        assertEquals(1, page.getPageNo());
        assertEquals(0, page.getOffset());
    }

    @Test
    @DisplayName("非法 pageSize 回退为默认值，避免生成 LIMIT 0,-1")
    void invalidPageSizeFallsBack() {
        Page<Object> page = new Page<>(1, -1);
        assertEquals(20, page.getPageSize());

        page.setPageSize(0);
        assertEquals(20, page.getPageSize());
    }

    @Test
    @DisplayName("页码小于 1 时回退为首页")
    void pageNoLowerBound() {
        Page<Object> page = new Page<>(0, 10);
        assertEquals(1, page.getPageNo());
    }

    @Test
    @DisplayName("totalCount 未回填时不裁剪页码")
    void pageNoNotClampedBeforeCount() {
        Page<Object> page = new Page<>(5, 10);
        assertEquals(5, page.getPageNo());
        assertEquals(40, page.getOffset());
    }

    @Test
    @DisplayName("页码越界回退到最后一页")
    void pageNoUpperBound() {
        Page<Object> page = new Page<>(99, 10);
        page.setTotalCount(25);
        assertEquals(3, page.getPageNo());
        assertEquals(20, page.getOffset());
    }

    @Test
    @DisplayName("end() 计算总页数与页码区间")
    void endComputesPageCount() {
        Page<Object> page = new Page<>(2, 10);
        page.setTotalCount(25);
        page.end();

        assertEquals(3, page.getPageCount());
        assertEquals(1, page.getStartPageIndex());
        assertEquals(3, page.getEndPageIndex());
        assertTrue(page.isHasNext());
        assertTrue(page.isHasPre());
        assertEquals(3, page.getNextPage());
        assertEquals(1, page.getPrePage());
    }

    @Test
    @DisplayName("页码条：总页数超过 10 时当前页前 4 后 5")
    void pageBarInMiddle() {
        Page<Object> page = new Page<>(50, 10);
        page.setTotalCount(1000);
        page.end();

        assertEquals(100, page.getPageCount());
        assertEquals(46, page.getStartPageIndex());
        assertEquals(55, page.getEndPageIndex());
    }

    @Test
    @DisplayName("页码条：靠近首页")
    void pageBarAtHead() {
        Page<Object> page = new Page<>(2, 10);
        page.setTotalCount(1000);
        page.end();

        assertEquals(1, page.getStartPageIndex());
        assertEquals(10, page.getEndPageIndex());
    }

    @Test
    @DisplayName("页码条：靠近尾页")
    void pageBarAtTail() {
        Page<Object> page = new Page<>(99, 10);
        page.setTotalCount(1000);
        page.end();

        assertEquals(91, page.getStartPageIndex());
        assertEquals(100, page.getEndPageIndex());
    }

    @Test
    @DisplayName("总数为 0：pageCount 为 0，页码条为空区间（end < start），不越界裁剪")
    void emptyResult() {
        Page<Object> page = new Page<>(1, 10);
        page.setTotalCount(0);
        page.end();

        assertEquals(0, page.getPageCount());
        // start=1 / end=0 构成空区间，前端 forEach(begin=1, end=0) 不会渲染任何页码
        assertEquals(1, page.getStartPageIndex());
        assertEquals(0, page.getEndPageIndex());
        assertFalse(page.isHasNext());
    }

    @Test
    @DisplayName("end() 可重复调用，结果幂等")
    void endIsIdempotent() {
        Page<Object> page = new Page<>(3, 10);
        page.setTotalCount(55);
        page.end();
        int first = page.getStartPageIndex();
        page.end();
        assertEquals(first, page.getStartPageIndex());
        assertEquals(6, page.getPageCount());
    }

    @Test
    @DisplayName("of() 容错构造：null 用默认值")
    void ofFactory() {
        Page<Object> page = Page.of(null, null);
        assertEquals(1, page.getPageNo());
        assertEquals(20, page.getPageSize());

        Page<Object> page2 = Page.of(3, 5);
        assertEquals(3, page2.getPageNo());
        assertEquals(5, page2.getPageSize());
    }

    @Test
    @DisplayName("Oracle 行号：startRow / endRow")
    void oracleRows() {
        Page<Object> page = new Page<>(3, 10);
        assertEquals(21, page.getStartRow());
        assertEquals(30, page.getEndRow());
    }
}
