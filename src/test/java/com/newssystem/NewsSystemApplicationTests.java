package com.newssystem;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * SpringBoot 启动测试类
 * 运行此测试可验证Spring容器能否正常初始化（数据库配置必须正确）
 *
 * @author NewsSystem Dev
 */
@SpringBootTest
class NewsSystemApplicationTests {

    @Test
    void contextLoads() {
        // 空方法：若Spring容器初始化失败，此测试会自动报错
        System.out.println("Spring容器加载成功！");
    }
}
