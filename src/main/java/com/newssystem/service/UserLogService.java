package com.newssystem.service;

import com.newssystem.entity.UserLog;

import java.util.List;

/**
 * 用户行为日志业务接口
 *
 * @author NewsSystem Dev
 */
public interface UserLogService {

    /** 行为类型常量（与 user_log.action 取值保持一致） */
    String ACTION_REGISTER = "注册";
    String ACTION_LOGIN = "登录";
    String ACTION_LOGOUT = "退出";
    String ACTION_PROFILE = "修改个人信息";
    /** 浏览新闻：用户点击新闻标题查看原文 */
    String ACTION_VIEW_NEWS = "浏览新闻";

    /**
     * 记录一条用户行为日志（操作IP自动从当前请求中获取）。
     * 日志写入失败不影响主业务流程，内部已吞掉异常。
     *
     * @param userId   用户ID
     * @param username 用户名
     * @param action   行为类型（建议使用本接口的ACTION_*常量）
     * @param detail   行为详情（可为null）
     */
    void record(Long userId, String username, String action, String detail);

    /**
     * 查询某用户的全部行为日志（按发生时间倒序，最多返回limit条）
     */
    List<UserLog> listByUser(Long userId, int limit);
}
