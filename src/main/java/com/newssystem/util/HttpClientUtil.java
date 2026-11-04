package com.newssystem.util;

import org.apache.http.Header;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class HttpClientUtil {

    private static final Logger log = LoggerFactory.getLogger(HttpClientUtil.class);

    @Autowired
    private CloseableHttpClient httpClient;

    @Autowired
    private RequestConfig requestConfig;

    private static final String[] USER_AGENTS = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.5 Safari/605.1.15",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/114.0",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/113.0.0.0 Safari/537.36",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 16_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.5 Mobile/15E148 Safari/604.1"
    };

    /** 匹配 Content-Type 或 HTML 源码中的 charset 声明（charset=xxx） */
    private static final Pattern CHARSET_PATTERN =
            Pattern.compile("charset\\s*=\\s*[\"']?([A-Za-z0-9_\\-]+)", Pattern.CASE_INSENSITIVE);

    /** 优先匹配 &lt;meta&gt; 标签内的 charset 声明，避免误命中页面JS中的charset字样 */
    private static final Pattern META_CHARSET_PATTERN =
            Pattern.compile("<meta[^>]+charset\\s*=\\s*[\"']?([A-Za-z0-9_\\-]+)", Pattern.CASE_INSENSITIVE);

    public String doGet(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }

        HttpGet httpGet = new HttpGet(url.trim());
        httpGet.setConfig(requestConfig);
        httpGet.setHeader("User-Agent", getRandomUserAgent());
        httpGet.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        httpGet.setHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        httpGet.setHeader("Connection", "keep-alive");

        try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                log.warn("请求URL返回状态码异常，url={}, statusCode={}", url, statusCode);
                return null;
            }
            // 先取原始字节，再按响应头/页面meta声明的编码解码，
            // 避免GBK等非UTF-8页面（如新浪图集 slide.news.sina.com.cn）被强制按UTF-8解码产生乱码
            byte[] raw = EntityUtils.toByteArray(response.getEntity());
            String charset = resolveCharset(response.getEntity().getContentType(), raw);
            String html = new String(raw, charset);
            log.debug("请求URL成功，url={}, 编码={}, html长度={}", url, charset, html.length());
            return html;
        } catch (IOException e) {
            log.error("请求URL失败，url={}, 原因：{}", url, e.getMessage());
            return null;
        }
    }

    /**
     * 解析页面实际编码，优先级从高到低：
     * 1) HTTP响应头 Content-Type 中的 charset；
     * 2) HTML源码 meta 声明（GB2312/GBK/GB18030 页面均为 ASCII 兼容编码，
     *    可按 US-ASCII 安全扫描前 8KB 定位 &lt;meta charset=...&gt;）；
     * 3) 无任何声明时对字节流做 UTF-8 严格校验，校验失败回退 GB18030；
     * 4) 默认 UTF-8。
     * GB2312/GBK 统一用其超集 GB18030 解码，兼容性最好。
     */
    private String resolveCharset(Header contentTypeHeader, byte[] raw) {
        // 优先级1：HTTP响应头声明的编码
        if (contentTypeHeader != null) {
            String charset = normalizeCharset(contentTypeHeader.getValue());
            if (charset != null) {
                return charset;
            }
        }
        // 优先级2：HTML meta 声明（只扫前8KB，足够覆盖<head>区域）
        if (raw != null && raw.length > 0) {
            int probeLen = Math.min(raw.length, 8192);
            String head = new String(raw, 0, probeLen, StandardCharsets.US_ASCII);
            Matcher metaMatcher = META_CHARSET_PATTERN.matcher(head);
            if (metaMatcher.find()) {
                String charset = normalizeCharset(metaMatcher.group(1));
                if (charset != null) {
                    return charset;
                }
            }
            // 兼容无<meta>标签但声明了charset的页面（如 http-equiv 写法）
            Matcher matcher = CHARSET_PATTERN.matcher(head);
            if (matcher.find()) {
                String charset = normalizeCharset(matcher.group(1));
                if (charset != null) {
                    return charset;
                }
            }
        }
        // 优先级3：无声明时UTF-8严格校验失败 → 多半是GBK系页面
        if (raw != null && raw.length > 0 && !isValidUtf8(raw)) {
            return "GB18030";
        }
        return "UTF-8";
    }

    /**
     * 将 charset 声明归一化为Java可识别的编码名。
     * GB2312/GBK/GB18030 统一映射为 GB18030（向下兼容），utf8 等别名归一化为 UTF-8。
     *
     * @param text Content-Type 值或单独的 charset 名（可含 "charset=" 前缀）
     * @return Java编码名；无法识别时返回 null
     */
    private String normalizeCharset(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        Matcher matcher = CHARSET_PATTERN.matcher(text);
        String name = matcher.find() ? matcher.group(1) : text.trim();
        String lower = name.toLowerCase();
        if ("gb2312".equals(lower) || "gbk".equals(lower) || "gb18030".equals(lower)) {
            return "GB18030";
        }
        if ("utf8".equals(lower) || "utf-8".equals(lower)) {
            return "UTF-8";
        }
        try {
            java.nio.charset.Charset.forName(name);
            return name;
        } catch (Exception e) {
            return null;
        }
    }

    /** 严格校验字节数组是否为合法UTF-8（存在非法字节序列即返回false） */
    private boolean isValidUtf8(byte[] raw) {
        try {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            decoder.decode(ByteBuffer.wrap(raw));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    private String getRandomUserAgent() {
        int index = (int) (Math.random() * USER_AGENTS.length);
        return USER_AGENTS[index];
    }
}
