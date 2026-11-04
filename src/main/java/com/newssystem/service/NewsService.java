package com.newssystem.service;

import com.newssystem.common.PageResult;
import com.newssystem.common.SourceHotResult;
import com.newssystem.entity.News;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 新闻业务接口
 *
 * @author NewsSystem Dev
 */
public interface NewsService {

    /** 管理端新闻列表允许排序的字段白名单（同时供Controller入参校验与Service拼接SQL使用） */
    String[] ALLOWED_SORT_FIELDS = {"publish_time", "create_time", "id", "sentiment", "hot_score"};
    /** 默认排序字段：发布时间（按日期由新到旧） */
    String DEFAULT_SORT_FIELD = "publish_time";
    /** 默认排序方向：降序（最新发布的新闻排在最前） */
    String DEFAULT_SORT_ORDER = "desc";
    /** 列表单页最大条数：管理端列表一页展示全部新闻（不分页） */
    int MAX_PAGE_SIZE = 10000;

    /**
     * 分页查询新闻列表（管理端）
     *
     * @param pageNum    当前页（从1开始）
     * @param pageSize   每页大小
     * @param keyword    标题关键词（可选）
     * @param sources    新闻来源集合（可选，多选筛选；为空=全部来源，使用 IN 查询）
     * @param categories 新闻分类集合（可选，多选筛选；为空=全部分类，如财经/体育/科技）
     * @param sentiments 情感倾向集合（可选，多选筛选；为空=全部情感，1=正面，0=中性，-1=负面）
     * @param sortField  排序字段（可选，白名单：id默认/publish_time/create_time/hot_score/sentiment）
     * @param sortOrder  排序方向（可选，asc默认/desc）
     */
    PageResult<News> pageNews(Integer pageNum, Integer pageSize,
                               String keyword, List<String> sources, List<String> categories,
                               List<Integer> sentiments,
                               String sortField, String sortOrder);

    /**
     * 根据ID查询新闻详情
     */
    News getNewsById(Long id);

    /**
     * 删除新闻
     */
    boolean deleteNews(Long id);

    /**
     * 批量删除新闻（管理端）
     *
     * @param ids 待删除的新闻ID列表（自动忽略null/空白项；为空时返回false）
     * @return 实际删除的条数是否大于0
     */
    boolean deleteNewsBatch(List<Long> ids);

    /**
     * 用户端：查询全部新闻并按日期分组（条数与排序完全对齐管理端新闻列表，支持来源/分类/情感筛选）
     * 返回有序Map：key=新闻日期（LocalDate，倒序），value=当天新闻列表
     * 组内按发布时间由新到旧、时间相同按ID升序，与管理端默认排序一致。
     *
     * @param sources    新闻来源集合（可选，为空=全部来源，SQL IN 查询）
     * @param categories 新闻分类集合（可选，为空=全部分类；用户端为单选标签，至多1个值）
     * @param sentiments 情感倾向集合（可选，为空=全部情感，1正面/0中性/-1负面）
     * @param recentDays 时间范围天数（可选，为空或非正数=不限时间）
     */
    Map<LocalDate, List<News>> listGroupedByDate(List<String> sources,
                                                 List<String> categories,
                                                 List<Integer> sentiments,
                                                 Integer recentDays);

    /**
     * 用户端：查询"某新闻网（来源）某日热点排行榜"
     * 默认查询当天；当天无数据时自动回退为该新闻网最新一批热点。
     *
     * @param source 新闻网名称（不可为空）
     * @param date   指定日期（null=今天）
     */
    SourceHotResult listHotBySource(String source, LocalDate date);

    /**
     * 用户端：查询系统中全部新闻网（来源）导航列表
     * 每项包含 source（网站名）与 cnt（新闻数量）
     */
    List<Map<String, Object>> listSources();
}
