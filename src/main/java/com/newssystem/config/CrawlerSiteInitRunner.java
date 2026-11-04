package com.newssystem.config;

import com.newssystem.entity.CrawlerSite;
import com.newssystem.mapper.CrawlerSiteMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 爬取站点初始化器。
 *
 * crawler_site 表首次为空时，把 application.yml 中 crawler.seed-urls 配置的
 * 种子站点作为"内置站点"写入数据库，保证爬虫管理页下拉列表开箱即用；
 * 此后管理员的增删操作完全以数据库为准，重复启动不重复初始化（幂等）。
 *
 * @author NewsSystem Dev
 */
@Component
@Order(20)
public class CrawlerSiteInitRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CrawlerSiteInitRunner.class);

    @Autowired
    private CrawlerSiteMapper crawlerSiteMapper;

    /** 与 yml 中 crawler.seed-urls 顺序对应的内置站点显示名称 */
    private static final String[] BUILTIN_NAMES = {"新浪新闻", "网易新闻", "搜狐新闻"};

    @Value("${crawler.seed-urls:https://news.sina.com.cn/,https://news.163.com/,https://news.sohu.com/}")
    private List<String> seedUrls;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Long count = crawlerSiteMapper.selectCount(new QueryWrapper<>());
            if (count != null && count > 0) {
                return;
            }
            if (seedUrls == null || seedUrls.isEmpty()) {
                log.warn("crawler.seed-urls 未配置，爬取站点表保持为空，请在爬虫管理页手动添加");
                return;
            }
            for (int i = 0; i < seedUrls.size(); i++) {
                String url = seedUrls.get(i);
                if (url == null || url.trim().isEmpty()) {
                    continue;
                }
                CrawlerSite site = new CrawlerSite();
                // 名称按顺序取，超出预置名称的URL用域名兜底
                site.setName(i < BUILTIN_NAMES.length ? BUILTIN_NAMES[i] : "内置站点" + (i + 1));
                site.setUrl(url.trim());
                site.setIsBuiltin(1);
                site.setCreateTime(LocalDateTime.now());
                site.setUpdateTime(LocalDateTime.now());
                crawlerSiteMapper.insert(site);
            }
            log.info("内置爬取站点初始化完成，共写入{}个站点", seedUrls.size());
        } catch (Exception e) {
            // 初始化失败不阻断应用启动，管理员仍可在页面手动添加站点
            log.error("内置爬取站点初始化异常（不影响系统运行）:", e);
        }
    }
}
