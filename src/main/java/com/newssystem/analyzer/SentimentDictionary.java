package com.newssystem.analyzer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 情感词典容器：启动时从 classpath:dict/sentiment/ 加载四类词典。
 * 词典外置而非硬编码，便于扩充，也便于论文中说明"情感词典规模"。
 *
 * @author NewsSystem Dev
 */
@Component
public class SentimentDictionary {

    private static final Logger log = LoggerFactory.getLogger(SentimentDictionary.class);

    /** 情感词 -> 权重（正数正面，负数负面） */
    private final Map<String, Double> wordWeight = new HashMap<>();
    /** 否定词集合 */
    private final Set<String> negations = new HashSet<>();
    /** 程度副词 -> 系数 */
    private final Map<String, Double> degrees = new HashMap<>();

    @PostConstruct
    public void load() {
        loadWeight("dict/sentiment/positive.txt", true);
        loadWeight("dict/sentiment/negative.txt", false);
        loadSet("dict/sentiment/negation.txt");
        loadDegree("dict/sentiment/degree.txt");
        log.info("情感词典加载完成：情感词={} 否定词={} 程度词={}",
                wordWeight.size(), negations.size(), degrees.size());
    }

    private void loadWeight(String path, boolean positive) {
        for (String line : readLines(path)) {
            String[] p = line.split("\\s+");
            if (p.length == 0 || p[0].isEmpty()) continue;
            double w = 1.0;
            if (p.length > 1) {
                try {
                    w = Double.parseDouble(p[1]);
                } catch (NumberFormatException ignored) {
                    w = 1.0;
                }
            }
            wordWeight.put(p[0], positive ? Math.abs(w) : -Math.abs(w));
        }
    }

    private void loadSet(String path) {
        for (String line : readLines(path)) {
            negations.add(line);
        }
    }

    private void loadDegree(String path) {
        for (String line : readLines(path)) {
            String[] p = line.split("\\s+");
            if (p.length == 0 || p[0].isEmpty()) continue;
            double d = 1.0;
            if (p.length > 1) {
                try {
                    d = Double.parseDouble(p[1]);
                } catch (NumberFormatException ignored) {
                    d = 1.0;
                }
            }
            degrees.put(p[0], d);
        }
    }

    /** 读取词典文件：跳过空行与 # 注释，统一 UTF-8 */
    private java.util.List<String> readLines(String path) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                lines.add(line);
            }
        } catch (Exception e) {
            log.error("加载情感词典失败：{}", path, e);
        }
        return lines;
    }

    /**
     * 按词长倒序的情感词条，供长词优先匹配使用（长词先消费区间，短词不再重复计分）
     */
    public java.util.List<Map.Entry<String, Double>> getSortedEntries() {
        java.util.List<Map.Entry<String, Double>> list = new java.util.ArrayList<>(wordWeight.entrySet());
        list.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));
        return list;
    }

    public Map<String, Double> getWordWeight() { return wordWeight; }
    public Set<String> getNegations() { return negations; }
    public Map<String, Double> getDegrees() { return degrees; }
}
