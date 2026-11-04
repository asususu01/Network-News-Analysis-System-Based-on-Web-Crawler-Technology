package com.newssystem.crawler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link NewsCrawler#resolveSiteKey(String)} 站点归属判定单元测试。
 * 重点覆盖自定义站点的跨子域归一化（www/m/news/politics 等子域必须归属同一站点）。
 *
 * @author NewsSystem Dev
 */
class NewsCrawlerSiteKeyTest {

    @Test
    @DisplayName("已知门户：任意子域均归属同一友好站点名")
    void testKnownPortals() {
        assertEquals("新浪新闻", NewsCrawler.resolveSiteKey("https://news.sina.com.cn/"));
        assertEquals("新浪新闻", NewsCrawler.resolveSiteKey("https://finance.sina.cn/a/b.html"));
        assertEquals("网易新闻", NewsCrawler.resolveSiteKey("https://www.163.com/"));
        assertEquals("搜狐新闻", NewsCrawler.resolveSiteKey("https://business.sohu.com/"));
    }

    @Test
    @DisplayName("自定义站点：不同子域归一化到同一注册主域名（普通后缀）")
    void testCustomSiteSubDomains() {
        // 同一自定义站的 www/任意子域必须得到相同key，否则子域文章链接会被同站过滤丢弃
        String key1 = NewsCrawler.resolveSiteKey("https://www.examplenews.com/");
        String key2 = NewsCrawler.resolveSiteKey("https://news.examplenews.com/article/123");
        String key3 = NewsCrawler.resolveSiteKey("https://m.examplenews.com/a/456");
        assertEquals("examplenews.com", key1);
        assertEquals(key1, key2);
        assertEquals(key1, key3);
    }

    @Test
    @DisplayName("自定义站点：com.cn等双段后缀取最后三段作为主域")
    void testMultiPartSuffix() {
        String key = NewsCrawler.resolveSiteKey("https://www.chinanews.com.cn/");
        assertEquals("chinanews.com.cn", key);
        // 中新网文章链接即便在不同子域也归属同一站点
        assertEquals(key, NewsCrawler.resolveSiteKey("https://finance.chinanews.com.cn/2026/09-25/1.shtml"));
    }
}
