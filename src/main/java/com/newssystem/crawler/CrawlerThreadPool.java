package com.newssystem.crawler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class CrawlerThreadPool {

    private static final Logger log = LoggerFactory.getLogger(CrawlerThreadPool.class);

    @Value("${crawler.thread-count:5}")
    private int threadCount;

    private ThreadPoolExecutor executor;

    @PostConstruct
    public void init() {
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger seq = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("crawler-worker-" + seq.incrementAndGet());
                t.setDaemon(false);
                return t;
            }
        };

        executor = new ThreadPoolExecutor(
                threadCount,
                threadCount * 2,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        log.info("爬虫线程池初始化完成，核心线程数={}", threadCount);
    }

    public void submit(Runnable task) {
        if (executor == null || executor.isShutdown()) {
            throw new IllegalStateException("爬虫线程池已关闭");
        }
        executor.submit(task);
    }

    public boolean shutdownGracefully(long timeoutSeconds) {
        if (executor == null) return true;
        executor.shutdown();
        try {
            boolean finished = executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                log.warn("爬虫线程池等待{}秒仍未结束，尝试强制关闭", timeoutSeconds);
                executor.shutdownNow();
            }
            return finished;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
            return false;
        }
    }

    public int getActiveCount() {
        return executor == null ? 0 : executor.getActiveCount();
    }

    public int getQueueSize() {
        return executor == null ? 0 : executor.getQueue().size();
    }

    public long getCompletedTaskCount() {
        return executor == null ? 0 : executor.getCompletedTaskCount();
    }

    @PreDestroy
    public void destroy() {
        shutdownGracefully(10);
        log.info("爬虫线程池已销毁");
    }
}
