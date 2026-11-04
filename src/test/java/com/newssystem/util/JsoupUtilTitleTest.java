package com.newssystem.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link JsoupUtil#extractTitle(Document)} 标题提取与站点后缀清理测试。
 *
 * @author NewsSystem Dev
 */
class JsoupUtilTitleTest {

    private Document html(String head, String body) {
        return Jsoup.parse("<html><head>" + head + "</head><body>" + body + "</body></html>");
    }

    @Test
    @DisplayName("og:title中的站点后缀应被去除")
    void testStripSuffixFromOgTitle() {
        Document doc = html(
                "<meta property=\"og:title\" content=\"超500万中国学生的身体，正在悄悄变“弯”_手机网易网\">",
                "<h1>其他标题</h1>");
        assertEquals("超500万中国学生的身体，正在悄悄变“弯”", JsoupUtil.extractTitle(doc));
    }

    @Test
    @DisplayName("title标签中的搜狐/新浪后缀应被去除")
    void testStripSuffixFromTitleTag() {
        Document doc = html("<title>吴彦祖再创业：这次是家具 - 搜狐新闻</title>", "<p>正文</p>");
        assertEquals("吴彦祖再创业：这次是家具", JsoupUtil.extractTitle(doc));

        Document doc2 = html("<title>某条新闻_新浪财经</title>", "<p>正文</p>");
        assertEquals("某条新闻", JsoupUtil.extractTitle(doc2));
    }

    @Test
    @DisplayName("正文中的破折号内容不得被误删（仅清理结尾站点名）")
    void testKeepMeaningfulDash() {
        Document doc = html("<title>看图学习·向新之翼丨这些工程重器“上新”了</title>", "");
        assertEquals("看图学习·向新之翼丨这些工程重器“上新”了", JsoupUtil.extractTitle(doc));
    }
}
