package com.newssystem.util;

import java.util.regex.Pattern;

/**
 * 新闻文章URL识别器。
 *
 * 门户页面上的链接混杂着频道首页、导航、账号主页、专题页等非新闻链接，
 * 仅靠"正文长度≥100"会把这些落地页误当新闻入库。本类依据各主流门户文章页
 * 的URL结构特征，判断一个链接是否指向"具体新闻文章"，并用于：
 * 1) 爬虫入队过滤：只把文章链接作为新闻目标（频道链接仅用于扩散一层）；
 * 2) 入库前置校验：非文章形态的URL一律不保存。
 *
 * 已支持站点：新浪(sina.com.cn / sina.cn)、网易(163.com)、搜狐(sohu.com)；
 * 另提供"通用新闻文章规则"兜底，使管理员在爬虫管理页自定义添加的
 * 其他公开新闻网站（中国新闻网/光明网/澎湃/环球网等）也能被正确识别。
 *
 * @author NewsSystem Dev
 */
public final class ArticleUrlDetector {

    private ArticleUrlDetector() {}

    /**
     * 新浪文章：
     * - news/finance 等频道：/c/2026-09-20/doc-inisnhav1505872.shtml
     * - 客户端版(k站)：k.sina.com.cn/article_1936119834_7366d81a02001mzmu.html
     * - 移动版：finance.sina.cn/2026-09-20/detail-inisnsst2470560.d.html
     */
    private static final Pattern SINA_ARTICLE = Pattern.compile(
            "https?://[\\w.-]*sina\\.(?:com\\.cn|cn)/"
                    + "(?:.*?/)?(?:doc-[a-z0-9]{6,}\\.shtml"           // 频道文章
                    + "|article_\\d+_[0-9a-z]+\\.html"                 // k站文章
                    + "|\\d{4}-\\d{2}-\\d{2}/detail-[\\w-]+\\.d\\.html)"); // 移动版文章

    /**
     * 网易文章：
     * - www.163.com/dy/article/L78I7QP90519AKBM.html（订阅号/号外）
     * - www.163.com/news/article/J7H9VQ0001.html（频道文章，允许任意频道前缀）
     */
    private static final Pattern NETEASE_ARTICLE = Pattern.compile(
            "https?://[\\w.-]*163\\.com/(?:[\\w-]+/)*article/[A-Z0-9]{6,}\\.html");

    /**
     * 搜狐文章：www.sohu.com/a/1078605233_121157270（文章ID_作者ID）
     */
    private static final Pattern SOHU_ARTICLE = Pattern.compile(
            "https?://[\\w.-]*sohu\\.com/a/\\d+_\\d+");

    // ==================== 通用新闻文章规则（自定义站点兜底） ====================

    /** 以 .shtml/.html/.htm 结尾（查询串/锚点前），用于判断"文章详情页形态" */
    private static final Pattern HTML_EXT = Pattern.compile(
            "\\.(?:shtml|html?)(?:[?#].*)?$", Pattern.CASE_INSENSITIVE);

    /** 路径中含4位年份目录：/2026/ 或 /2026-09/ 或 /20260925/ 等（新闻文章强特征） */
    private static final Pattern YEAR_IN_PATH = Pattern.compile(
            "/(?:19|20)\\d{2}(?:[-/]\\d{2})?(?:/|[-_])");

    /** .html类文件的文件名中含4位以上连续数字：content_37012345.htm、t20260925_567.shtml */
    private static final Pattern NUMERIC_HTML_FILE = Pattern.compile(
            "/[^/]*\\d{4,}[^/]*\\.(?:shtml|html?)(?:[?#].*)?$", Pattern.CASE_INSENSITIVE);

    /**
     * 无静态扩展名的ID型文章路径（部分新锐媒体）：
     * 澎湃 newsDetail_forward_30000000、环球 /article/4Pukxx、各类 /detail/123 /a/123。
     * 前缀与ID之间必须有明确分隔符（/ _ -），避免把 /article、/news 这类
     * 频道入口（或白名单单字"a"开头的普通单词）误判为文章。
     */
    private static final Pattern ID_STYLE_PATH = Pattern.compile(
            "/(?:article|articles|a|newsDetail|detail|content|doc|view|post)"
                    + "[-_/][^/]*?(?:\\d{4,}|[A-Za-z0-9]{6,})(?:[?#].*)?$");

    /** 频道/站点首页类：index.shtml、index.html、default.html 等，即便含数字也不算文章 */
    private static final Pattern INDEX_PAGE = Pattern.compile(
            "/(?:index|default|main)(?:[_-]?\\d+)?\\.(?:shtml|html?)(?:[?#].*)?$",
            Pattern.CASE_INSENSITIVE);

    /**
     * 判断链接是否为任一已支持站点的新闻文章URL；
     * 三家门户的专用规则优先，不满足时再用通用新闻规则兜底（支持自定义站点）。
     */
    public static boolean isArticleUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        if (SINA_ARTICLE.matcher(url).find()
                || NETEASE_ARTICLE.matcher(url).find()
                || SOHU_ARTICLE.matcher(url).find()) {
            return true;
        }
        // 首页/频道落地页一律排除（index.shtml 等）
        if (INDEX_PAGE.matcher(url).find()) {
            return false;
        }
        // 通用规则一：.shtml/.html 结尾 + 路径含年份（/gn/2026/09-25/xxx.shtml）
        if (HTML_EXT.matcher(url).find() && YEAR_IN_PATH.matcher(url).find()) {
            return true;
        }
        // 通用规则二：.shtml/.html 结尾 + 文件名含长数字ID（content_37012345.htm）
        if (NUMERIC_HTML_FILE.matcher(url).find()) {
            return true;
        }
        // 通用规则三：无扩展名的ID型文章（/newsDetail_forward_30000000、/article/4Pukxx）
        return ID_STYLE_PATH.matcher(url).find();
    }

    /**
     * 规范化文章URL：去除查询参数与锚点。
     * 门户文章链接常带有 ?scm= / ?clickfrom= / ?from= 等跟踪参数，
     * 不影响正文访问，去除后可避免同一文章因参数不同被重复爬取/重复入库。
     * 非文章URL保持原样（频道链接可能依赖参数的情况不做处理）。
     */
    public static String normalizeArticleUrl(String url) {
        if (url == null) {
            return null;
        }
        if (!isArticleUrl(url)) {
            return url;
        }
        int hash = url.indexOf('#');
        if (hash >= 0) {
            url = url.substring(0, hash);
        }
        int query = url.indexOf('?');
        if (query >= 0) {
            url = url.substring(0, query);
        }
        return url;
    }
}
