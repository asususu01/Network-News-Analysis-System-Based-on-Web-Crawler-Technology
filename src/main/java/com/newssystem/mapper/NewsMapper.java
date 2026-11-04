package com.newssystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.newssystem.entity.News;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 新闻 Mapper 接口
 *
 * @author NewsSystem Dev
 */
@Mapper
public interface NewsMapper extends BaseMapper<News> {

    /**
     * 查询指定时间段内按来源分组统计新闻数量
     * 用于饼图展示新闻来源分布
     */
    @Select("SELECT source AS name, COUNT(*) AS value FROM news " +
            "WHERE create_time BETWEEN #{startTime} AND #{endTime} " +
            "AND source IS NOT NULL AND source != '' " +
            "GROUP BY source ORDER BY value DESC")
    List<Map<String, Object>> countBySource(@Param("startTime") LocalDateTime startTime,
                                            @Param("endTime") LocalDateTime endTime);

    /**
     * 查询指定时间段内按新闻分类分组统计新闻数量
     * 用于数据分析页"新闻类型统计"横向柱状图；分类为空的历史脏数据归入"社会"
     */
    @Select("SELECT COALESCE(NULLIF(category, ''), '社会') AS name, COUNT(*) AS value FROM news " +
            "WHERE create_time BETWEEN #{startTime} AND #{endTime} " +
            "GROUP BY COALESCE(NULLIF(category, ''), '社会') ORDER BY value DESC")
    List<Map<String, Object>> countByCategory(@Param("startTime") LocalDateTime startTime,
                                               @Param("endTime") LocalDateTime endTime);

    /**
     * 按日期分组统计新闻数量（最近N天）
     * 用于折线图或柱状图展示新闻数量变化
     */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS statDate, COUNT(*) AS newsCount " +
            "FROM news WHERE create_time >= #{startTime} " +
            "GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d') " +
            "ORDER BY statDate ASC")
    List<Map<String, Object>> countByDate(@Param("startTime") LocalDateTime startTime);

    /**
     * 统计正面/中性/负面情感数量
     * 用于情感饼图
     */
    @Select("SELECT CASE sentiment " +
            "   WHEN 1 THEN '正面' " +
            "   WHEN -1 THEN '负面' " +
            "   ELSE '中性' END AS name, " +
            "COUNT(*) AS value FROM news " +
            "WHERE create_time BETWEEN #{startTime} AND #{endTime} " +
            "GROUP BY sentiment")
    List<Map<String, Object>> countBySentiment(@Param("startTime") LocalDateTime startTime,
                                                @Param("endTime") LocalDateTime endTime);

    /**
     * 用户端：查询指定日期范围内的热点榜。
     * source 为空时聚合全部新闻网（全站热榜），非空时只看该站点。
     * 全站模式下必须限制返回条数：单日新闻量可达数百条，不限制会导致页面体积失控。
     *
     * @param source 新闻网名称（可为 null/空，表示全站）
     * @param start  当天 00:00:00（含）
     * @param end    次日 00:00:00（不含）
     * @param limit  最多返回条数
     */
    @Select("<script>SELECT * FROM news " +
            "WHERE COALESCE(publish_time, create_time) &gt;= #{start} " +
            "  AND COALESCE(publish_time, create_time) &lt;  #{end} " +
            "<if test='source != null and source != \"\"'> AND source = #{source} </if>" +
            "ORDER BY hot_score DESC, COALESCE(publish_time, create_time) DESC, id DESC " +
            "LIMIT #{limit}</script>")
    List<News> selectHot(@Param("source") String source,
                         @Param("start") LocalDateTime start,
                         @Param("end") LocalDateTime end,
                         @Param("limit") int limit);

    /**
     * 用户端：查询最新的 N 条热点（指定日期无数据时降级使用）。
     * source 为空表示全站；用 &lt;where&gt; 自动处理"没有来源条件"的情况，避免出现 WHERE ORDER BY 语法错误。
     *
     * @param source 新闻网名称（可为 null/空，表示全站）
     * @param limit  最多返回条数
     */
    @Select("<script>SELECT * FROM news " +
            "<where><if test='source != null and source != \"\"'> source = #{source} </if></where> " +
            "ORDER BY hot_score DESC, COALESCE(publish_time, create_time) DESC, id DESC " +
            "LIMIT #{limit}</script>")
    List<News> selectLatestHot(@Param("source") String source,
                               @Param("limit") int limit);

    /**
     * 用户端：查询系统中全部新闻网（来源）名称及其新闻数量
     * 用于用户端首页"新闻网站导航"
     */
    @Select("SELECT source AS source, COUNT(*) AS cnt FROM news " +
            "WHERE source IS NOT NULL AND source != '' " +
            "GROUP BY source ORDER BY cnt DESC")
    List<Map<String, Object>> countSources();

    /**
     * 情感走势：按天统计平均情感强度与报道量，用于双轴折线图
     */
    @Select("SELECT DATE_FORMAT(create_time, '%m-%d') AS statDate, " +
            "       ROUND(AVG(sentiment_score), 1) AS avgScore, " +
            "       COUNT(*) AS newsCount " +
            "FROM news WHERE create_time >= #{startTime} " +
            "GROUP BY DATE_FORMAT(create_time, '%m-%d') ORDER BY statDate ASC")
    List<Map<String, Object>> sentimentTrend(@Param("startTime") LocalDateTime startTime);

    /**
     * 查询最近N天内入库新闻的URL哈希，用于应用启动时预热URL去重集合。
     * 说明：爬虫运行期去重集合只在内存中，重启后会被清空，若不预热会重复抓取已有新闻。
     */
    @Select("SELECT url_hash FROM news WHERE create_time >= #{since}")
    List<String> selectRecentUrlHashes(@Param("since") LocalDateTime since);

    /**
     * 事件聚类候选召回：4段指纹任一相同即入候选集。
     * 鸽笼原理：两个64位指纹汉明距离<=3时，切分为4段16位后至少有一段完全相同，
     * 因此按段等值查询不会漏召回，同时把候选集压到很小的规模。
     */
    @Select("SELECT id, title, sim_hash, cluster_id FROM news " +
            "WHERE create_time >= #{since} AND sim_hash IS NOT NULL " +
            "  AND (sim_seg0 = #{s0} OR sim_seg1 = #{s1} OR sim_seg2 = #{s2} OR sim_seg3 = #{s3}) " +
            "LIMIT 200")
    List<News> selectClusterCandidates(@Param("s0") int s0, @Param("s1") int s1,
                                       @Param("s2") int s2, @Param("s3") int s3,
                                       @Param("since") LocalDateTime since);

    /**
     * 内容级去重：统计同来源、同标题、指定时间之后已入库的新闻数量。
     * URL级去重无法覆盖"同一文章不同URL"的情况（不同栏目页链接可能带不同参数），此处用标题兜底。
     *
     * @param source 新闻来源（必须带来源条件，避免误杀不同媒体转载同一稿件）
     * @param title  清洗后的新闻标题
     * @param since  统计起始时间（通常为当前时间往前24小时）
     */
    @Select("SELECT COUNT(*) FROM news " +
            "WHERE source = #{source} AND title = #{title} " +
            "  AND create_time >= #{since}")
    int countSameTitle(@Param("source") String source,
                       @Param("title") String title,
                       @Param("since") LocalDateTime since);

    /**
     * 新闻入库后回填事件簇ID
     */
    @Update("UPDATE news SET cluster_id = #{clusterId} WHERE id = #{id}")
    int assignClusterId(@Param("id") Long id, @Param("clusterId") Long clusterId);
}
