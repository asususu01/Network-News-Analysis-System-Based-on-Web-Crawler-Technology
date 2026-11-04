package com.newssystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.newssystem.common.PageResult;
import com.newssystem.common.SourceHotResult;
import com.newssystem.entity.News;
import com.newssystem.mapper.NewsMapper;
import com.newssystem.service.NewsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class NewsServiceImpl implements NewsService {

    private static final Logger log = LoggerFactory.getLogger(NewsServiceImpl.class);

    /**
     * 排序字段白名单：前端传入的字段名 -> 实际 ORDER BY SQL表达式。
     * 只允许白名单内字段，杜绝前端任意拼接列名造成的SQL注入风险。
     * publish_time 使用 COALESCE 兜底：发布时间为空的新闻按入库时间参与排序，避免空值排序混乱。
     * 字段键集合与 {@link NewsService#ALLOWED_SORT_FIELDS} 保持一致。
     */
    private static final Map<String, String> SORT_COLUMN_MAP = new LinkedHashMap<>();
    static {
        SORT_COLUMN_MAP.put("publish_time", "COALESCE(publish_time, create_time)");
        SORT_COLUMN_MAP.put("create_time", "create_time");
        SORT_COLUMN_MAP.put("id", "id");
        SORT_COLUMN_MAP.put("sentiment", "sentiment");
        SORT_COLUMN_MAP.put("hot_score", "hot_score");
    }

    @Autowired
    private NewsMapper newsMapper;

    /** 清洗字符串筛选项：去除空白项、去重；null 视为空集合 */
    private static List<String> cleanStrings(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(v -> v != null && !v.trim().isEmpty())
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
    }

    /** 清洗情感筛选项：去除 null 并去重 */
    private static List<Integer> cleanInts(List<Integer> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }

    @Override
    public PageResult<News> pageNews(Integer pageNum, Integer pageSize,
                                      String keyword, List<String> sources, List<String> categories,
                                      List<Integer> sentiments,
                                      String sortField, String sortOrder) {
        int pn = pageNum == null || pageNum < 1 ? 1 : pageNum;
        // 管理端列表一页展示全部新闻：上限放宽到MAX_PAGE_SIZE
        int ps = pageSize == null || pageSize < 1 ? 10 : (pageSize > MAX_PAGE_SIZE ? MAX_PAGE_SIZE : pageSize);

        QueryWrapper<News> qw = new QueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            qw.like("title", keyword.trim());
        }
        // 多来源筛选：表头下拉勾选的多个来源取并集（IN）；为空表示全部来源
        List<String> validSources = cleanStrings(sources);
        if (!validSources.isEmpty()) {
            qw.in("source", validSources);
        }
        // 多分类筛选：勾选的多个新闻分类取并集（IN）；为空表示全部分类
        List<String> validCategories = cleanStrings(categories);
        if (!validCategories.isEmpty()) {
            qw.in("category", validCategories);
        }
        // 多情感筛选：表头下拉勾选的多个情感类型取并集（IN）；为空表示全部情感
        List<Integer> validSentiments = cleanInts(sentiments);
        if (!validSentiments.isEmpty()) {
            qw.in("sentiment", validSentiments);
        }

        // ===== 排序处理（白名单校验 + 空值兜底 + 稳定次级排序） =====
        // 排序字段：不在白名单内一律使用默认值，防止SQL注入
        String fieldKey = (sortField == null || !SORT_COLUMN_MAP.containsKey(sortField))
                ? DEFAULT_SORT_FIELD : sortField;
        String orderColumn = SORT_COLUMN_MAP.get(fieldKey);
        // 排序方向：仅 asc 为升序；desc/为空/非法一律回退默认方向（发布时间倒序，即日期由新到旧）
        boolean isAsc = "asc".equalsIgnoreCase(sortOrder);
        // 主排序
        qw.orderBy(true, isAsc, orderColumn);
        // 次级排序：主字段相同时按id升序，保证排序稳定且沿用原有的ID升序规则
        if (!"id".equals(fieldKey)) {
            qw.orderBy(true, true, "id");
        }
        log.debug("管理端新闻分页：pageNum={}, pageSize={}, sortField={}, order={}",
                pn, ps, fieldKey, isAsc ? "ASC" : "DESC");

        Page<News> page = new Page<>(pn, ps);
        newsMapper.selectPage(page, qw);

        return PageResult.of(
                (int) page.getCurrent(),
                (int) page.getSize(),
                page.getTotal(),
                page.getRecords()
        );
    }

    @Override
    public News getNewsById(Long id) {
        if (id == null) return null;
        return newsMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteNews(Long id) {
        if (id == null) return false;
        int deleted = newsMapper.deleteById(id);
        return deleted > 0;
    }

    /**
     * 批量删除新闻：过滤非法ID后调用MyBatis-Plus批量删除
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteNewsBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        // 去重并过滤null，避免无效值影响删除结果
        List<Long> validIds = ids.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (validIds.isEmpty()) {
            return false;
        }
        int deleted = newsMapper.deleteBatchIds(validIds);
        log.info("批量删除新闻：请求{}个ID，实际删除{}条", validIds.size(), deleted);
        return deleted > 0;
    }

    /**
     * 用户端：全部新闻按日期分组（日期倒序，权威顺序由SQL保证）
     * 支持与管理端一致的来源/分类/情感筛选（SQL IN）
     */
    @Override
    public Map<LocalDate, List<News>> listGroupedByDate(List<String> sources,
                                                         List<String> categories,
                                                         List<Integer> sentiments,
                                                         Integer recentDays) {
        QueryWrapper<News> qw = new QueryWrapper<>();
        // 多来源筛选：勾选的多个来源取并集；为空表示全部来源
        List<String> validSources = cleanStrings(sources);
        if (!validSources.isEmpty()) {
            qw.in("source", validSources);
        }
        // 分类筛选：用户端单选标签，至多传1个分类；统一按IN处理与管理端口径一致
        List<String> validCategories = cleanStrings(categories);
        if (!validCategories.isEmpty()) {
            qw.in("category", validCategories);
        }
        // 多情感筛选：勾选的多个情感类型取并集；为空表示全部情感
        List<Integer> validSentiments = cleanInts(sentiments);
        if (!validSentiments.isEmpty()) {
            qw.in("sentiment", validSentiments);
        }
        // 时间范围筛选：只保留最近 N 天（按发布时间，缺失时回退入库时间），从数据源头控制列表体量
        if (recentDays != null && recentDays > 0) {
            LocalDateTime start = LocalDateTime.now().minusDays(recentDays);
            qw.apply("COALESCE(publish_time, create_time) >= {0}", start);
            log.debug("用户端新闻列表时间范围：最近{}天（起始 {}）", recentDays, start);
        }
        // 与管理端新闻列表保持完全一致的排序：发布时间倒序、时间相同按ID升序；
        // 先按日期倒序分组，再在组内保持该顺序，因此两端展示的条数与先后次序一致。
        // 不加LIMIT，用户端同样展示全部已采集新闻。
        qw.last("ORDER BY DATE(COALESCE(publish_time, create_time)) DESC, "
                + "COALESCE(publish_time, create_time) DESC, id ASC");
        List<News> latest = newsMapper.selectList(qw);

        // 使用LinkedHashMap保证分组后的日期顺序与查询结果一致（最新日期在前）
        Map<LocalDate, List<News>> grouped = latest.stream()
                .collect(Collectors.groupingBy(
                        this::resolveNewsDate,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        // 不截断每日条数：用户端与管理端保持相同的新闻数量与数据范围
        log.debug("用户端新闻列表：来源{} 情感{}，查询{}条，分为{}个日期组",
                validSources, validSentiments, latest.size(), grouped.size());
        return grouped;
    }

    /** 某新闻网今日暂无数据时，回退展示该网最新热点的最大条数 */
    private static final int FALLBACK_LIMIT = 50;
    /** 热榜返回条数上限（全站聚合后单日可达数百条，必须截断） */
    private static final int HOT_TOP_N = 50;

    /**
     * 用户端：全站 / 指定新闻网某日热点排行榜
     * 1) 先查请求日期当天的新闻（source 为空时聚合全部来源）；
     * 2) 若当天无数据，回退查最新一批热点，保证页面始终有内容可演示；
     * 3) 若系统/该站从无新闻，返回空列表由页面提示。
     */
    @Override
    public SourceHotResult listHotBySource(String source, LocalDate date) {
        // 空串统一转为 null，表示"全站聚合"而非"无数据"
        String webSource = (source == null || source.trim().isEmpty()) ? null : source.trim();
        LocalDate target = date == null ? LocalDate.now() : date;

        // 第一步：查询请求日期当天的热点（webSource 为 null 时聚合全部来源）
        LocalDateTime start = target.atStartOfDay();
        LocalDateTime end = target.plusDays(1).atStartOfDay();
        // 多取一批用于簇去重：同一事件可能被多家媒体报道，去重后仍需保证 Top 50 条数
        List<News> todayList = newsMapper.selectHot(webSource, start, end, HOT_TOP_N * 3);
        todayList = dedupByCluster(todayList, HOT_TOP_N);

        if (todayList != null && !todayList.isEmpty()) {
            log.debug("热榜[{}] {} 当日热点{}条",
                    webSource == null ? "全站" : webSource, target, todayList.size());
            return new SourceHotResult(webSource, target, todayList, false, target);
        }

        // 第二步：当天无数据，回退查询最新的一批热点（同样按事件簇去重，避免同事件霸榜）
        List<News> latest = dedupByCluster(
                newsMapper.selectLatestHot(webSource, FALLBACK_LIMIT * 3), FALLBACK_LIMIT);
        if (latest == null) {
            latest = new ArrayList<>();
        }
        LocalDate actualDate = null;
        if (!latest.isEmpty()) {
            News first = latest.get(0);
            actualDate = first.getPublishTime() != null
                    ? first.getPublishTime().toLocalDate()
                    : (first.getCreateTime() != null ? first.getCreateTime().toLocalDate() : target);
        }
        log.debug("热榜[{}] {} 无当日数据，回退最新热点{}条，实际日期={}",
                webSource == null ? "全站" : webSource, target, latest.size(), actualDate);
        return new SourceHotResult(webSource, target, latest, !latest.isEmpty(), actualDate);
    }

    /**
     * 热榜去重：同一事件簇只保留热度最高的一条（入参已按热度倒序，首次出现即最高分）。
     * 仅在存在 cluster_id 时生效，老数据（cluster_id 为 null）原样保留。
     */
    private List<News> dedupByCluster(List<News> list, int topN) {
        if (list == null || list.isEmpty()) {
            return list;
        }
        Set<Long> seen = new HashSet<>();
        List<News> result = new ArrayList<>();
        for (News n : list) {
            Long cid = n.getClusterId();
            if (cid != null && !seen.add(cid)) {
                continue;
            }
            result.add(n);
            if (result.size() >= topN) {
                break;
            }
        }
        return result;
    }

    /**
     * 用户端：全部新闻网（来源）导航
     */
    @Override
    public List<Map<String, Object>> listSources() {
        List<Map<String, Object>> list = newsMapper.countSources();
        return list == null ? new ArrayList<>() : list;
    }

    /**
     * 计算一条新闻所属的"用户端展示日期"：
     * 优先取发布时间的日期，发布时间为空时回退为入库时间
     */
    private LocalDate resolveNewsDate(News news) {
        if (news.getPublishTime() != null) {
            return news.getPublishTime().toLocalDate();
        }
        if (news.getCreateTime() != null) {
            return news.getCreateTime().toLocalDate();
        }
        return LocalDate.now();
    }
}
