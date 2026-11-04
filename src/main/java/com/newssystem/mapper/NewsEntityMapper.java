package com.newssystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.newssystem.entity.NewsEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 新闻实体 Mapper 接口
 *
 * @author NewsSystem Dev
 */
@Mapper
public interface NewsEntityMapper extends BaseMapper<NewsEntity> {

    /**
     * 实体共现关系：同一篇新闻中共同出现的实体对，按共现次数倒序。
     * 自连接即时计算，不单独维护关系表（数据量小，省一张表的维护成本）。
     *
     * @param since 只统计最近N天入库新闻的实体
     * @param limit 返回关系数上限
     */
    @Select("SELECT a.name AS source, b.name AS target, COUNT(*) AS weight " +
            "FROM news_entity a " +
            "JOIN news_entity b ON a.url_hash = b.url_hash AND a.name < b.name " +
            "JOIN news n ON n.url_hash = a.url_hash " +
            "WHERE n.create_time >= #{since} " +
            "GROUP BY a.name, b.name HAVING weight >= 2 " +
            "ORDER BY weight DESC LIMIT #{limit}")
    List<Map<String, Object>> selectCooccurrence(@Param("since") LocalDateTime since,
                                                 @Param("limit") int limit);

    /**
     * 实体热度榜：按类型分区各取 Top N。
     * 用 ROW_NUMBER() 窗口函数分区排序，保证人物/机构不被高频地名挤出榜单。
     * （直接 ORDER BY 频次 LIMIT 40 的结果 100% 是地点。）
     *
     * @param since   只统计最近 N 天入库新闻的实体
     * @param perType 每种类型各取多少条
     * @return [{name, type, cnt}, ...] 按 cnt 倒序
     */
    @Select("SELECT name, type, cnt FROM (" +
            "  SELECT e.name AS name, e.type AS type, COUNT(*) AS cnt, " +
            "         ROW_NUMBER() OVER (PARTITION BY e.type ORDER BY COUNT(*) DESC) AS rn " +
            "  FROM news_entity e JOIN news n ON n.url_hash = e.url_hash " +
            "  WHERE n.create_time >= #{since} " +
            "  GROUP BY e.name, e.type" +
            ") t WHERE rn <= #{perType} ORDER BY cnt DESC")
    List<Map<String, Object>> selectTopEntitiesPerType(@Param("since") LocalDateTime since,
                                                       @Param("perType") int perType);

    /**
     * 实体抽取统计口径：不同实体总数 / 有实体标签的新闻数。
     * 用于页面展示"共抽取 N 个实体，覆盖 M 篇新闻"，避免只报 Top N 造成误解。
     */
    @Select("SELECT COUNT(DISTINCT e.name) AS total_entity, " +
            "COUNT(DISTINCT e.url_hash) AS news_with_entity " +
            "FROM news_entity e JOIN news n ON n.url_hash = e.url_hash " +
            "WHERE n.create_time >= #{since}")
    Map<String, Object> selectEntityStats(@Param("since") LocalDateTime since);
}
