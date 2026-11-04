package com.newssystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.newssystem.entity.EventCluster;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 事件簇 Mapper 接口
 *
 * @author NewsSystem Dev
 */
@Mapper
public interface EventClusterMapper extends BaseMapper<EventCluster> {

    /**
     * 簇列表：按报道数倒序，仅取最近N天有更新的簇。
     *
     * @param minNewsCount 最少报道数（传 2 表示只看多源报道；null 表示不过滤）
     */
    @Select("<script>SELECT * FROM event_cluster WHERE last_time &gt;= #{since} " +
            "<if test='minNewsCount != null'> AND news_count &gt;= #{minNewsCount} </if>" +
            "ORDER BY news_count DESC, last_time DESC LIMIT #{limit}</script>")
    List<EventCluster> selectRecentClusters(@Param("since") LocalDateTime since,
                                            @Param("minNewsCount") Integer minNewsCount,
                                            @Param("limit") int limit);

    /**
     * 聚类统计口径：区间内簇总数 / 多源簇数 / 覆盖新闻数。
     * 单独出接口，避免前端拿"当前页"累加当成总量。
     */
    @Select("SELECT COUNT(*) AS cluster_total, " +
            "COALESCE(SUM(news_count >= 2), 0) AS multi_total, " +
            "COALESCE(SUM(news_count), 0) AS news_total " +
            "FROM event_cluster WHERE last_time >= #{since}")
    Map<String, Object> selectClusterStats(@Param("since") LocalDateTime since);

    /**
     * 按簇内实际新闻重新统计簇指标（报道数/媒体数/平均情感/首末时间）。
     * 用聚合子查询而非增量计算，避免删除新闻后统计值失真。
     */
    @Update("UPDATE event_cluster ec SET " +
            "news_count = (SELECT COUNT(*) FROM news n WHERE n.cluster_id = ec.id), " +
            "source_count = (SELECT COUNT(DISTINCT n.source) FROM news n WHERE n.cluster_id = ec.id), " +
            "sentiment_avg = (SELECT ROUND(AVG(n.sentiment_score)) FROM news n WHERE n.cluster_id = ec.id), " +
            "first_time = (SELECT MIN(COALESCE(n.publish_time, n.create_time)) FROM news n WHERE n.cluster_id = ec.id), " +
            "last_time = (SELECT MAX(COALESCE(n.publish_time, n.create_time)) FROM news n WHERE n.cluster_id = ec.id) " +
            "WHERE ec.id = #{clusterId}")
    int refreshStats(@Param("clusterId") Long clusterId);

    /**
     * 清理没有任何新闻的空簇
     */
    @Update("DELETE FROM event_cluster WHERE id NOT IN (SELECT cluster_id FROM news WHERE cluster_id IS NOT NULL)")
    int deleteEmptyClusters();
}
