package com.newssystem.analyzer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * TextRank 抽取式摘要。
 *
 * 算法步骤：
 * 1) 按 。！？；换行 分句，过滤过短/过长句；
 * 2) 每句分词取词集合（去重）；
 * 3) 句子相似度 sim(si,sj) = |交集| / (log(|si|+1) + log(|sj|+1))；
 * 4) 迭代 PR(si) = (1-d) + d * Σ(PR(sj) * sim(si,sj) / Σsim(sj,sk))，d=0.85，迭代20次；
 * 5) 取 PR 最高的3句，按原文顺序拼接，截断200字。
 *
 * @author NewsSystem Dev
 */
@Component
public class TextRankSummarizer {

    /** 阻尼系数 */
    private static final double DAMPING = 0.85;
    /** 迭代次数 */
    private static final int ITERATIONS = 20;
    /** 摘要选句数量 */
    private static final int TOP_SENTENCES = 3;
    /** 摘要最大长度 */
    private static final int MAX_SUMMARY_LENGTH = 200;
    /** 参与摘要计算的正文最大长度 */
    private static final int MAX_INPUT_LENGTH = 5000;
    /** 单篇最多分析的句子数（限制迭代复杂度，正文过长时只取前40句） */
    private static final int MAX_SENTENCES = 40;

    @Autowired
    private IkWordAnalyzer wordAnalyzer;

    /**
     * 生成摘要；正文不足两句时返回空串（无需摘要）
     *
     * @param content 清洗后的新闻正文
     * @return 不超过200字的抽取式摘要
     */
    public String summarize(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "";
        }
        String text = content.length() > MAX_INPUT_LENGTH
                ? content.substring(0, MAX_INPUT_LENGTH) : content;

        List<String> sentences = splitSentences(text);
        if (sentences.size() < 2) {
            return "";
        }

        // 每句分词后的词集合
        List<Set<String>> tokenSets = new ArrayList<>();
        for (String s : sentences) {
            tokenSets.add(new HashSet<>(wordAnalyzer.segment(s)));
        }

        int n = sentences.size();
        double[][] sim = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double v = similarity(tokenSets.get(i), tokenSets.get(j));
                sim[i][j] = v;
                sim[j][i] = v;
            }
        }

        // 预计算每个句子的出边权重和，避免在内层循环重复累加（O(n³) -> O(n²)）
        double[] outSum = new double[n];
        for (int j = 0; j < n; j++) {
            double s = 0;
            for (int k = 0; k < n; k++) {
                if (k != j) {
                    s += sim[j][k];
                }
            }
            outSum[j] = s;
        }

        // TextRank 迭代
        double[] pr = new double[n];
        Arrays.fill(pr, 1.0 / n);
        for (int it = 0; it < ITERATIONS; it++) {
            double[] next = new double[n];
            for (int i = 0; i < n; i++) {
                double sum = 0;
                for (int j = 0; j < n; j++) {
                    if (i == j || sim[i][j] <= 0 || outSum[j] <= 0) {
                        continue;
                    }
                    sum += pr[j] * sim[i][j] / outSum[j];
                }
                next[i] = (1 - DAMPING) / n + DAMPING * sum;
            }
            pr = next;
        }

        // 取分数最高的若干句，按原文顺序拼接
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) {
            idx[i] = i;
        }
        // lambda 只能捕获 effectively final 变量：迭代结束后用 final 引用固定得分数组
        final double[] scores = pr;
        Arrays.sort(idx, (a, b) -> Double.compare(scores[b], scores[a]));
        List<Integer> picked = new ArrayList<>();
        for (int i = 0; i < Math.min(TOP_SENTENCES, n); i++) {
            picked.add(idx[i]);
        }
        picked.sort(Integer::compareTo);

        StringBuilder sb = new StringBuilder();
        for (Integer i : picked) {
            sb.append(sentences.get(i)).append('。');
        }
        String summary = sb.toString();
        if (summary.length() > MAX_SUMMARY_LENGTH) {
            summary = summary.substring(0, MAX_SUMMARY_LENGTH) + "…";
        }
        return summary;
    }

    /** 分句：按中文句末标点与换行切分，过滤长度 8~200 的句子 */
    private List<String> splitSentences(String text) {
        List<String> list = new ArrayList<>();
        String[] parts = text.split("[。！？!?；;\\n\\r]+");
        for (String p : parts) {
            String s = p.trim();
            if (s.length() < 8) {
                continue;
            }
            // 过滤网页模板文案（来源/作者/责任编辑/分享栏等），避免摘要混入页面噪声
            if (isTemplateSentence(s)) {
                continue;
            }
            if (s.length() > 200) {
                s = s.substring(0, 200);
            }
            list.add(s);
            if (list.size() >= MAX_SENTENCES) {
                break;
            }
        }
        return list;
    }

    /** 判断是否为网页模板/版权/导航类噪声句 */
    private boolean isTemplateSentence(String s) {
        String[] markers = {
                "来源:", "来源：", "作者:", "作者：", "责任编辑", "发布时间", "分享到",
                "大字体", "小字体", "编辑:", "编辑：", "原标题", "更多精彩内容",
                "扫描二维码", "点击进入", "上一篇", "下一篇", "相关阅读", "返回首页"
        };
        return Arrays.stream(markers).anyMatch(s::contains);
    }

    /** 句子相似度：|交集| / (log(|si|+1) + log(|sj|+1)) */
    private double similarity(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        int inter = 0;
        for (String w : a) {
            if (b.contains(w)) {
                inter++;
            }
        }
        if (inter == 0) {
            return 0;
        }
        return inter / (Math.log(a.size() + 1) + Math.log(b.size() + 1));
    }
}
