package com.newssystem.common;

import com.newssystem.entity.News;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 用户端"某新闻网热点排行榜"查询结果封装。
 *
 * 用于用户端从新闻列表跳转到具体新闻网的热点榜单页：
 * - 正常情况：返回该新闻网请求日期当天的热点列表（fallback=false，actualDate=请求日期）；
 * - 回退情况：当天无数据时自动回退为该网最新一批热点（fallback=true，
 *   actualDate=回退数据实际所属日期），页面据此显示提示。
 *
 * @author NewsSystem Dev
 */
public class SourceHotResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 新闻网名称（已去空白；null/空表示全站聚合模式） */
    private String source;
    /** 用户请求查看的日期 */
    private LocalDate requestDate;
    /** 热点新闻列表（正常/回退后的最终列表） */
    private List<News> list;
    /** 是否为回退数据（true=请求日期当天无数据，展示的是最新一批） */
    private boolean fallback;
    /** 回退数据的实际日期（正常时与requestDate相同） */
    private LocalDate actualDate;

    public SourceHotResult() {}

    public SourceHotResult(String source, LocalDate requestDate, List<News> list,
                           boolean fallback, LocalDate actualDate) {
        this.source = source;
        this.requestDate = requestDate;
        this.list = list;
        this.fallback = fallback;
        this.actualDate = actualDate;
    }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    /** 是否为全站聚合模式（source 为空） */
    public boolean isAllSources() {
        return source == null || source.trim().isEmpty();
    }

    public LocalDate getRequestDate() { return requestDate; }
    public void setRequestDate(LocalDate requestDate) { this.requestDate = requestDate; }

    public List<News> getList() { return list; }
    public void setList(List<News> list) { this.list = list; }

    public boolean isFallback() { return fallback; }
    public void setFallback(boolean fallback) { this.fallback = fallback; }

    public LocalDate getActualDate() { return actualDate; }
    public void setActualDate(LocalDate actualDate) { this.actualDate = actualDate; }
}
