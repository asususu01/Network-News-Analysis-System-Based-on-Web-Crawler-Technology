package com.newssystem.crawler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CrawlerScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(CrawlerScheduledTask.class);

    @Autowired
    private NewsCrawler newsCrawler;

    @Autowired
    private CrawlerTaskManager taskManager;

    @Value("${crawler.scheduled-enabled:false}")
    private boolean scheduledEnabled;

    @Value("${crawler.seed-urls:https://news.sina.com.cn/,https://news.163.com/,https://news.sohu.com/}")
    private List<String> seedUrls;

    /**
     * 定时任务入口
     * 默认Cron在application.yml中配置,每两小时执行一次
     * cron表达式支持从配置文件读取,使用占位符
     */
    @Scheduled(cron = "${crawler.cron:0 0 */2 * * ?}")
    public void scheduledCrawl() {
        if (!scheduledEnabled) {
            log.debug("定时爬虫任务未启用,跳过本次执行");
            return;
        }
        if (taskManager.isRunning()) {
            log.warn("定时爬虫检测到已有任务在运行,跳过本次执行");
            return;
        }
        log.info("定时爬虫任务被触发,准备启动...");
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    newsCrawler.startCrawl(seedUrls, "定时触发");
                } catch (Exception e) {
                    log.error("定时爬虫任务执行异常:", e);
                }
            }
        }, "scheduled-crawler");
        thread.setDaemon(false);
        thread.start();
    }
}
