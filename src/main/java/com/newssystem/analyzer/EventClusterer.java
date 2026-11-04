package com.newssystem.analyzer;

import com.newssystem.entity.EventCluster;
import com.newssystem.entity.News;
import com.newssystem.mapper.EventClusterMapper;
import com.newssystem.mapper.NewsMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 事件聚类器（SimHash + 分桶召回）。
 *
 * 流程：
 * 1) 取「标题 + 正文前200字」分词，词频作为权重，计算64位 SimHash 指纹；
 * 2) 将指纹切成4段16位分别建索引，按"任一相同"召回候选集（鸽笼原理保证不漏召回）；
 * 3) 候选集内计算汉明距离，距离 <= 3 判定为同一事件，复用已有簇；
 * 4) 否则新建事件簇。
 *
 * @author NewsSystem Dev
 */
@Component
public class EventClusterer {

    private static final Logger log = LoggerFactory.getLogger(EventClusterer.class);

    /** 汉明距离阈值：<=3 视为同一事件 */
    private static final int HAMMING_THRESHOLD = 3;
    /**
     * 标题词集合 Jaccard 下限：SimHash 通过后仍需语义重合度达标，抑制短标题误聚。
     * 取 0.3 而非 0.4：实测 0.3~0.4 区间均为真实同事件（如"5.3级地震"vs"5.1级左右地震"），
     * 继续降到 0.25 以下会引入"同系列不同事件"误判（如"我们的长征路丨渡船之上"与"我们的长征路丨答卷"）。
     */
    private static final double TITLE_JACCARD_MIN = 0.3;
    /** 候选集时间窗（天），避免与过旧新闻聚成一簇；放宽到 7 天让跨天报道也能合并 */
    private static final int CANDIDATE_DAYS = 7;
    /** 正文参与指纹计算的最大长度 */
    private static final int CONTENT_SNIPPET = 200;

    @Autowired
    private IkWordAnalyzer wordAnalyzer;

    @Autowired
    private NewsMapper newsMapper;

    @Autowired
    private EventClusterMapper clusterMapper;

    /**
     * 计算 SimHash 指纹并回填到 news 对象（入库前调用）。
     *
     * @return 分词词频，供新建簇时提取关键词；文本为空时返回 null
     */
    public Map<String, Integer> fillFingerprint(News news) {
        if (news == null) {
            return null;
        }
        String title = news.getTitle() == null ? "" : news.getTitle();
        String content = news.getContent() == null ? "" : news.getContent();
        String snippet = content.length() > CONTENT_SNIPPET
                ? content.substring(0, CONTENT_SNIPPET) : content;
        // 关键词取自「标题 + 正文前200字」，信息更丰富
        Map<String, Integer> freq = SimHashUtil.wordFrequency(wordAnalyzer.segment(title + " " + snippet));
        // 指纹只取标题：不同来源的正文带有各自的导航/版权模板，混入正文会显著稀释指纹，
        // 导致同一热点（标题相同）的报道汉明距离被拉大而无法聚簇。标题是事件主题最稳定的特征。
        Map<String, Integer> titleFreq = SimHashUtil.wordFrequency(wordAnalyzer.segment(title));
        if (titleFreq.isEmpty()) {
            titleFreq = freq;
        }
        if (titleFreq.isEmpty()) {
            return null;
        }
        long fp = SimHashUtil.simHash(titleFreq);
        news.setSimHash(fp);
        news.setSimSeg0(SimHashUtil.segment(fp, 0));
        news.setSimSeg1(SimHashUtil.segment(fp, 1));
        news.setSimSeg2(SimHashUtil.segment(fp, 2));
        news.setSimSeg3(SimHashUtil.segment(fp, 3));
        return freq;
    }

    /**
     * 为新闻分配事件簇ID（新闻入库后调用，调用方负责回填 cluster_id 并刷新统计）。
     *
     * @param news 已入库且已计算指纹的新闻
     * @param freq 分词词频（新建簇时用于提取关键词，可为null）
     * @return 命中的簇ID；指纹为空时返回null
     */
    public Long assignCluster(News news, Map<String, Integer> freq) {
        if (news == null || news.getSimHash() == null) {
            return null;
        }
        long fp = news.getSimHash();
        List<News> candidates = newsMapper.selectClusterCandidates(
                news.getSimSeg0(), news.getSimSeg1(), news.getSimSeg2(), news.getSimSeg3(),
                LocalDateTime.now().minusDays(CANDIDATE_DAYS));

        if (candidates != null) {
            for (News c : candidates) {
                if (c.getSimHash() == null || c.getClusterId() == null) {
                    continue;
                }
                if (news.getId() != null && news.getId().equals(c.getId())) {
                    continue;
                }
                if (SimHashUtil.hamming(fp, c.getSimHash()) <= HAMMING_THRESHOLD
                        && titleJaccard(news.getTitle(), c.getTitle()) >= TITLE_JACCARD_MIN) {
                    return c.getClusterId();
                }
            }
        }
        return createCluster(news, freq);
    }

    /**
     * 新建事件簇，返回新簇ID
     */
    private Long createCluster(News news, Map<String, Integer> freq) {
        EventCluster c = new EventCluster();
        c.setTitle(news.getTitle());
        c.setKeywords(topKeywords(freq, 5));
        c.setNewsCount(1);
        c.setSourceCount(1);
        c.setSentimentAvg(news.getSentimentScore() == null ? 0 : news.getSentimentScore());
        LocalDateTime t = news.getPublishTime() != null ? news.getPublishTime() : LocalDateTime.now();
        c.setFirstTime(t);
        c.setLastTime(t);
        clusterMapper.insert(c);
        return c.getId();
    }

    /**
     * 按簇内实际新闻重新统计簇指标（报道数/媒体数/平均情感/首末时间）
     */
    public void refreshStats(Long clusterId) {
        if (clusterId == null) {
            return;
        }
        try {
            clusterMapper.refreshStats(clusterId);
        } catch (Exception e) {
            log.error("刷新事件簇统计失败 clusterId={}", clusterId, e);
        }
    }

    /**
     * 清理没有新闻的空簇（爬虫任务结束时调用）
     */
    public void cleanEmptyClusters() {
        try {
            int n = clusterMapper.deleteEmptyClusters();
            if (n > 0) {
                log.info("清理空事件簇{}个", n);
            }
        } catch (Exception e) {
            log.error("清理空事件簇失败", e);
        }
    }

    /**
     * 标题词集合的 Jaccard 相似度 = |交集| / |并集|。
     * 与 SimHash 互补：SimHash 看整体位模式，Jaccard 看具体词重合，
     * 两者同时达标才判定为同一事件。
     */
    private double titleJaccard(String a, String b) {
        Set<String> sa = new HashSet<>(wordAnalyzer.segment(a == null ? "" : a));
        Set<String> sb = new HashSet<>(wordAnalyzer.segment(b == null ? "" : b));
        if (sa.isEmpty() || sb.isEmpty()) {
            return 0;
        }
        int inter = 0;
        for (String w : sa) {
            if (sb.contains(w)) {
                inter++;
            }
        }
        return (double) inter / (sa.size() + sb.size() - inter);
    }

    private String topKeywords(Map<String, Integer> freq, int n) {
        if (freq == null || freq.isEmpty()) {
            return "";
        }
        return freq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(n)
                .map(Map.Entry::getKey)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }
}
