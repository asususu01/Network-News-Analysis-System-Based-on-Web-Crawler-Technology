package com.newssystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.newssystem.entity.User;
import com.newssystem.mapper.UserMapper;
import com.newssystem.service.UserLogService;
import com.newssystem.service.UserService;
import com.newssystem.util.DateUtil;
import com.newssystem.util.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 用户业务实现
 *
 * @author NewsSystem Dev
 */
@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    /** 会话中保存登录用户的 key */
    public static final String SESSION_USER_KEY = "LOGIN_USER";

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserLogService userLogService;

    @Override
    public boolean register(String username, String password, Integer age, Integer gender, String occupation) {
        if (username == null || username.trim().isEmpty()
                || password == null || password.trim().isEmpty()) {
            return false;
        }
        String name = username.trim();
        // 用户名唯一校验
        if (getByUsername(name) != null) {
            log.warn("注册失败：用户名已存在，username={}", name);
            return false;
        }
        User user = new User();
        user.setUsername(name);
        user.setPassword(HashUtil.md5(password.trim()));
        user.setAge(age);
        user.setGender(gender == null ? 0 : gender);
        user.setOccupation((occupation == null || occupation.trim().isEmpty()) ? null : occupation.trim());
        int rows = userMapper.insert(user);
        log.info("用户注册成功：username={}, rows={}", name, rows);
        if (rows > 0) {
            // 记录用户行为日志：注册
            userLogService.record(user.getId(), name,
                    UserLogService.ACTION_REGISTER, "新用户注册成功");
        }
        return rows > 0;
    }

    @Override
    public boolean login(String username, String password, HttpSession session) {
        if (username == null || password == null
                || username.trim().isEmpty() || password.trim().isEmpty()) {
            return false;
        }
        User user = getByUsername(username.trim());
        if (user == null) {
            log.warn("用户登录失败：账号不存在，username={}", username);
            return false;
        }
        String pwdMd5 = HashUtil.md5(password.trim());
        if (!pwdMd5.equalsIgnoreCase(user.getPassword())) {
            log.warn("用户登录失败：密码错误，username={}", username);
            return false;
        }
        user.setPassword(null);
        session.setAttribute(SESSION_USER_KEY, user);
        log.info("用户登录成功：username={}", user.getUsername());
        // 记录用户行为日志：登录
        userLogService.record(user.getId(), user.getUsername(),
                UserLogService.ACTION_LOGIN, "用户登录成功");
        return true;
    }

    @Override
    public boolean updateProfile(Long userId, Integer age, Integer gender, String occupation, String newPassword) {
        if (userId == null) return false;
        User user = userMapper.selectById(userId);
        if (user == null) return false;
        user.setAge(age);
        user.setGender(gender == null ? 0 : gender);
        user.setOccupation((occupation == null || occupation.trim().isEmpty()) ? null : occupation.trim());
        // 新密码非空才更新密码
        if (newPassword != null && !newPassword.trim().isEmpty()) {
            user.setPassword(HashUtil.md5(newPassword.trim()));
        }
        int rows = userMapper.updateById(user);
        boolean changePwd = newPassword != null && !newPassword.trim().isEmpty();
        log.info("用户 {} 修改个人信息：age={}, gender={}, occupation={}, changePwd={}",
                user.getUsername(), age, gender, occupation, changePwd);
        if (rows > 0) {
            // 记录用户行为日志：修改个人信息（注明是否同时修改了密码）
            String detail = "修改个人信息（年龄/性别/职业" + (changePwd ? "，并修改了密码" : "") + "）";
            userLogService.record(user.getId(), user.getUsername(),
                    UserLogService.ACTION_PROFILE, detail);
        }
        return rows > 0;
    }

    @Override
    public User getByUsername(String username) {
        if (username == null || username.trim().isEmpty()) return null;
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq("username", username.trim());
        return userMapper.selectOne(qw);
    }

    @Override
    public long countTotal() {
        return userMapper.countTotal();
    }

    @Override
    public List<Map<String, Object>> countByGender() {
        List<Map<String, Object>> list = userMapper.countByGender();
        return list == null ? new ArrayList<>() : list;
    }

    @Override
    public List<Map<String, Object>> countByOccupation(int topN) {
        int n = Math.max(1, Math.min(topN, 50));
        List<Map<String, Object>> list = userMapper.countByOccupation(n);
        return list == null ? new ArrayList<>() : list;
    }

    @Override
    public List<Map<String, Object>> countByAgeGroup() {
        List<Map<String, Object>> list = userMapper.countByAgeGroup();
        return list == null ? new ArrayList<>() : list;
    }

    @Override
    public List<Map<String, Object>> countByRegisterDate(int recentDays) {
        int days = Math.max(1, recentDays);
        LocalDateTime start = DateUtil.today().minusDays(days - 1).atStartOfDay();
        List<Map<String, Object>> list = userMapper.countByRegisterDate(start);
        return list == null ? new ArrayList<>() : list;
    }
}
