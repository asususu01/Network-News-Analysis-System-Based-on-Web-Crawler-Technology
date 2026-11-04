package com.newssystem.service;

import com.newssystem.entity.Admin;

import javax.servlet.http.HttpSession;

/**
 * 管理员业务接口
 *
 * @author NewsSystem Dev
 */
public interface AdminService {

    /**
     * 根据用户名查询管理员
     */
    Admin getByUsername(String username);

    /**
     * 登录校验
     *
     * @param username 用户名
     * @param password 明文密码（前端传入）
     * @param session  当前Session
     * @return true=登录成功；false=账号或密码错误
     */
    boolean login(String username, String password, HttpSession session);

    /**
     * 修改密码
     */
    boolean changePassword(Long adminId, String oldPwd, String newPwd);
}
