package com.newssystem.analyzer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 情感分析器（词典加权版）。
 *
 * 打分规则：
 * 1) 基础分 = 情感词权重；
 * 2) 若情感词前 4 个字符内出现否定词 -> 极性翻转并 ×0.8；
 * 3) 若情感词前 3 个字符内出现程度副词 -> 乘以对应系数；
 * 4) 累加后归一化到 [-100, 100]，标题重复计算两次（标题对新闻倾向的表征力更强）。
 *
 * @author NewsSystem Dev
 */
@Component
public class SimpleSentimentAnalyzer {

    /** 否定词回溯窗口（字符） */
    private static final int NEGATION_WINDOW = 4;
    /** 程度副词回溯窗口（字符） */
    private static final int DEGREE_WINDOW = 3;
    /** 判定为正面/负面的阈值（强度值） */
    private static final int THRESHOLD = 15;

    @Autowired
    private SentimentDictionary dictionary;

    /**
     * 返回情感强度，范围 [-100, 100]。
     *
     * @param title   新闻标题（可为空）
     * @param content 新闻正文（已清洗）
     */
    public int analyzeScore(String title, String content) {
        double raw = score(title) * 2 + score(content);
        return (int) Math.round(100.0 * raw / (Math.abs(raw) + 5));
    }

    /**
     * 兼容旧调用：仅凭正文返回三档极性（1/0/-1）
     */
    public int analyze(String content) {
        return toPolarity(analyzeScore(null, content));
    }

    /**
     * 强度值 -> 三档极性，保证 news.sentiment 字段语义不变
     */
    public int toPolarity(int score) {
        if (score >= THRESHOLD) return 1;
        if (score <= -THRESHOLD) return -1;
        return 0;
    }

    /**
     * 单段文本的原始加权得分。
     * 按词长倒序匹配：长词先命中并标记其占用区间，短词落在已占用区间时跳过，
     * 避免"起诉书"被"起诉"重复计分这类包含关系污染。
     */
    private double score(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        double sum = 0;
        Set<String> negations = dictionary.getNegations();
        Map<String, Double> degrees = dictionary.getDegrees();
        // consumed[i] = 文本第 i 个字符已被某个更长的情感词占用
        boolean[] consumed = new boolean[text.length()];

        for (Map.Entry<String, Double> e : dictionary.getSortedEntries()) {
            String w = e.getKey();
            int idx = text.indexOf(w);
            while (idx >= 0) {
                int end = idx + w.length();
                if (isFree(consumed, idx, end)) {
                    double v = e.getValue();

                    // 否定词修饰：翻转极性并按 0.8 衰减
                    String negCtx = text.substring(Math.max(0, idx - NEGATION_WINDOW), idx);
                    for (String n : negations) {
                        if (negCtx.contains(n)) {
                            v = -v * 0.8;
                            break;
                        }
                    }

                    // 程度副词修饰：按系数放大/缩小
                    String degCtx = text.substring(Math.max(0, idx - DEGREE_WINDOW), idx);
                    for (Map.Entry<String, Double> d : degrees.entrySet()) {
                        if (degCtx.contains(d.getKey())) {
                            v *= d.getValue();
                            break;
                        }
                    }

                    sum += v;
                    markConsumed(consumed, idx, end);
                }
                idx = text.indexOf(w, idx + w.length());
            }
        }
        return sum;
    }

    /** 判断区间 [begin, end) 是否未被占用 */
    private boolean isFree(boolean[] consumed, int begin, int end) {
        for (int i = begin; i < end; i++) {
            if (consumed[i]) {
                return false;
            }
        }
        return true;
    }

    /** 标记区间 [begin, end) 已被占用 */
    private void markConsumed(boolean[] consumed, int begin, int end) {
        for (int i = begin; i < end; i++) {
            consumed[i] = true;
        }
    }
}
