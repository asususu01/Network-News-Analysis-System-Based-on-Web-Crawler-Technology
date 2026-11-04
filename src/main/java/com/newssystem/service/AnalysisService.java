package com.newssystem.service;

import com.newssystem.entity.EventCluster;
import com.newssystem.entity.HotWord;
import com.newssystem.entity.News;

import java.util.List;
import java.util.Map;

/**
 * 数据分析业务接口
 *
 * @author NewsSystem Dev
 */
public interface AnalysisService {

    /**
     * 查询最近N天TopN热词
     *
     * @param recentDays 最近几天
     * @param topN       前N名
     */
    List<HotWord> getTopHotWords(int recentDays, int topN);

    /**
     * 手动触发热词分析
     *
     * @param recentDays 分析最近几天
     * @return 入库条数
     */
    int doHotWordAnalysis(int recentDays);

    /**
     * 按来源统计新闻数量（最近N天）
     *
     * @param recentDays 最近几天
     * @return [{name:"新浪新闻", value:123}, ...]
     */
    List<Map<String, Object>> countBySource(int recentDays);

    /**
     * 按新闻分类统计新闻数量（最近N天）
     *
     * @return [{name:"财经", value:12}, ...] 按数量倒序
     */
    List<Map<String, Object>> countByCategory(int recentDays);

    /**
     * 按日期统计新闻数量（最近N天）
     *
     * @return [{statDate:"2025-01-01", newsCount:50}, ...]
     */
    List<Map<String, Object>> countByDate(int recentDays);

    /**
     * 按情感统计新闻数量（最近N天）
     *
     * @return [{name:"正面", value:xxx}, ...]
     */
    List<Map<String, Object>> countBySentiment(int recentDays);

    /**
     * 情感走势（最近N天）：按天统计平均情感强度与报道量
     *
     * @return [{statDate:"10-24", avgScore:12.5, newsCount:20}, ...]
     */
    List<Map<String, Object>> sentimentTrend(int recentDays);

    /**
     * 热词爆发榜：今日相对昨日频次增长100%以上的词
     *
     * @param topN 返回条数
     */
    List<Map<String, Object>> getBurstWords(int topN);

    /**
     * 事件簇列表（最近 N 天有更新的簇，按报道数倒序）
     *
     * @param minNewsCount 最少报道数，null 表示不过滤；传 2 即只看多源报道
     */
    List<EventCluster> listClusters(int recentDays, int limit, Integer minNewsCount);

    /** 聚类统计口径：{cluster_total, multi_total, news_total} */
    Map<String, Object> clusterStats(int recentDays);

    /**
     * 某事件簇内的全部报道（多源对比）
     */
    List<News> listClusterNews(Long clusterId);

    /**
     * 实体热度榜数据（原实体关系网络改造）。
     * {items:[{name,type,cnt}], pairs:[{source,target,weight}],
     *  total_entity:N, news_with_entity:M}
     */
    Map<String, Object> entityGraph(int recentDays);

    /** 用户总数 */
    long countUsersTotal();

    /** 用户性别分布：[{name:"男", value:N}, ...] */
    List<Map<String, Object>> countUsersByGender();

    /** 用户职业分布 Top N */
    List<Map<String, Object>> countUsersByOccupation(int topN);

    /** 用户年龄段分布 */
    List<Map<String, Object>> countUsersByAgeGroup();

    /** 用户注册趋势（最近N天） */
    List<Map<String, Object>> countUsersByRegisterDate(int recentDays);
}
