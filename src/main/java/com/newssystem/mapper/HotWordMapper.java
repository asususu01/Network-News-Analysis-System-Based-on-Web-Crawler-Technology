package com.newssystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.newssystem.entity.HotWord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 热词 Mapper 接口
 *
 * @author NewsSystem Dev
 */
@Mapper
public interface HotWordMapper extends BaseMapper<HotWord> {

    /**
     * 查询指定日期范围内的TopN热词（按频率倒序）
     *
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @param topN      前N名
     */
    @Select("SELECT word, SUM(frequency) AS frequency FROM hot_word " +
            "WHERE stat_date BETWEEN #{startDate} AND #{endDate} " +
            "GROUP BY word ORDER BY frequency DESC LIMIT #{topN}")
    List<HotWord> selectTopHotWords(@Param("startDate") LocalDate startDate,
                                     @Param("endDate") LocalDate endDate,
                                     @Param("topN") Integer topN);

    /**
     * 爆发词：以最近一个完整自然日为统计日，与其前若干天的日均频次对比，
     * 频次达到基期均值 2 倍以上且基期均值不低于 minBase 的词。
     *
     * 说明：早期实现是「今天 vs 昨天」，但 hot_word 按天聚合、当天的行只覆盖到分析执行时刻，
     * 半天数据永远翻不过昨天的全天数据，导致榜单恒为空。改为两个完整日窗口对比后结果稳定。
     *
     * @param statDate 统计日（最近一个完整自然日）
     * @param baseStart 基期开始日期（统计日之前第 7 天）
     * @param baseEnd   基期结束日期（统计日前一天）
     * @param minBase   基期日均最低频次，过滤偶然出现的小词
     * @param minDays   基期至少要有几天的数据，避免仅凭 1 天基线误判
     * @param topN      返回条数
     */
    @Select("SELECT t.word AS word, t.f AS frequency, ROUND(AVG(y.f), 1) AS baseFreq, " +
            "       ROUND((t.f - AVG(y.f)) / GREATEST(AVG(y.f), 1) * 100) AS growth " +
            "FROM (SELECT word, SUM(frequency) f FROM hot_word " +
            "      WHERE stat_date = #{statDate} GROUP BY word) t " +
            "JOIN (SELECT word, stat_date, SUM(frequency) f FROM hot_word " +
            "      WHERE stat_date BETWEEN #{baseStart} AND #{baseEnd} " +
            "      GROUP BY word, stat_date) y ON t.word = y.word " +
            "GROUP BY t.word, t.f " +
            "HAVING COUNT(y.f) >= #{minDays} AND AVG(y.f) >= #{minBase} AND t.f > AVG(y.f) * 2 " +
            "ORDER BY growth DESC LIMIT #{topN}")
    List<Map<String, Object>> selectBurstWords(@Param("statDate") LocalDate statDate,
                                               @Param("baseStart") LocalDate baseStart,
                                               @Param("baseEnd") LocalDate baseEnd,
                                               @Param("minBase") int minBase,
                                               @Param("minDays") int minDays,
                                               @Param("topN") int topN);
}
