package com.newssystem.interceptor;

import com.newssystem.entity.Admin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@Component
public class LoginInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LoginInterceptor.class);

    public static final String SESSION_ADMIN_KEY = "LOGIN_ADMIN";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession();
        Admin admin = (Admin) session.getAttribute(SESSION_ADMIN_KEY);
        if (admin == null) {
            log.debug("用户未登录，拦截请求：{}", request.getRequestURI());

            String xrw = request.getHeader("X-Requested-With");
            if ("XMLHttpRequest".equalsIgnoreCase(xrw)) {
                response.setContentType("application/json;charset=UTF-8");
                response.setStatus(401);
                response.getWriter().write("{\"code\":401,\"msg\":\"请先登录\",\"data\":null}");
                return false;
            }
            response.sendRedirect(request.getContextPath() + "/login");
            return false;
        }
        return true;
    }
}
