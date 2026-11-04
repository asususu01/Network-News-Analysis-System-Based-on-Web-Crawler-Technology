package com.newssystem.config;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * HttpClient 连接池配置
 * 使用连接池避免每次请求重新创建TCP连接，提高爬虫效率
 *
 * @author NewsSystem Dev
 */
@Configuration
public class HttpClientConfig {

    /** 连接池最大连接数 */
    @Value("${crawler.max-total:50}")
    private int maxTotal;

    /** 每个路由最大并发数 */
    @Value("${crawler.default-max-per-route:10}")
    private int defaultMaxPerRoute;

    /** 连接超时（ms） */
    @Value("${crawler.connection-timeout:10000}")
    private int connectTimeout;

    /** 读取超时（ms） */
    @Value("${crawler.socket-timeout:20000}")
    private int socketTimeout;

    /**
     * 连接池管理器
     */
    @Bean
    public PoolingHttpClientConnectionManager poolingHttpClientConnectionManager() {
        PoolingHttpClientConnectionManager manager = new PoolingHttpClientConnectionManager();
        manager.setMaxTotal(maxTotal);
        manager.setDefaultMaxPerRoute(defaultMaxPerRoute);
        return manager;
    }

    /**
     * 请求超时配置
     */
    @Bean
    public RequestConfig requestConfig() {
        return RequestConfig.custom()
                .setConnectTimeout(connectTimeout)        // 建立TCP连接超时
                .setConnectionRequestTimeout(connectTimeout) // 从连接池获取连接超时
                .setSocketTimeout(socketTimeout)          // 读取数据超时
                .build();
    }

    /**
     * 创建可复用的 CloseableHttpClient 实例（Bean单例）
     */
    @Bean
    public CloseableHttpClient closeableHttpClient(PoolingHttpClientConnectionManager manager,
                                                    RequestConfig requestConfig) {
        return HttpClients.custom()
                .setConnectionManager(manager)
                .setDefaultRequestConfig(requestConfig)
                // 禁用自动重试，避免对目标服务器造成压力
                .disableAutomaticRetries()
                .build();
    }
}
