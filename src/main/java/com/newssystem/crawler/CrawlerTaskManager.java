package com.newssystem.crawler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 爬虫任务运行管理器。
 *
 * 负责：全局进度统计（总URL/成功/失败/重复）、日志缓冲、
 *      以及本次任务每个站点的入库量与失败原因汇总（用于前端失败弹窗）。
 *
 * @author NewsSystem Dev
 */
@Component
public class CrawlerTaskManager {

    private static final Logger log = LoggerFactory.getLogger(CrawlerTaskManager.class);

    private volatile boolean running = false;
    private volatile LocalDateTime startTime;
    private volatile LocalDateTime endTime;
    private volatile int totalCount;
    private volatile int successCount;
    private volatile int failCount;
    private volatile int duplicateCount;
    private volatile String triggerType;

    /** 本次任务的站点级运行汇总（任务结束后由 {@link NewsCrawler} 填充） */
    private volatile List<SiteFailureInfo> siteFailures = new ArrayList<>();
    /** 本次任务所有站点的汇总（成功+失败，任务结束后填充） */
    private volatile List<SiteFailureInfo> siteSummaries = new ArrayList<>();

    private final StringBuilder latestLogs = new StringBuilder();
    private static final int MAX_LOG_LINES = 200;

    public boolean isRunning() { return running; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public int getTotalCount() { return totalCount; }
    public int getSuccessCount() { return successCount; }
    public int getFailCount() { return failCount; }
    public int getDuplicateCount() { return duplicateCount; }
    public String getTriggerType() { return triggerType; }
    public List<SiteFailureInfo> getSiteFailures() {
        return siteFailures == null ? Collections.emptyList() : siteFailures;
    }
    public List<SiteFailureInfo> getSiteSummaries() {
        return siteSummaries == null ? Collections.emptyList() : siteSummaries;
    }

    public synchronized void startTask(String triggerType) {
        if (running) {
            log.warn("爬虫任务已经在运行中，不能重复启动！");
            return;
        }
        this.running = true;
        this.startTime = LocalDateTime.now();
        this.endTime = null;
        this.totalCount = 0;
        this.successCount = 0;
        this.failCount = 0;
        this.duplicateCount = 0;
        this.triggerType = triggerType;
        this.siteFailures = new ArrayList<>();
        this.siteSummaries = new ArrayList<>();
        latestLogs.setLength(0);
        appendLog("【任务开始】触发方式=" + triggerType + "，开始时间=" + startTime);
        log.info("==================== 爬虫任务启动（{}） ====================", triggerType);
    }

    /**
     * 写入本次任务每个站点的运行汇总（含入库量与失败原因）。
     * 通常在 {@link NewsCrawler#startCrawlWithNames} 的任务收尾阶段一次性填充。
     *
     * @param allSummaries 全部站点的汇总
     * @param failures     其中入库量为 0 的站点（失败），可视为 allSummaries 的子集
     */
    public synchronized void setSiteSummaries(List<SiteFailureInfo> allSummaries,
                                              List<SiteFailureInfo> failures) {
        this.siteSummaries = allSummaries == null ? new ArrayList<>() : allSummaries;
        this.siteFailures = failures == null ? new ArrayList<>() : failures;
    }

    /**
     * 单站点失败信息：站点名称、入库量、失败原因。
     * 仅作为前后端数据传输 DTO，不做业务逻辑。
     */
    public static class SiteFailureInfo {
        private String siteName;
        private int savedCount;
        private String failureReason;
        /** 是否为失败（savedCount == 0），便于前端过滤 */
        private boolean failed;

        public SiteFailureInfo(String siteName, int savedCount, String failureReason, boolean failed) {
            this.siteName = siteName;
            this.savedCount = savedCount;
            this.failureReason = failureReason;
            this.failed = failed;
        }

        public String getSiteName() { return siteName; }
        public int getSavedCount() { return savedCount; }
        public String getFailureReason() { return failureReason; }
        public boolean isFailed() { return failed; }
    }

    public synchronized void stopTask() {
        if (!running) {
            return;
        }
        this.running = false;
        this.endTime = LocalDateTime.now();
        long seconds = ChronoUnit.SECONDS.between(startTime, endTime);
        appendLog("【任务结束】总URL=" + totalCount + "，成功=" + successCount
                + "，失败=" + failCount + "，重复=" + duplicateCount
                + "，总耗时=" + seconds + "秒");
        log.info("==================== 爬虫任务结束，总耗时{}秒 ====================", seconds);
    }

    public synchronized void forceStopTask() {
        if (!running) {
            return;
        }
        appendLog("【用户手动停止】爬虫任务已被用户强制停止");
        log.warn("用户手动停止爬虫任务");
        stopTask();
    }

    public synchronized void incrementTotal(int delta) {
        this.totalCount += delta;
    }

    public synchronized void incrementSuccess() {
        this.successCount++;
    }

    public synchronized void incrementFail() {
        this.failCount++;
    }

    public synchronized void incrementDuplicate() {
        this.duplicateCount++;
    }

    public long getElapsedSeconds() {
        if (startTime == null) return 0;
        LocalDateTime end = endTime == null ? LocalDateTime.now() : endTime;
        return ChronoUnit.SECONDS.between(startTime, end);
    }

    public synchronized void appendLog(String msg) {
        String line = "[" + LocalDateTime.now() + "] " + msg;
        latestLogs.append(line).append("\n");
        while (countLines(latestLogs) > MAX_LOG_LINES) {
            int idx = latestLogs.indexOf("\n");
            if (idx < 0) break;
            latestLogs.delete(0, idx + 1);
        }
        log.info("[CRAWLER] {}", msg);
    }

    public synchronized String getLatestLogText() {
        return latestLogs.toString();
    }

    private static int countLines(StringBuilder sb) {
        int count = 0;
        for (int i = 0; i < sb.length(); i++) {
            if (sb.charAt(i) == '\n') count++;
        }
        return count;
    }
}
