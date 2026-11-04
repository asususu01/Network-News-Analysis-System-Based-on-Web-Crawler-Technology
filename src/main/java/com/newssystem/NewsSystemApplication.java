package com.newssystem;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 基于网络爬虫技术的网络新闻分析系统 - 启动类
 * 计算机毕业设计
 *
 * @author NewsSystem Dev
 */
@SpringBootApplication
@EnableScheduling           // 开启Spring定时任务支持
@MapperScan("com.newssystem.mapper") // 扫描Mapper接口
public class NewsSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(NewsSystemApplication.class, args);
        System.out.println("====================================================");
        System.out.println("  基于网络爬虫技术的网络新闻分析系统 启动成功！");
        System.out.println("  访问地址：http://localhost:8080");
        System.out.println("  默认账号：admin  /  123456");
        System.out.println("====================================================");
    }
}
