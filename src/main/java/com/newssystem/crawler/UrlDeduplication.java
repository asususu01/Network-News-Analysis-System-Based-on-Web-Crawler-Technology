package com.newssystem.crawler;

import com.newssystem.mapper.NewsMapper;
import com.newssystem.util.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class UrlDeduplication {

    private static final Logger log = LoggerFactory.getLogger(UrlDeduplication.class);

    /** 启动预热的时间窗口（天）：只加载近期已入库新闻的URL哈希 */
    private static final int WARM_UP_DAYS = 30;

    private final Set<String> urlHashSet = Collections.synchronizedSet(new HashSet<>());

    /**
     * 预热基线：应用启动时从数据库加载的近期URL哈希。
     * 每个爬虫任务开始调用 {@link #clear()} 时恢复为这份基线，
     * 既能隔离不同任务的内存去重集合，又不会把历史新闻重新抓一遍。
     */
    private final Set<String> baselineHashes = Collections.synchronizedSet(new HashSet<>());

    @Autowired
    private NewsMapper newsMapper;

    /**
     * 启动预热：从 news 表加载最近30天已入库新闻的 url_hash。
     * 数据库唯一索引能兜底不产生脏数据，但预热可以避免白费请求与配额。
     */
    @PostConstruct
    public void warmUp() {
        try {
            List<String> hashes = newsMapper.selectRecentUrlHashes(
                    LocalDateTime.now().minusDays(WARM_UP_DAYS));
            if (hashes != null) {
                for (String h : hashes) {
                    if (h != null && !h.isEmpty()) {
                        baselineHashes.add(h);
                    }
                }
            }
            urlHashSet.addAll(baselineHashes);
            log.info("URL去重集合预热完成，加载{}条历史URL哈希", urlHashSet.size());
        } catch (Exception e) {
            log.error("URL去重集合预热失败，本次仍使用内存去重", e);
        }
    }

    public boolean isDuplicated(String url) {
        if (url == null || url.isEmpty()) {
            return true;
        }
        String hash = HashUtil.md5(url);
        return urlHashSet.contains(hash);
    }

    public boolean addUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        String hash = HashUtil.md5(url);
        return urlHashSet.add(hash);
    }

    /**
     * 重置为本任务所需的去重集合：清空本次任务期间新增的URL，但保留启动预热的近30天历史哈希，
     * 防止重启后把已入库的新闻重复抓取。
     */
    public void clear() {
        urlHashSet.clear();
        urlHashSet.addAll(baselineHashes);
        log.info("URL去重集合已重置为预热基线，当前大小={}", urlHashSet.size());
    }

    public int size() {
        return urlHashSet.size();
    }
}
