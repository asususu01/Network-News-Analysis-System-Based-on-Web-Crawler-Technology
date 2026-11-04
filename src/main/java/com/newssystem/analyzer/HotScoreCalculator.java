package com.newssystem.analyzer;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 新闻热度打分器。
 *
 * 纯静态页面爬虫拿不到真实阅读量/评论数，因此本系统的"热度"基于公开页面
 * 上可观测的编辑信号进行估算，总分 0~100，由三部分组成：
 *
 * 1) 页面位置分（权重60）：门户首页/频道页的新闻链接按编辑重要性从上到下排列，
 *    链接位置越靠前热度越高；深层页面(depth≥2)的链接可信度递减，乘以深度系数。
 * 2) 标题特征分（权重25）：含突发/重磅/独家/直播/回应等热门词汇、带【】栏目标记、
 *    标题长度在规范区间内，均给予加分。
 * 3) 正文质量分（权重15）：正文越长信息量通常越足（达到500字即满分）。
 *
 * @author NewsSystem Dev
 */
@Component
public class HotScoreCalculator {

    /** 页面位置分满分 */
    private static final int POSITION_MAX = 60;
    /** 位置每靠后一名扣减的分数 */
    private static final int POSITION_STEP = 3;
    /** 位置分保底值（避免首页底部链接得0分） */
    private static final int POSITION_FLOOR = 8;

    /** 标题特征分满分 */
    private static final int TITLE_MAX = 25;
    /** 每个热门词加分（最多计入3个） */
    private static final int HOT_KEYWORD_BONUS = 5;
    /** 热门词最多计几个 */
    private static final int HOT_KEYWORD_MAX_HITS = 3;
    /** 带【】或[]栏目标记的加分 */
    private static final int BRACKET_BONUS = 5;
    /** 规范标题长度的加分 */
    private static final int LENGTH_BONUS = 5;
    /** 规范标题长度区间 */
    private static final int TITLE_LEN_MIN = 10;
    private static final int TITLE_LEN_MAX = 40;

    /** 正文质量分满分 */
    private static final int CONTENT_MAX = 15;

    /** 热门/高关注词汇（命中即加分） */
    private static final String[] HOT_KEYWORDS = {
            "突发", "重磅", "独家", "直播", "回应", "官宣", "速报", "最新",
            "热议", "热搜", "冲上热搜", "爆", "关注", "通报", "辟谣", "发布",
            "会见", "举行", "成功", "突破", "冠军", "夺冠"
    };

    /** 标题中的【栏目】或[专栏]标记 */
    private static final Pattern BRACKET_TAG = Pattern.compile("[【\\[][^】\\]]{1,30}[】\\]]");

    /**
     * 计算一条新闻的热度得分。
     *
     * @param title        新闻标题（清洗后）
     * @param content      新闻正文（清洗后）
     * @param linkPosition 该新闻链接在其父页面同站链接中的序号（0表示最靠前，从0开始）
     * @param depth        该新闻相对种子页的BFS深度（种子页直接子链接为1）
     * @return 热度得分，区间 [0,100]
     */
    public int calculate(String title, String content, int linkPosition, int depth) {
        int score = positionScore(linkPosition, depth)
                + titleScore(title)
                + contentScore(content);
        if (score < 0) {
            score = 0;
        }
        if (score > 100) {
            score = 100;
        }
        return score;
    }

    /**
     * 页面位置分：越靠前越高，深度越深系数越低。
     * depth=1（种子页直接链接）系数1.0；depth=2 系数0.75；depth≥3 系数0.55。
     */
    private int positionScore(int linkPosition, int depth) {
        int pos = Math.max(0, linkPosition);
        int raw = POSITION_MAX - pos * POSITION_STEP;
        if (raw < POSITION_FLOOR) {
            raw = POSITION_FLOOR;
        }
        double factor;
        if (depth <= 1) {
            factor = 1.0;
        } else if (depth == 2) {
            factor = 0.75;
        } else {
            factor = 0.55;
        }
        return (int) Math.round(raw * factor);
    }

    /**
     * 标题特征分：热门词（最多3个） + 栏目标记 + 规范长度。
     */
    private int titleScore(String title) {
        if (title == null || title.isEmpty()) {
            return 0;
        }
        int score = 0;

        int hits = 0;
        for (String kw : HOT_KEYWORDS) {
            if (title.contains(kw)) {
                hits++;
                if (hits >= HOT_KEYWORD_MAX_HITS) {
                    break;
                }
            }
        }
        score += Math.min(hits, HOT_KEYWORD_MAX_HITS) * HOT_KEYWORD_BONUS;

        if (BRACKET_TAG.matcher(title).find()) {
            score += BRACKET_BONUS;
        }

        int len = title.trim().length();
        if (len >= TITLE_LEN_MIN && len <= TITLE_LEN_MAX) {
            score += LENGTH_BONUS;
        }

        return Math.min(score, TITLE_MAX);
    }

    /**
     * 正文质量分：≥500字满分15，≥200字10分，≥100字5分。
     */
    private int contentScore(String content) {
        if (content == null) {
            return 0;
        }
        int len = content.length();
        if (len >= 500) {
            return CONTENT_MAX;
        }
        if (len >= 200) {
            return 10;
        }
        if (len >= 100) {
            return 5;
        }
        return 0;
    }
}
