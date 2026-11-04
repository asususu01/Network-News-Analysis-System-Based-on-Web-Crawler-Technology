package com.newssystem.config;

import com.newssystem.interceptor.LoginInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置类
 * 配置登录拦截器、静态资源、默认跳转页等
 *
 * @author NewsSystem Dev
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private LoginInterceptor loginInterceptor;

    /**
     * 注册登录拦截器
     * 规则：除登录页、静态资源、登录接口外，其余都必须登录
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                // 拦截所有请求
                .addPathPatterns("/**")
                // 放行登录页 & 登录接口
                .excludePathPatterns("/login", "/doLogin", "/admin/login", "/admin/doLogin")
                // 放行用户端（游客免登录的新闻浏览页面 + 注册/登录接口）
                .excludePathPatterns("/client", "/client/**",
                        "/user/register", "/user/doRegister",
                        "/user/login", "/user/doLogin", "/user/logout", "/user/profile")
                // 放行用户端数据分析页所需的只读统计接口（图表数据公开只读，管理端页面本身仍需登录）
                .excludePathPatterns("/analysis/countBy*")
                // 放行新增的只读分析接口：情感走势、热词爆发榜、事件簇、实体关系网络
                .excludePathPatterns("/analysis/sentimentTrend", "/analysis/hotWords",
                        "/analysis/burstWords", "/analysis/clusters",
                        "/analysis/cluster/stats", "/analysis/cluster/detail/*",
                        "/analysis/entityGraph")
                // 放行静态资源（含本地化的第三方库 /lib/**：Bootstrap、Icons、jQuery、ECharts）
                .excludePathPatterns("/static/**", "/css/**", "/js/**", "/images/**", "/lib/**", "/favicon.ico")
                // 放行错误页面
                .excludePathPatterns("/error");
    }

    /**
     * 静态资源映射
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");
    }

    /**
     * 默认首页：访问 / 跳转到 /index（新闻列表页）
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index");
    }
}
