package com.newssystem.analyzer;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.newssystem.entity.HotWord;
import com.newssystem.entity.News;
import com.newssystem.mapper.HotWordMapper;
import com.newssystem.mapper.NewsMapper;
import com.newssystem.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class HotWordExtractor {

    private static final Logger log = LoggerFactory.getLogger(HotWordExtractor.class);

    @Autowired
    private NewsMapper newsMapper;

    @Autowired
    private HotWordMapper hotWordMapper;

    @Autowired
    private IkWordAnalyzer ikWordAnalyzer;

    @Value("${analyzer.hot-word-top-n:50}")
    private int hotWordTopN;

    @Transactional(rollbackFor = Exception.class)
    public int extractAndSave(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            log.warn("热词分析参数错误：startDate={}, endDate={}", startDate, endDate);
            return 0;
        }
        log.info("开始分析热词，日期范围={} ~ {}", startDate, endDate);

        LocalDate cur = startDate;
        int totalInserted = 0;
        while (!cur.isAfter(endDate)) {
            LocalDateTime dayStart = cur.atStartOfDay();
            LocalDateTime dayEnd = cur.plusDays(1).atStartOfDay();

            QueryWrapper<News> qw = new QueryWrapper<>();
            qw.ge("create_time", dayStart).lt("create_time", dayEnd);
            qw.select("title", "content");
            List<News> newsList = newsMapper.selectList(qw);

            if (newsList != null && !newsList.isEmpty()) {
                Map<String, Integer> freqMap = countWordFrequency(newsList);
                List<Map.Entry<String, Integer>> topList = getTopN(freqMap, hotWordTopN);

                QueryWrapper<HotWord> delQw = new QueryWrapper<>();
                delQw.eq("stat_date", cur);
                hotWordMapper.delete(delQw);

                LocalDateTime now = LocalDateTime.now();
                for (Map.Entry<String, Integer> e : topList) {
                    HotWord hw = new HotWord();
                    hw.setWord(e.getKey());
                    hw.setFrequency(e.getValue());
                    hw.setStatDate(cur);
                    hw.setCreateTime(now);
                    hotWordMapper.insert(hw);
                    totalInserted++;
                }
                log.info("日期 {} 热词分析完成，共 {} 条入库（新闻数={}）",
                        cur, topList.size(), newsList.size());
            } else {
                log.debug("日期 {} 没有新闻，跳过", cur);
            }
            cur = cur.plusDays(1);
        }

        log.info("热词分析结束，累计入库 {} 条热词", totalInserted);
        return totalInserted;
    }

    public int extractToday() {
        LocalDate today = DateUtil.today();
        return extractAndSave(today, today);
    }

    public int extractRecentDays(int days) {
        LocalDate end = DateUtil.today();
        LocalDate start = end.minusDays(Math.max(0, days - 1));
        return extractAndSave(start, end);
    }

    private Map<String, Integer> countWordFrequency(List<News> newsList) {
        Map<String, Integer> freqMap = new HashMap<>();
        for (News news : newsList) {
            if (news.getTitle() != null && !news.getTitle().isEmpty()) {
                List<String> titleWords = ikWordAnalyzer.segment(news.getTitle());
                for (String w : titleWords) {
                    freqMap.merge(w, 2, Integer::sum);
                }
            }
            if (news.getContent() != null && !news.getContent().isEmpty()) {
                String content = news.getContent();
                if (content.length() > 5000) {
                    content = content.substring(0, 5000);
                }
                List<String> contentWords = ikWordAnalyzer.segment(content);
                for (String w : contentWords) {
                    freqMap.merge(w, 1, Integer::sum);
                }
            }
        }
        return freqMap;
    }

    private List<Map.Entry<String, Integer>> getTopN(Map<String, Integer> freqMap, int n) {
        if (freqMap == null || freqMap.isEmpty()) {
            return new ArrayList<>();
        }
        return freqMap.entrySet()
                .stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(n)
                .collect(Collectors.toList());
    }
}
