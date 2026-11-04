package com.newssystem.common;

import java.io.Serializable;
import java.util.List;

/**
 * 分页结果封装
 * 用于列表分页查询返回
 *
 * @param <T> 数据行泛型
 * @author NewsSystem Dev
 */
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer pageNum;
    private Integer pageSize;
    private Long total;
    private Integer pages;
    private List<T> rows;

    public PageResult() {}

    public PageResult(Integer pageNum, Integer pageSize, Long total, List<T> rows) {
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.total = total;
        this.rows = rows;
        if (total != null && pageSize != null && total > 0 && pageSize > 0) {
            this.pages = (int) Math.ceil((double) total / pageSize);
        } else {
            this.pages = 0;
        }
    }

    public Integer getPageNum() { return pageNum; }
    public void setPageNum(Integer pageNum) { this.pageNum = pageNum; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
    public Long getTotal() { return total; }
    public void setTotal(Long total) { this.total = total; }
    public Integer getPages() { return pages; }
    public void setPages(Integer pages) { this.pages = pages; }
    public List<T> getRows() { return rows; }
    public void setRows(List<T> rows) { this.rows = rows; }

    public static <T> PageResult<T> of(Integer pageNum, Integer pageSize, Long total, List<T> rows) {
        return new PageResult<>(pageNum, pageSize, total, rows);
    }
}
