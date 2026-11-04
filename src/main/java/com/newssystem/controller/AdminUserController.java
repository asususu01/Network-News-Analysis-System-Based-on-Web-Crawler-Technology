package com.newssystem.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.newssystem.common.Result;
import com.newssystem.entity.User;
import com.newssystem.entity.UserLog;
import com.newssystem.mapper.UserMapper;
import com.newssystem.service.UserLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * 管理端"用户管理"板块控制器。
 * 路径 /admin/users 受 {@link com.newssystem.interceptor.LoginInterceptor}
 * 统一登录拦截保护（未登录管理员会被重定向到登录页/AJAX返回401）。
 *
 * @author NewsSystem Dev
 */
@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserLogService userLogService;

    /**
     * 用户管理列表页：展示全部已注册用户的信息（密码等敏感字段不返回页面）
     */
    @GetMapping
    public String userManagePage(Model model) {
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.orderByAsc("id");
        List<User> users = userMapper.selectList(qw);
        // 清除密码哈希，敏感信息不下发页面
        if (users != null) {
            users.forEach(u -> u.setPassword(null));
        }
        model.addAttribute("users", users);
        model.addAttribute("userCount", users == null ? 0 : users.size());
        return "user_manage";
    }

    /**
     * 查询指定用户的行为日志（点击用户名时AJAX调用，返回JSON）
     *
     * @param userId 用户ID
     */
    @GetMapping("/logs")
    @ResponseBody
    public Result<List<UserLog>> userLogs(@RequestParam Long userId) {
        if (userId == null || userId <= 0) {
            return Result.fail("用户ID不合法");
        }
        List<UserLog> logs = userLogService.listByUser(userId, 200);
        return Result.success(logs);
    }
}
