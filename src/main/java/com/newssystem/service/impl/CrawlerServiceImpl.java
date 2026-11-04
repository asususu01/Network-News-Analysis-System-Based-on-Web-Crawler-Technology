package com.newssystem.service.impl;

import com.newssystem.analyzer.HotWordExtractor;
import com.newssystem.crawler.CrawlerTaskManager;
import com.newssystem.crawler.NewsCrawler;
import com.newssystem.entity.CrawlerSite;
import com.newssystem.service.CrawlerService;
import com.newssystem.service.CrawlerSiteService;
import com.newssystem.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CrawlerServiceImpl implements CrawlerService {

    private static final Logger log = LoggerFactory.getLogger(CrawlerServiceImpl.class);

    @Autowired
    private NewsCrawler newsCrawler;

    @Autowired
    private CrawlerTaskManager taskManager;

    @Autowired
    private HotWordExtractor hotWordExtractor;

    @Autowired
    private CrawlerSiteService crawlerSiteService;

    @Value("${analyzer.auto-analyze-after-crawl:true}")
    private boolean autoAnalyzeAfterCrawl;

    /** 爬虫管理页"爬取数量"下拉允许的选项（最小5条，最大50条） */
    private static final List<Integer> ALLOWED_PER_SITE_COUNTS = Arrays.asList(5, 10, 20, 30, 40, 50);

    /** 配置文件中的默认每站爬取数量（非法入参回退值） */
    @Value("${crawler.max-news-per-site:5}")
    private int defaultMaxPerSite;

    @Override
    public boolean startCrawler(Integer maxPerSite, List<Long> siteIds) {
        if (taskManager.isRunning()) {
            log.warn("手动启动失败：爬虫任务正在运行中");
            return false;
        }

        // 根据页面选中的站点ID加载站点配置；未选择时爬取全部已配置站点
        List<CrawlerSite> sites = crawlerSiteService.listByIds(siteIds);
        if (sites == null || sites.isEmpty()) {
            log.warn("手动启动失败：没有可爬取的站点（站点列表为空或所选站点已被删除）");
            return false;
        }

        // 组装爬虫种子（URL + 管理员配置的站点名称）
        final List<NewsCrawler.Seed> seeds = new ArrayList<>();
        for (CrawlerSite site : sites) {
            if (site.getUrl() != null && !site.getUrl().trim().isEmpty()) {
                seeds.add(new NewsCrawler.Seed(site.getUrl().trim(), site.getName()));
            }
        }
        if (seeds.isEmpty()) {
            log.warn("手动启动失败：所选站点均未配置有效网址");
            return false;
        }

        // 仅接受下拉白名单中的数量（5/10/20/30/40/50），其余回退默认值，防止前端伪造参数
        final int quota = (maxPerSite != null && ALLOWED_PER_SITE_COUNTS.contains(maxPerSite))
                ? maxPerSite : defaultMaxPerSite;
        log.info("手动启动爬虫：选中{}个站点，每站点配额={} 条，站点={}",
                seeds.size(), quota, buildSiteNameText(seeds));

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    newsCrawler.startCrawlWithNames(seeds, "手动触发", quota);

                    if (autoAnalyzeAfterCrawl) {
                        log.info("爬虫结束，自动触发热词分析...");
                        taskManager.appendLog("开始执行热词分析（最近3天）");
                        int cnt = hotWordExtractor.extractRecentDays(3);
                        taskManager.appendLog("热词分析完成，入库热词数量=" + cnt);
                    }
                } catch (Exception e) {
                    log.error("手动爬虫任务异常:", e);
                }
            }
        }, "manual-crawler");
        t.setDaemon(false);
        t.start();
        return true;
    }

    @Override
    public boolean stopCrawler() {
        if (!taskManager.isRunning()) {
            log.warn("手动停止失败：爬虫任务未在运行");
            return false;
        }
        taskManager.forceStopTask();
        return true;
    }

    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> map = new HashMap<>();
        map.put("running", taskManager.isRunning());
        map.put("triggerType", taskManager.getTriggerType());
        map.put("startTime", taskManager.getStartTime() == null
                ? "" : DateUtil.formatDateTime(taskManager.getStartTime()));
        map.put("endTime", taskManager.getEndTime() == null
                ? "" : DateUtil.formatDateTime(taskManager.getEndTime()));
        map.put("elapsedSeconds", taskManager.getElapsedSeconds());
        map.put("totalCount", taskManager.getTotalCount());
        map.put("successCount", taskManager.getSuccessCount());
        map.put("failCount", taskManager.getFailCount());
        map.put("duplicateCount", taskManager.getDuplicateCount());
        map.put("logs", taskManager.getLatestLogText());
        // 站点级运行汇总（任务结束时填充，运行期间为空列表）
        map.put("siteSummaries", taskManager.getSiteSummaries());
        map.put("siteFailures", taskManager.getSiteFailures());
        return map;
    }

    /**
     * 拼接本次任务的站点清单文本，如：[新浪新闻, 网易新闻]
     */
    private String buildSiteNameText(List<NewsCrawler.Seed> seeds) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < seeds.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(seeds.get(i).getName());
        }
        return sb.append("]").toString();
    }
}
