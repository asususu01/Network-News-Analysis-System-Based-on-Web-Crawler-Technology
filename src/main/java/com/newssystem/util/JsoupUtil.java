package com.newssystem.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class JsoupUtil {

    private static final Logger log = LoggerFactory.getLogger(JsoupUtil.class);

    private JsoupUtil() {}

    public static Document parseHtml(String html, String baseUri) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        try {
            return Jsoup.parse(html, baseUri == null ? "" : baseUri);
        } catch (Exception e) {
            log.error("Jsoup解析HTML失败，原因：{}", e.getMessage());
            return null;
        }
    }

    public static String extractTitle(Document doc) {
        if (doc == null) return null;
        try {
            Element ogTitle = doc.selectFirst("meta[property=og:title]");
            if (ogTitle != null && !ogTitle.attr("content").trim().isEmpty()) {
                return stripSiteSuffix(ogTitle.attr("content").trim());
            }
            Element h1 = doc.selectFirst("h1");
            if (h1 != null && !h1.text().trim().isEmpty()) {
                return stripSiteSuffix(h1.text().trim());
            }
            String title = doc.title();
            return title == null ? null : stripSiteSuffix(title.trim());
        } catch (Exception e) {
            log.warn("提取标题失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 去除标题末尾由门户拼接的站点后缀，例如：
     * "超500万中国学生的身体正在变弯_手机网易网" → "超500万中国学生的身体正在变弯"
     * 仅去除"分隔符 + 已知站点名"结尾的片段，避免误删标题正文中的破折号内容。
     */
    private static String stripSiteSuffix(String title) {
        if (title == null || title.isEmpty()) {
            return title;
        }
        // 分隔符(_ - – |)后紧跟已知门户站点名，且位于字符串末尾
        String suffixRegex = "[_\\-–|]\\s*(手机网易网|手机新浪网|网易新闻|网易网|网易|新浪新闻|新浪网|新浪财经|搜狐新闻|搜狐网|搜狐|腾讯新闻|腾讯网)\\s*$";
        String cleaned = title.replaceFirst(suffixRegex, "").trim();
        // 防止极端情况下标题被清空
        return cleaned.isEmpty() ? title : cleaned;
    }

    public static String extractContent(Document doc) {
        if (doc == null) return null;
        try {
            String[] contentSelectors = {
                    "article",
                    "div.article-content",
                    "div.post-content",
                    "div.content",
                    "div#content",
                    "div.text",
                    "div.detail",
                    "div.news-content",
                    "div.main-content"
            };
            for (String selector : contentSelectors) {
                Element el = doc.selectFirst(selector);
                if (el != null) {
                    String text = el.text().trim();
                    if (text.length() >= 50) {
                        return text;
                    }
                }
            }
            Elements pList = doc.select("p");
            StringBuilder sb = new StringBuilder();
            for (Element p : pList) {
                String text = p.text().trim();
                if (text.length() >= 20) {
                    sb.append(text).append("\n");
                }
            }
            return sb.toString().trim();
        } catch (Exception e) {
            log.warn("提取正文失败：{}", e.getMessage());
            return null;
        }
    }

    public static LocalDateTime extractPublishTime(Document doc) {
        if (doc == null) return null;
        try {
            Element metaTime = doc.selectFirst("meta[property=article:published_time]");
            if (metaTime != null) {
                LocalDateTime dt = DateUtil.parseAny(metaTime.attr("content"));
                if (dt != null) return dt;
            }
            String[] metaNames = {"publishdate", "pubdate", "date", "publish-time"};
            for (String name : metaNames) {
                Element meta = doc.selectFirst("meta[name=" + name + "]");
                if (meta != null) {
                    LocalDateTime dt = DateUtil.parseAny(meta.attr("content"));
                    if (dt != null) return dt;
                }
            }
            String[] classSelectors = {
                    ".pub-time", ".publish-time", ".time", ".date",
                    ".news-time", ".article-time", ".post-time"
            };
            for (String selector : classSelectors) {
                Element el = doc.selectFirst(selector);
                if (el != null) {
                    LocalDateTime dt = DateUtil.parseAny(el.text());
                    if (dt != null) return dt;
                }
            }
        } catch (Exception e) {
            log.warn("提取发布时间失败：{}", e.getMessage());
        }
        return null;
    }

    public static String extractSource(Document doc, String defaultSource) {
        if (doc == null) return defaultSource;
        try {
            Element siteName = doc.selectFirst("meta[property=og:site_name]");
            if (siteName != null && !siteName.attr("content").trim().isEmpty()) {
                return siteName.attr("content").trim();
            }
            Element source = doc.selectFirst("meta[name=source]");
            if (source != null && !source.attr("content").trim().isEmpty()) {
                return source.attr("content").trim();
            }
            Element srcEl = doc.selectFirst(".source, .news-source, .article-source");
            if (srcEl != null && !srcEl.text().trim().isEmpty()) {
                return srcEl.text().trim();
            }
        } catch (Exception e) {
            log.warn("提取来源失败：{}", e.getMessage());
        }
        return defaultSource;
    }

    public static List<String> extractNewsLinks(Document doc, String baseUrl, String keywordRegex) {
        List<String> result = new ArrayList<>();
        if (doc == null) return result;
        Set<String> seen = new HashSet<>();
        try {
            Elements links = doc.select("a[href]");
            for (Element a : links) {
                String href = a.attr("abs:href");
                if (href == null || href.isEmpty()) continue;
                if (seen.contains(href)) continue;
                seen.add(href);
                if (!href.startsWith("http")) continue;
                if (href.matches(".*\\.(jpg|jpeg|png|gif|bmp|webp|mp4|avi|pdf|zip|rar|exe)(\\?.*)?$")) {
                    continue;
                }
                if (keywordRegex != null && !keywordRegex.isEmpty()) {
                    if (href.matches(keywordRegex)) {
                        result.add(href);
                    }
                } else {
                    result.add(href);
                }
            }
        } catch (Exception e) {
            log.warn("提取页面链接失败：{}", e.getMessage());
        }
        return result;
    }
}
