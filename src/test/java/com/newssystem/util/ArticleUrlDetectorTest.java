package com.newssystem.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ArticleUrlDetector} 单元测试。
 * 覆盖三站文章链接识别、频道/导航链接排除、跟踪参数规范化。
 *
 * @author NewsSystem Dev
 */
class ArticleUrlDetectorTest {

    @Test
    @DisplayName("新浪：频道doc文章/k站article/移动版detail均识别为文章")
    void testSinaArticles() {
        assertTrue(ArticleUrlDetector.isArticleUrl(
                "https://news.sina.com.cn/gov/xlxw/2026-09-20/doc-inisnhav1505872.shtml"));
        assertTrue(ArticleUrlDetector.isArticleUrl(
                "https://finance.sina.com.cn/jjxw/2026-09-20/doc-abc123456.shtml"));
        assertTrue(ArticleUrlDetector.isArticleUrl(
                "https://k.sina.com.cn/article_1936119834_7366d81a02001mzmu.html"));
        assertTrue(ArticleUrlDetector.isArticleUrl(
                "https://finance.sina.cn/2026-09-20/detail-inisnsst2470560.d.html"));
    }

    @Test
    @DisplayName("网易：dy/article与频道article均识别为文章")
    void testNeteaseArticles() {
        assertTrue(ArticleUrlDetector.isArticleUrl(
                "https://www.163.com/dy/article/L78I7QP90519AKBM.html"));
        assertTrue(ArticleUrlDetector.isArticleUrl(
                "https://www.163.com/news/article/J7H9VQ0001.html"));
        // 带跟踪参数也应识别
        assertTrue(ArticleUrlDetector.isArticleUrl(
                "https://www.163.com/dy/article/L78I7QP90519AKBM.html?clickfrom=w_yw"));
    }

    @Test
    @DisplayName("搜狐：/a/数字_数字 识别为文章")
    void testSohuArticles() {
        assertTrue(ArticleUrlDetector.isArticleUrl(
                "https://www.sohu.com/a/1078605233_121157270"));
        assertTrue(ArticleUrlDetector.isArticleUrl(
                "https://www.sohu.com/a/1077849522_121333745?scm=10001.691"));
    }

    @Test
    @DisplayName("频道首页/导航/账号/专题/搜索等非文章链接必须排除")
    void testNonArticleLinks() {
        assertFalse(ArticleUrlDetector.isArticleUrl("https://news.sina.com.cn/"));
        assertFalse(ArticleUrlDetector.isArticleUrl("https://mil.news.sina.com.cn/"));
        assertFalse(ArticleUrlDetector.isArticleUrl("https://news.sina.com.cn/china/"));
        assertFalse(ArticleUrlDetector.isArticleUrl("https://s.weibo.com/weibo?q=test"));
        assertFalse(ArticleUrlDetector.isArticleUrl("https://www.163.com/"));
        assertFalse(ArticleUrlDetector.isArticleUrl("https://open.163.com/#f=topnav"));
        assertFalse(ArticleUrlDetector.isArticleUrl("https://study.163.com/client/download.htm?from=x"));
        assertFalse(ArticleUrlDetector.isArticleUrl("https://hongcai.163.com/?from=pcsy-button"));
        assertFalse(ArticleUrlDetector.isArticleUrl("http://mp.sohu.com/profile?xpt=AbCdEf"));
        assertFalse(ArticleUrlDetector.isArticleUrl("https://www.sohu.com/subject/322546"));
        assertFalse(ArticleUrlDetector.isArticleUrl("https://news.ifeng.com/shanklist/3-305565-"));
    }

    @Test
    @DisplayName("通用规则：自定义站点的年份路径文章识别（中新网/光明网/央广网等）")
    void testGenericYearPathArticles() {
        // 中国新闻网：/gn/2026/09-25/10678901.shtml
        assertTrue(ArticleUrlDetector.isArticleUrl(
                "https://www.chinanews.com.cn/gn/2026/09-25/10678901.shtml"));
        // 光明网：/2026-09/25/content_37012345.htm
        assertTrue(ArticleUrlDetector.isArticleUrl(
                "https://news.gmw.cn/2026-09/25/content_37012345.htm"));
        // 央广网：/20260925/t20260925_523456789.shtml
        assertTrue(ArticleUrlDetector.isArticleUrl(
                "http://news.cnr.cn/native/gd/20260925/t20260925_523456789.shtml"));
        // 带跟踪参数仍应识别
        assertTrue(ArticleUrlDetector.isArticleUrl(
                "https://www.chinanews.com.cn/sh/2026/09-25/10678901.shtml?from=homepage"));
    }

    @Test
    @DisplayName("通用规则：无扩展名的ID型文章识别（澎湃/环球等）")
    void testGenericIdStyleArticles() {
        // 澎湃：newsDetail_forward_30000000
        assertTrue(ArticleUrlDetector.isArticleUrl(
                "https://www.thepaper.cn/newsDetail_forward_30000000"));
        // 环球：/article/4PukxAbCDe
        assertTrue(ArticleUrlDetector.isArticleUrl(
                "https://world.huanqiu.com/article/4PukxAbCDe"));
        // 普通 /detail/123456 形态
        assertTrue(ArticleUrlDetector.isArticleUrl(
                "https://news.example.com/detail/123456"));
    }

    @Test
    @DisplayName("通用规则：自定义站点的频道页/分页页不得误判为文章")
    void testGenericNonArticleLinks() {
        // 频道首页（目录形式，无扩展名）
        assertFalse(ArticleUrlDetector.isArticleUrl("https://www.chinanews.com.cn/finance/"));
        assertFalse(ArticleUrlDetector.isArticleUrl("https://www.chinanews.com.cn/china/"));
        // 频道落地 index.shtml/index.html（含数字后缀的情况也要排除）
        assertFalse(ArticleUrlDetector.isArticleUrl(
                "https://www.chinanews.com.cn/scroll-news/news1.html"));
        assertFalse(ArticleUrlDetector.isArticleUrl(
                "https://www.example.com/channel/index_2.shtml"));
        // 文章目录入口本身（无ID）不算文章
        assertFalse(ArticleUrlDetector.isArticleUrl("https://news.example.com/article"));
        // 短ID分页（不足4位数字）不按数字ID文章处理
        assertFalse(ArticleUrlDetector.isArticleUrl("https://news.example.com/list/page2.html"));
    }

    @Test
    @DisplayName("规范化：文章URL去除query和锚点，非文章URL原样返回")
    void testNormalize() {
        assertEquals(
                "https://www.163.com/dy/article/L78I7QP90519AKBM.html",
                ArticleUrlDetector.normalizeArticleUrl(
                        "https://www.163.com/dy/article/L78I7QP90519AKBM.html?clickfrom=w_yw"));
        assertEquals(
                "https://www.sohu.com/a/1078605233_121157270",
                ArticleUrlDetector.normalizeArticleUrl(
                        "https://www.sohu.com/a/1078605233_121157270?scm=abc#part1"));
        assertEquals(
                "https://k.sina.com.cn/article_1_2.html",
                ArticleUrlDetector.normalizeArticleUrl(
                        "https://k.sina.com.cn/article_1_2.html?from=local"));
        // 非文章链接原样返回
        assertEquals(
                "https://study.163.com/client/download.htm?from=x",
                ArticleUrlDetector.normalizeArticleUrl(
                        "https://study.163.com/client/download.htm?from=x"));
        assertNull(ArticleUrlDetector.normalizeArticleUrl(null));
    }
}
