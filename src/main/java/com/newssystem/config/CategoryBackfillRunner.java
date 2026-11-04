package com.newssystem.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.newssystem.analyzer.NewsClassifier;
import com.newssystem.entity.News;
import com.newssystem.mapper.NewsMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 存量新闻分类回填器。
 *
 * news 表新增 category 列后，历史数据该字段为 NULL。应用启动后自动扫描
 * category 为空的新闻，调用 {@link NewsClassifier} 依据URL/标题/正文补算分类
 * 并回写数据库；新爬取的新闻入库时已直接写入分类，不会被重复处理。
 * 幂等：仅更新 NULL 行，重复启动无副作用。
 *
 * @author NewsSystem Dev
 */
@Component
@Order(20)
public class CategoryBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CategoryBackfillRunner.class);

    @Autowired
    private NewsMapper newsMapper;

    @Autowired
    private NewsClassifier newsClassifier;

    @Override
    public void run(ApplicationArguments args) {
        try {
            // 只处理尚未分类的存量新闻（新数据入库时已带分类）
            QueryWrapper<News> qw = new QueryWrapper<>();
            qw.isNull("category").or().eq("category", "");
            List<News> uncategorized = newsMapper.selectList(qw);
            if (uncategorized == null || uncategorized.isEmpty()) {
                log.debug("存量新闻分类回填：无待分类数据，跳过");
                return;
            }

            log.info("存量新闻分类回填开始：共{}条待分类", uncategorized.size());
            int updated = 0;
            for (News news : uncategorized) {
                String category = newsClassifier.classify(
                        news.getUrl(), news.getTitle(), news.getContent());
                News patch = new News();
                patch.setId(news.getId());
                patch.setCategory(category);
                updated += newsMapper.updateById(patch);
            }
            log.info("存量新闻分类回填完成：成功更新{}条", updated);
        } catch (Exception e) {
            // 回填失败不阻断应用启动（不影响爬取与列表功能，下次启动会再次尝试）
            log.error("存量新闻分类回填异常（不影响系统运行）:", e);
        }
    }
}
