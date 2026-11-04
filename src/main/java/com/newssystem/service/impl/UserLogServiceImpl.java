package com.newssystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.newssystem.entity.UserLog;
import com.newssystem.mapper.UserLogMapper;
import com.newssystem.service.UserLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户行为日志业务实现。
 * 所有日志写入均做异常兜底——日志失败绝不能阻断注册/登录等主流程。
 *
 * @author NewsSystem Dev
 */
@Service
public class UserLogServiceImpl implements UserLogService {

    private static final Logger log = LoggerFactory.getLogger(UserLogServiceImpl.class);

    /** 单个用户日志最大查询条数 */
    private static final int MAX_LOG_LIMIT = 200;

    @Autowired
    private UserLogMapper userLogMapper;

    @Override
    public void record(Long userId, String username, String action, String detail) {
        if (userId == null || username == null || action == null) {
            return;
        }
        try {
            UserLog userLog = new UserLog();
            userLog.setUserId(userId);
            userLog.setUsername(username);
            userLog.setAction(action);
            // 详情过长截断，避免超过字段长度导致入库失败
            if (detail != null && detail.length() > 250) {
                detail = detail.substring(0, 250);
            }
            userLog.setDetail(detail);
            userLog.setIp(resolveClientIp());
            userLog.setCreateTime(LocalDateTime.now());
            userLogMapper.insert(userLog);
        } catch (Exception e) {
            // 日志记录失败只打印警告，不影响用户主操作
            log.warn("用户行为日志写入失败：userId={}, action={}, err={}",
                    userId, action, e.getMessage());
        }
    }

    @Override
    public List<UserLog> listByUser(Long userId, int limit) {
        if (userId == null) {
            return java.util.Collections.emptyList();
        }
        int safeLimit = Math.max(1, Math.min(limit, MAX_LOG_LIMIT));
        QueryWrapper<UserLog> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).orderByDesc("create_time").orderByDesc("id");
        qw.last("LIMIT " + safeLimit);
        List<UserLog> list = userLogMapper.selectList(qw);
        return list == null ? java.util.Collections.emptyList() : list;
    }

    /**
     * 从当前请求线程上下文获取客户端真实IP。
     * 优先取反向代理透传头 X-Forwarded-For 的首个地址；无请求上下文（如异步线程）时返回null。
     */
    private String resolveClientIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return null;
            }
            HttpServletRequest request = attrs.getRequest();
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.trim().isEmpty() && !"unknown".equalsIgnoreCase(xff.trim())) {
                // 多级代理时第一个为客户端原始IP
                return xff.split(",")[0].trim();
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.trim().isEmpty()) {
                return realIp.trim();
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }
}
