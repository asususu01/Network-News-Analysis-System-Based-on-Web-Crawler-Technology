package com.newssystem.service;

import java.util.List;
import java.util.Map;

/**
 * 爬虫业务接口
 *
 * @author NewsSystem Dev
 */
public interface CrawlerService {

    /**
     * 手动启动爬虫任务（异步执行）
     *
     * @param maxPerSite 每个种子站点本次任务最多入库的新闻条数（下拉选择，仅允许
     *                   5/10/20/30/40/50，非法值由实现回退为默认5条）
     * @param siteIds    指定本次爬取的站点ID列表（爬虫管理页下拉选择）；
     *                   null或空表示爬取全部已配置站点
     * @return 是否成功启动（正在运行中、或选中站点不存在/已被删除时返回false）
     */
    boolean startCrawler(Integer maxPerSite, List<Long> siteIds);

    /**
     * 手动停止爬虫任务
     */
    boolean stopCrawler();

    /**
     * 获取爬虫运行状态 + 统计信息
     * 返回Map包含：running, triggerType, startTime, elapsedSeconds,
     * totalCount, successCount, failCount, duplicateCount, logs
     */
    Map<String, Object> getStatus();
}
