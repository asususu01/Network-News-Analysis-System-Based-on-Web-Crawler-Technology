package com.newssystem.analyzer;

import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.SegToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class IkWordAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(IkWordAnalyzer.class);

    @Value("${analyzer.min-word-length:2}")
    private int minWordLength;

    /**
     * Jieba分词器（全局单例，线程安全，内部采用懒加载词典）
     */
    private static final JiebaSegmenter SEGMENTER = new JiebaSegmenter();

    /**
     * 内置停用词集合（简易停用词典）
     * 因为毕设不需要引入庞大的停用词典，使用常用停用词即可
     */
    private static final Set<String> STOP_WORDS = new HashSet<>();

    static {
        String[] stopWords = {
                "的", "了", "和", "是", "在", "我", "有", "他", "她", "它", "们",
                "你", "您", "就", "也", "都", "不", "人", "一", "一个", "上", "下",
                "而", "及", "与", "或", "但", "但是", "因为", "所以", "如果", "虽然",
                "这个", "那个", "这些", "那些", "这里", "那里", "自己", "什么", "怎么",
                "这样", "那样", "如何", "为了", "对于", "关于", "通过", "进行", "可以",
                "已经", "正在", "将", "会", "要", "能", "能够", "应该", "必须",
                "我们", "你们", "他们", "她们", "它们", "我国", "目前", "现在", "以后",
                "之前", "之后", "今天", "昨天", "明天", "今年", "去年", "明年",
                "以及", "及其", "等等", "一下", "一种", "一样", "一直", "其实",
                "还是", "还有", "就是", "只是", "不过", "然后", "而且", "并且"
        };
        for (String w : stopWords) {
            STOP_WORDS.add(w);
        }
    }

    /**
     * 对文本进行分词，返回有效词语列表
     *
     * @param text     待分词文本（建议是清洗后的纯文本）
     * @param useSmart true=智能分词（Jieba SEARCH模式，粒度较粗，更适合中文热词统计）；
     *                 false=INDEX模式（细粒度，用于索引）
     * @return 分词列表；分词异常返回空List
     */
    public List<String> segment(String text, boolean useSmart) {
        List<String> result = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return result;
        }

        try {
            JiebaSegmenter.SegMode mode = useSmart
                    ? JiebaSegmenter.SegMode.SEARCH
                    : JiebaSegmenter.SegMode.INDEX;

            List<SegToken> tokens = SEGMENTER.process(text, mode);

            for (SegToken token : tokens) {
                String word = token.word;
                // 过滤：null、纯空白
                if (word == null || word.trim().isEmpty()) {
                    continue;
                }
                word = word.trim();
                // 过滤：长度过短
                if (word.length() < minWordLength) {
                    continue;
                }
                // 过滤：停用词
                if (STOP_WORDS.contains(word)) {
                    continue;
                }
                // 过滤：纯数字（如"2023"），但字母数字混合保留（如"GDP"）
                if (isAllDigits(word)) {
                    continue;
                }
                result.add(word);
            }
        } catch (Exception e) {
            log.error("Jieba分词异常：", e);
        }
        return result;
    }

    /**
     * 对单段文本分词（默认智能分词）
     */
    public List<String> segment(String text) {
        return segment(text, true);
    }

    /**
     * 判断字符串是否全部是数字
     */
    private boolean isAllDigits(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
