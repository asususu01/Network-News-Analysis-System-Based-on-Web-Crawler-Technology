package com.newssystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.newssystem.entity.Admin;
import com.newssystem.interceptor.LoginInterceptor;
import com.newssystem.mapper.AdminMapper;
import com.newssystem.service.AdminService;
import com.newssystem.util.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;

@Service
public class AdminServiceImpl implements AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminServiceImpl.class);

    @Autowired
    private AdminMapper adminMapper;

    @Override
    public Admin getByUsername(String username) {
        if (username == null || username.isEmpty()) return null;
        QueryWrapper<Admin> qw = new QueryWrapper<>();
        qw.eq("username", username);
        return adminMapper.selectOne(qw);
    }

    @Override
    public boolean login(String username, String password, HttpSession session) {
        if (username == null || password == null
                || username.trim().isEmpty() || password.trim().isEmpty()) {
            return false;
        }
        Admin admin = getByUsername(username.trim());
        if (admin == null) {
            log.warn("登录失败：账号不存在，username={}", username);
            return false;
        }
        String pwdMd5 = HashUtil.md5(password.trim());
        if (!pwdMd5.equalsIgnoreCase(admin.getPassword())) {
            log.warn("登录失败：密码错误，username={}", username);
            return false;
        }
        admin.setPassword(null);
        session.setAttribute(LoginInterceptor.SESSION_ADMIN_KEY, admin);
        log.info("登录成功：username={}", username);
        return true;
    }

    @Override
    public boolean changePassword(Long adminId, String oldPwd, String newPwd) {
        if (adminId == null || oldPwd == null || newPwd == null) {
            return false;
        }
        Admin admin = adminMapper.selectById(adminId);
        if (admin == null) return false;
        String oldMd5 = HashUtil.md5(oldPwd.trim());
        if (!oldMd5.equalsIgnoreCase(admin.getPassword())) {
            return false;
        }
        admin.setPassword(HashUtil.md5(newPwd.trim()));
        admin.setUpdateTime(LocalDateTime.now());
        return adminMapper.updateById(admin) > 0;
    }
}
