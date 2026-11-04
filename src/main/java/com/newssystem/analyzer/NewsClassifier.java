package com.newssystem.analyzer;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 新闻自动分类器。
 *
 * 判定依据（两级策略，优先使用高置信信号）：
 * 1) URL频道特征：三大门户的频道子域名/路径命名非常规范（如 finance.sina / money.163 /
 *    business.sohu → 财经；sports.* → 体育；mil/war → 军事），命中即直接归类；
 * 2) 标题+正文关键词加权计分：标题中命中权重为3，正文前500字命中权重为1，取得分最高的分类；
 * 3) 均未命中时归入默认分类"社会"。
 *
 * 分类体系共10类：财经、体育、科技、娱乐、军事、教育、健康、汽车、国际、社会。
 *
 * @author NewsSystem Dev
 */
@Component
public class NewsClassifier {

    /** 默认分类（无法识别时兜底） */
    public static final String DEFAULT_CATEGORY = "社会";

    /** 全部分类（顺序即同分时的优先级，也用于前端筛选选项） */
    public static final List<String> ALL_CATEGORIES = Arrays.asList(
            "财经", "体育", "科技", "娱乐", "军事",
            "教育", "健康", "汽车", "国际", "社会"
    );

    /**
     * URL频道特征（统一转小写后做包含匹配）。
     * 按 map 迭代顺序匹配，命中第一个即返回——每个token只对应唯一频道，无歧义。
     * 同时匹配子域名与路径（如 mil.news.sina.com.cn、www.163.com/world/）。
     */
    private static final Map<String, String> URL_CHANNEL_MAP = new LinkedHashMap<>();
    static {
        // 财经
        URL_CHANNEL_MAP.put("finance", "财经");
        URL_CHANNEL_MAP.put("money", "财经");
        URL_CHANNEL_MAP.put("stock", "财经");
        URL_CHANNEL_MAP.put("business", "财经");
        URL_CHANNEL_MAP.put("fund", "财经");
        URL_CHANNEL_MAP.put("economy", "财经");
        // 体育
        URL_CHANNEL_MAP.put("sports", "体育");
        // 科技（数码/IT频道）
        URL_CHANNEL_MAP.put("tech", "科技");
        URL_CHANNEL_MAP.put("digi", "科技");
        // 娱乐
        URL_CHANNEL_MAP.put("ent", "娱乐");
        URL_CHANNEL_MAP.put("yule", "娱乐");
        URL_CHANNEL_MAP.put("movie", "娱乐");
        // 军事
        URL_CHANNEL_MAP.put("mil", "军事");
        URL_CHANNEL_MAP.put("war", "军事");
        // 教育
        URL_CHANNEL_MAP.put("edu", "教育");
        URL_CHANNEL_MAP.put("learning", "教育");
        // 健康
        URL_CHANNEL_MAP.put("health", "健康");
        // 汽车
        URL_CHANNEL_MAP.put("auto", "汽车");
        // 国际
        URL_CHANNEL_MAP.put("world", "国际");
        URL_CHANNEL_MAP.put("intl", "国际");
    }

    /**
     * 关键词词典：分类 -> 关键词数组。
     * 词语长度尽量≥2，避免单字误判（如"股"可能出现在"一股寒流"）。
     */
    private static final Map<String, String[]> KEYWORD_MAP = new LinkedHashMap<>();
    static {
        KEYWORD_MAP.put("财经", new String[]{
                "股票", "A股", "美股", "港股", "基金", "经济", "金融", "银行", "利率", "通胀",
                "GDP", "上市", "财报", "营收", "投资", "理财", "汇率", "期货", "债券", "楼市",
                "房价", "央行", "股市", "证券", "保险"});
        KEYWORD_MAP.put("体育", new String[]{
                "比赛", "联赛", "冠军", "球队", "球员", "进球", "足球", "篮球", "奥运", "亚运",
                "世锦赛", "决赛", "执教", "主场", "客场", "赛季", "NBA", "CBA", "夺冠", "对阵"});
        KEYWORD_MAP.put("科技", new String[]{
                "科技", "人工智能", "芯片", "半导体", "手机", "互联网", "软件", "5G", "算法",
                "大数据", "云计算", "量子", "机器人", "大模型", "数码", "智能", "卫星", "航天"});
        KEYWORD_MAP.put("娱乐", new String[]{
                "电影", "票房", "综艺", "明星", "演员", "导演", "歌手", "专辑", "电视剧", "剧集",
                "上映", "主演", "影视", "偶像", "演唱会", "红毯", "艺人"});
        KEYWORD_MAP.put("军事", new String[]{
                "军事", "导弹", "战机", "航母", "武器", "国防", "军演", "海军", "空军", "陆军",
                "坦克", "雷达", "部队", "击落", "舰艇", "军人"});
        KEYWORD_MAP.put("教育", new String[]{
                "教育", "高考", "大学", "学校", "学生", "考试", "招生", "教师", "考研", "留学",
                "中小学", "开学", "毕业", "课堂", "校园"});
        KEYWORD_MAP.put("健康", new String[]{
                "健康", "医院", "医生", "疾病", "疫苗", "养生", "医疗", "患者", "感染", "症状",
                "癌症", "睡眠", "饮食", "营养"});
        KEYWORD_MAP.put("汽车", new String[]{
                "汽车", "车型", "新车", "车企", "轿车", "SUV", "混动", "续航", "车市",
                "4S店", "驾驶", "车市"});
        KEYWORD_MAP.put("国际", new String[]{
                "国际", "外交", "总统", "首相", "联合国", "海外", "全球", "美方", "俄方",
                "中东", "欧洲", "乌克兰", "俄罗斯", "乌克兰", "国际社会"});
    }

    /** 正文参与分类匹配的最大长度（取前N字，长正文分类成本可控） */
    private static final int CONTENT_SCAN_LIMIT = 500;

    /**
     * 对一条新闻进行自动分类。
     *
     * @param url     新闻原始URL（频道域名信号，可为null）
     * @param title   清洗后的标题
     * @param content 清洗后的正文
     * @return 分类名称（ALL_CATEGORIES中的一项，不会返回null）
     */
    public String classify(String url, String title, String content) {
        // 第一级：URL频道特征（高置信，命中即返回）
        String byUrl = classifyByUrl(url);
        if (byUrl != null) {
            return byUrl;
        }

        // 第二级：标题(权重3) + 正文前500字(权重1) 关键词计分
        String titlePart = title == null ? "" : title;
        String contentPart = content == null ? "" :
                (content.length() > CONTENT_SCAN_LIMIT ? content.substring(0, CONTENT_SCAN_LIMIT) : content);

        String best = DEFAULT_CATEGORY;
        int bestScore = 0;
        // 按固定顺序遍历，同分时保留靠前的分类
        for (Map.Entry<String, String[]> entry : KEYWORD_MAP.entrySet()) {
            int score = 0;
            for (String kw : entry.getValue()) {
                score += countOccurrences(titlePart, kw) * 3;
                score += countOccurrences(contentPart, kw);
            }
            if (score > bestScore) {
                bestScore = score;
                best = entry.getKey();
            }
        }
        return best;
    }

    /**
     * 依据URL频道子域名/路径判定分类，无法识别返回null。
     */
    private String classifyByUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        String lower = url.toLowerCase();
        for (Map.Entry<String, String> e : URL_CHANNEL_MAP.entrySet()) {
            if (lower.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    /**
     * 统计 keyword 在 text 中出现的次数（非重叠）。
     * 中文不适用单词边界，直接 indexOf 递进统计即可。
     */
    private int countOccurrences(String text, String keyword) {
        if (text == null || text.isEmpty() || keyword == null || keyword.isEmpty()) {
            return 0;
        }
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(keyword, idx)) >= 0) {
            count++;
            idx += keyword.length();
        }
        return count;
    }
}
