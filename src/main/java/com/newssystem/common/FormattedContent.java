package com.newssystem.common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 爬取新闻正文的"展示层格式化结果"。
 *
 * 背景：爬虫抓取的频道页正文是多种形态的文本聚合流（例如
 * "【No.324】xxx [详细]"、"直播吧:xxx"、"财经 | xxx" 或纯文章段落），
 * 直接整段显示可读性很差。{@code ContentFormatter} 将原始正文清洗、切分为
 * 本对象，模板按 {@link #mode} 选择"条目列表"或"文章段落"视图渲染。
 *
 * 注意：本对象仅用于展示，不会修改 news 表中保存的原始 content。
 *
 * @author NewsSystem Dev
 */
public class FormattedContent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 聚合条目模式：正文由多条新闻标题/短讯拼接而成（频道页常见） */
    public static final String MODE_AGGREGATED = "AGGREGATED";
    /** 文章段落模式：正文为一篇相对完整的文章（详情页常见） */
    public static final String MODE_ARTICLE = "ARTICLE";

    /** 展示模式 */
    private String mode;
    /** 聚合模式下的新闻条目列表 */
    private List<Item> items = new ArrayList<>();
    /** 文章模式下的自然段列表 */
    private List<String> paragraphs = new ArrayList<>();
    /** 清洗后有效内容的总字数（不含空白） */
    private int totalChars;

    public FormattedContent() {}

    public FormattedContent(String mode) {
        this.mode = mode;
    }

    /** 是否无内容可展示 */
    public boolean isEmpty() {
        if (MODE_AGGREGATED.equals(mode)) {
            return items == null || items.isEmpty();
        }
        return paragraphs == null || paragraphs.isEmpty();
    }

    /** 有效条目/段落数量 */
    public int getSectionCount() {
        return MODE_AGGREGATED.equals(mode)
                ? (items == null ? 0 : items.size())
                : (paragraphs == null ? 0 : paragraphs.size());
    }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }
    public List<String> getParagraphs() { return paragraphs; }
    public void setParagraphs(List<String> paragraphs) { this.paragraphs = paragraphs; }
    public int getTotalChars() { return totalChars; }
    public void setTotalChars(int totalChars) { this.totalChars = totalChars; }

    /**
     * 单条新闻条目（聚合模式）
     */
    public static class Item implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 条目前缀来源/栏目（如：直播吧、财经、网易号），可能为空 */
        private String source;
        /** 条目标题正文 */
        private String title;

        public Item() {}

        public Item(String source, String title) {
            this.source = source;
            this.title = title;
        }

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
    }
}
