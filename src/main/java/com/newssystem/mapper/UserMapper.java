package com.newssystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.newssystem.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 用户 Mapper 接口
 *
 * @author NewsSystem Dev
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /** 按性别统计用户数量（用于管理端用户性别分布饼图） */
    @Select("SELECT CASE gender WHEN 1 THEN '男' WHEN 2 THEN '女' ELSE '未知' END AS name, " +
            "COUNT(*) AS value FROM user GROUP BY gender ORDER BY value DESC")
    List<Map<String, Object>> countByGender();

    /** 按职业统计用户数量（Top N，用于职业分布柱状/饼图） */
    @Select("SELECT COALESCE(NULLIF(occupation,''), '未填写') AS name, COUNT(*) AS value " +
            "FROM user GROUP BY occupation ORDER BY value DESC LIMIT #{topN}")
    List<Map<String, Object>> countByOccupation(@Param("topN") int topN);

    /**
     * 按年龄段统计用户数量（用于年龄分布柱状图）。
     * 区间：<18 / 18-25 / 26-35 / 36-45 / 46-60 / >60
     */
    @Select("SELECT CASE " +
            "   WHEN age IS NULL THEN '未知' " +
            "   WHEN age < 18 THEN '<18' " +
            "   WHEN age BETWEEN 18 AND 25 THEN '18-25' " +
            "   WHEN age BETWEEN 26 AND 35 THEN '26-35' " +
            "   WHEN age BETWEEN 36 AND 45 THEN '36-45' " +
            "   WHEN age BETWEEN 46 AND 60 THEN '46-60' " +
            "   ELSE '>60' END AS name, " +
            "COUNT(*) AS value FROM user GROUP BY name ORDER BY MIN(COALESCE(age,-1))")
    List<Map<String, Object>> countByAgeGroup();

    /**
     * 按注册日期统计用户数量（最近N天，用于注册趋势折线/柱状图）。
     *
     * @param startTime 统计起始时间（含）
     */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS statDate, COUNT(*) AS userCount " +
            "FROM user WHERE create_time >= #{startTime} " +
            "GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d') ORDER BY statDate ASC")
    List<Map<String, Object>> countByRegisterDate(@Param("startTime") LocalDateTime startTime);

    /** 用户总数（用于管理端概览） */
    @Select("SELECT COUNT(*) FROM user")
    long countTotal();
}
