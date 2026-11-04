package com.newssystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.newssystem.entity.EventCluster;
import com.newssystem.entity.HotWord;
import com.newssystem.entity.News;
import com.newssystem.mapper.EventClusterMapper;
import com.newssystem.mapper.HotWordMapper;
import com.newssystem.mapper.NewsEntityMapper;
import com.newssystem.mapper.NewsMapper;
import com.newssystem.analyzer.HotWordExtractor;
import com.newssystem.service.AnalysisService;
import com.newssystem.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class AnalysisServiceImpl implements AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisServiceImpl.class);

    @Autowired
    private HotWordMapper hotWordMapper;

    @Autowired
    private NewsMapper newsMapper;

    @Autowired
    private HotWordExtractor hotWordExtractor;

    @Autowired
    private EventClusterMapper eventClusterMapper;

    @Autowired
    private NewsEntityMapper newsEntityMapper;

    @Autowired
    private com.newssystem.service.UserService userService;

    @Override
    public List<HotWord> getTopHotWords(int recentDays, int topN) {
        int days = Math.max(1, recentDays);
        int top = Math.max(1, topN);

        LocalDate end = DateUtil.today();
        LocalDate start = end.minusDays(days - 1);
        return orEmpty(hotWordMapper.selectTopHotWords(start, end, top));
    }

    @Override
    public int doHotWordAnalysis(int recentDays) {
        int days = Math.max(1, recentDays);
        return hotWordExtractor.extractRecentDays(days);
    }

    @Override
    public List<Map<String, Object>> countBySource(int recentDays) {
        LocalDateTime[] range = buildDateTimeRange(recentDays);
        return orEmpty(newsMapper.countBySource(range[0], range[1]));
    }

    @Override
    public List<Map<String, Object>> countByCategory(int recentDays) {
        LocalDateTime[] range = buildDateTimeRange(recentDays);
        return orEmpty(newsMapper.countByCategory(range[0], range[1]));
    }

    @Override
    public List<Map<String, Object>> countByDate(int recentDays) {
        return orEmpty(newsMapper.countByDate(sinceDate(recentDays)));
    }

    @Override
    public List<Map<String, Object>> countBySentiment(int recentDays) {
        LocalDateTime[] range = buildDateTimeRange(recentDays);
        List<Map<String, Object>> list = orEmpty(newsMapper.countBySentiment(range[0], range[1]));

        Map<String, Long> cntMap = new HashMap<>();
        cntMap.put("正面", 0L);
        cntMap.put("中性", 0L);
        cntMap.put("负面", 0L);
        for (Map<String, Object> m : list) {
            String name = (String) m.get("name");
            Object val = m.get("value");
            Long v = (val instanceof Number) ? ((Number) val).longValue() : 0L;
            cntMap.put(name, v);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (String k : new String[]{"正面", "中性", "负面"}) {
            Map<String, Object> row = new HashMap<>();
            row.put("name", k);
            row.put("value", cntMap.get(k));
            result.add(row);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> sentimentTrend(int recentDays) {
        return orEmpty(newsMapper.sentimentTrend(sinceDate(recentDays)));
    }

    @Override
    public List<Map<String, Object>> getBurstWords(int topN) {
        // 以最近一个完整自然日为统计日，与其前 7 天的日均频次对比
        // （当天数据只覆盖到分析执行时刻，用半天数据对比全天会永远判定不出"爆发"）
        LocalDate statDate = DateUtil.today().minusDays(1);
        LocalDate baseStart = statDate.minusDays(7);
        LocalDate baseEnd = statDate.minusDays(1);
        return orEmpty(hotWordMapper.selectBurstWords(
                statDate, baseStart, baseEnd, 5, 2, Math.max(1, topN)));
    }

    @Override
    public List<EventCluster> listClusters(int recentDays, int limit, Integer minNewsCount) {
        // 上限保护：防止前端传大数把整表拖出来；前端聚类页一次性展示，取 1000 条足够覆盖统计区间
        int top = Math.min(Math.max(1, limit), 1000);
        return orEmpty(eventClusterMapper.selectRecentClusters(sinceDate(recentDays), minNewsCount, top));
    }

    @Override
    public Map<String, Object> clusterStats(int recentDays) {
        Map<String, Object> stats = eventClusterMapper.selectClusterStats(sinceDate(recentDays));
        return stats == null ? new HashMap<>() : stats;
    }

    @Override
    public List<News> listClusterNews(Long clusterId) {
        if (clusterId == null) {
            return new ArrayList<>();
        }
        QueryWrapper<News> qw = new QueryWrapper<>();
        qw.eq("cluster_id", clusterId);
        // 只查列表展示需要的字段，避免把整篇正文传给前端
        qw.select("id", "title", "url", "source", "category", "publish_time", "create_time",
                "sentiment", "sentiment_score", "hot_score", "summary");
        qw.orderByDesc("publish_time").orderByAsc("id");
        return orEmpty(newsMapper.selectList(qw));
    }

    /**
     * 实体热度榜数据（原"实体关系网络"改造）。
     *
     * 不再输出力导向图的 nodes/links：实测当前语料下 40 个节点 100% 是地点、
     * 82% 的共现边为"地点-地点"，图退化为以"中国"为中心的星形，无法传达有效信息。
     * 改为分类型榜单 + 共现对排行 + 抽取覆盖率，诚实呈现抽取结果。
     */
    @Override
    public Map<String, Object> entityGraph(int recentDays) {
        LocalDateTime since = sinceDate(recentDays);

        // 每类型各取 Top10，前端"全部"视图再截断为 15 条
        List<Map<String, Object>> items = orEmpty(newsEntityMapper.selectTopEntitiesPerType(since, 10));
        List<Map<String, Object>> pairs = orEmpty(newsEntityMapper.selectCooccurrence(since, 10));
        Map<String, Object> stats = newsEntityMapper.selectEntityStats(since);

        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("pairs", pairs);
        result.put("total_entity", stats == null ? 0 : stats.get("total_entity"));
        result.put("news_with_entity", stats == null ? 0 : stats.get("news_with_entity"));
        return result;
    }

    // ========== 用户数据统计（委托 UserService） ==========
    @Override
    public long countUsersTotal() {
        return userService.countTotal();
    }

    @Override
    public List<Map<String, Object>> countUsersByGender() {
        return userService.countByGender();
    }

    @Override
    public List<Map<String, Object>> countUsersByOccupation(int topN) {
        return userService.countByOccupation(topN);
    }

    @Override
    public List<Map<String, Object>> countUsersByAgeGroup() {
        return userService.countByAgeGroup();
    }

    @Override
    public List<Map<String, Object>> countUsersByRegisterDate(int recentDays) {
        return userService.countByRegisterDate(recentDays);
    }

    private LocalDateTime[] buildDateTimeRange(int recentDays) {
        LocalDate today = DateUtil.today();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        LocalDateTime start = today.minusDays(Math.max(1, recentDays) - 1).atStartOfDay();
        return new LocalDateTime[]{start, end};
    }

    /** 统计区间起始时间：最近 N 天（含今天）首日的 00:00 */
    private static LocalDateTime sinceDate(int recentDays) {
        return DateUtil.today().minusDays(Math.max(1, recentDays) - 1).atStartOfDay();
    }

    /** null 集合统一转为空集合，调用方无需判空 */
    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? new ArrayList<>() : list;
    }
}
