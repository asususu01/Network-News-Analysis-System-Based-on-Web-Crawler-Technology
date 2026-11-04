package com.newssystem.service;

import com.newssystem.entity.User;

import java.util.List;
import java.util.Map;

/**
 * 用户业务接口（用户端注册/登录 + 管理端用户统计）
 *
 * @author NewsSystem Dev
 */
public interface UserService {

    /**
     * 用户注册：用户名唯一，密码MD5加密后入库
     *
     * @param username   用户名（必填）
     * @param password   密码（必填，明文，内部MD5）
     * @param age        年龄（可选）
     * @param gender     性别：1男 2女 0未知
     * @param occupation 职业（可选）
     * @return 注册成功返回 true；用户名已存在/参数非法返回 false
     */
    boolean register(String username, String password, Integer age, Integer gender, String occupation);

    /**
     * 用户登录，成功后将用户对象（密码置空）存入 session
     *
     * @return 登录成功返回 true
     */
    boolean login(String username, String password, javax.servlet.http.HttpSession session);

    /** 根据用户名查询用户 */
    User getByUsername(String username);

    /**
     * 修改个人信息（年龄、性别、职业、密码）。
     * 密码为空表示不修改密码；非空则MD5后更新。
     *
     * @return true=修改成功；false=用户不存在
     */
    boolean updateProfile(Long userId, Integer age, Integer gender, String occupation, String newPassword);

    /** 用户总数 */
    long countTotal();

    /** 按性别统计：[{name:"男", value:N}, ...] */
    List<Map<String, Object>> countByGender();

    /** 按职业统计 Top N */
    List<Map<String, Object>> countByOccupation(int topN);

    /** 按年龄段统计 */
    List<Map<String, Object>> countByAgeGroup();

    /** 按注册日期统计（最近N天） */
    List<Map<String, Object>> countByRegisterDate(int recentDays);
}
