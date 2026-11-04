package com.newssystem.controller;

import com.newssystem.analyzer.NewsClassifier;
import com.newssystem.common.Result;
import com.newssystem.common.SourceHotResult;
import com.newssystem.entity.News;
import com.newssystem.entity.User;
import com.newssystem.service.NewsService;
import com.newssystem.service.UserLogService;
import com.newssystem.service.impl.UserServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 用户端控制器（免登录公开访问，游客可直接浏览）
 *
 * 用户端新闻列表：
 * 1) GET /client          新闻列表（按日期分组，右侧可按来源/情感多选筛选，与管理端一致）
 *    - 点击新闻标题：新窗口打开爬虫抓取的该新闻原文地址(news.url)
 *    - 点击来源标签：新窗口打开该新闻网官方网站（如"搜狐新闻"→搜狐新闻网）
 * 2) GET /client/hot?source=xxx  某新闻网今日热点排行榜（保留的二级页面）
 *
 * @author NewsSystem Dev
 */
@Controller
@RequestMapping("/client")
public class ClientController {

    private static final Logger log = LoggerFactory.getLogger(ClientController.class);

    /**
     * 新闻网名称 → 官方网站地址映射。
     * 用户端"来源"列点击后在新窗口打开对应新闻网首页（与爬虫 resolveSiteKey 的站点口径一致）。
     */
    private static final Map<String, String> SOURCE_HOME_URLS = new LinkedHashMap<>();
    static {
        SOURCE_HOME_URLS.put("新浪新闻", "https://news.sina.com.cn/");
        SOURCE_HOME_URLS.put("网易新闻", "https://news.163.com/");
        SOURCE_HOME_URLS.put("腾讯新闻", "https://news.qq.com/");
        SOURCE_HOME_URLS.put("搜狐新闻", "https://news.sohu.com/");
        SOURCE_HOME_URLS.put("凤凰新闻", "https://news.ifeng.com/");
        SOURCE_HOME_URLS.put("人民网", "http://www.people.com.cn/");
        SOURCE_HOME_URLS.put("新华网", "http://www.xinhuanet.com/");
        SOURCE_HOME_URLS.put("澎湃新闻", "https://www.thepaper.cn/");
    }

    @Autowired
    private NewsService newsService;

    @Autowired
    private UserLogService userLogService;

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

    /** 把登录状态放入模型：导航栏展示与浏览日志共用 */
    private static void addLoginState(Model model, javax.servlet.http.HttpSession session) {
        User loginUser = (User) session.getAttribute(UserServiceImpl.SESSION_USER_KEY);
        model.addAttribute("isLoggedIn", loginUser != null);
        model.addAttribute("loginUser", loginUser);
    }

    /**
     * 用户端新闻列表页（按日期分组，最新日期在前）
     * 支持与管理端一致的多来源/多情感筛选（同名参数重复，如 sources=新浪&sources=网易）
     * 分类为列表上方的单选标签栏（categories 至多1个值）
     * 游客免登录可直接访问
     */
    @GetMapping({"", "/", "/list"})
    public String newsList(@RequestParam(value = "sources", required = false) List<String> sources,
                           @RequestParam(value = "categories", required = false) List<String> categories,
                           @RequestParam(value = "sentiments", required = false) List<Integer> sentiments,
                           @RequestParam(value = "recentDays", required = false, defaultValue = "0") Integer recentDays,
                           Model model,
                           javax.servlet.http.HttpSession session) {
        // 入参清洗（去空白/去重），供查询与页面勾选回显共用
        List<String> selectedSources = cleanStrings(sources);
        List<String> selectedCategories = cleanStrings(categories);
        List<Integer> selectedSentiments = cleanInts(sentiments);

        // 时间范围白名单：0=全部时间（默认，与管理端全量展示一致），其余仅接受 3/7/15/30 天
        int days = (recentDays != null && (recentDays == 0 || recentDays == 3 || recentDays == 7
                || recentDays == 15 || recentDays == 30)) ? recentDays : 0;

        Map<LocalDate, List<News>> groups =
                newsService.listGroupedByDate(selectedSources, selectedCategories, selectedSentiments, days);
        model.addAttribute("groups", groups);
        // 时间范围回显（下拉框选中态）
        model.addAttribute("selectedDays", days);
        // 右侧筛选面板的来源选项（系统实际存在的新闻网 + 数量）与勾选回显
        model.addAttribute("sourceOptions", newsService.listSources());
        model.addAttribute("selectedSources", selectedSources);
        model.addAttribute("selectedCategories", selectedCategories);
        model.addAttribute("selectedSentiments", selectedSentiments);
        // 列表上方分类单选标签栏的选项（与管理端相同的10个分类）
        model.addAttribute("categoryOptions", NewsClassifier.ALL_CATEGORIES);
        // 来源列点击跳转所用的官网地址映射
        model.addAttribute("sourceHomeUrls", SOURCE_HOME_URLS);
        // 登录状态：仅用于导航栏展示与浏览日志记录，游客同样可点击标题查看新闻详情
        addLoginState(model, session);
        model.addAttribute("today", LocalDate.now());
        return "user_list";
    }

    /**
     * 新闻浏览中转接口：用户在用户端点击新闻标题时访问。
     * 作用：先在服务端记录"浏览新闻"用户日志（何时、看了哪条新闻、IP），
     * 再 302 重定向到该新闻的原始URL（爬虫抓取的来源页面），浏览器新标签页直接打开原文。
     *
     * 权限：游客与登录用户均可查看新闻详情；
     * 登录用户额外记录一条浏览日志，游客不记录（无用户身份）。
     *
     * @param id 新闻ID
     */
    @GetMapping("/news/view/{id}")
    public String viewNews(@PathVariable("id") Long id,
                           javax.servlet.http.HttpSession session) {
        News news = newsService.getNewsById(id);
        if (news == null) {
            return "redirect:/client";
        }
        // 仅登录用户记录行为日志：浏览新闻（详情中写明新闻标题，管理端可查看"何时看了哪条新闻"）
        User loginUser = (User) session.getAttribute(UserServiceImpl.SESSION_USER_KEY);
        if (loginUser != null) {
            userLogService.record(loginUser.getId(), loginUser.getUsername(),
                    UserLogService.ACTION_VIEW_NEWS, "浏览新闻《" + news.getTitle() + "》");
        }

        // 无原文地址的异常数据回退到用户端列表；正常情况 302 跳转新闻原文
        if (news.getUrl() == null || news.getUrl().trim().isEmpty()) {
            return "redirect:/client";
        }
        return "redirect:" + news.getUrl().trim();
    }

    /**
     * 用户端数据分析页（免登录可访问）
     * 复用管理端的分析模板与统计接口：新闻类型、来源、数量趋势、情感倾向 + 用户画像共8个图表。
     * 通过 clientMode 标记让模板渲染用户端导航（新闻浏览 / 数据分析 / 登录状态）。
     */
    @GetMapping("/analysis")
    public String analysisPage(Model model, javax.servlet.http.HttpSession session) {
        model.addAttribute("clientMode", true);
        addLoginState(model, session);
        return "analysis";
    }

    /**
     * 用户端事件聚类页（免登录可访问）
     * 复用管理端事件簇模板，通过 clientMode 标记渲染用户端导航。
     */
    @GetMapping("/clusters")
    public String clusterPage(Model model, javax.servlet.http.HttpSession session) {
        model.addAttribute("clientMode", true);
        addLoginState(model, session);
        return "event_cluster";
    }

    /**
     * 全站 / 指定新闻网的热点排行榜页面（用户端导航「热点榜单」入口）
     *
     * source 为空时展示全站聚合热榜；非空时只看该站点。
     * 页面顶部提供「全部站点 + 各站点」切换按钮。
     *
     * @param source 新闻网名称（可选，为空表示全站）
     * @param date   日期（可选，默认今天；传入历史日期可查看该日榜单）
     */
    @GetMapping("/hot")
    public String hotList(@RequestParam(value = "source", required = false) String source,
                          @RequestParam(value = "date", required = false)
                          @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
                          Model model,
                          javax.servlet.http.HttpSession session) {
        // 站点选项：来源列表（与用户端列表筛选面板同源，含各站点新闻数量）
        List<Map<String, Object>> sourceList = newsService.listSources();
        model.addAttribute("sourceList", sourceList);

        // source 为空表示全站热榜，不再默认取第一个站点；
        // 站点切换由页面按钮控制，"全部"对应不带 source 参数
        SourceHotResult result = newsService.listHotBySource(source, date);
        model.addAttribute("hotSource", result.getSource());
        model.addAttribute("requestDate", result.getRequestDate());
        model.addAttribute("hotList", result.getList());
        model.addAttribute("fallback", result.isFallback());
        model.addAttribute("actualDate", result.getActualDate());
        model.addAttribute("today", LocalDate.now());
        // 登录状态：Fragment 导航按登录态渲染用户区
        addLoginState(model, session);
        return "user_hot";
    }

    /**
     * JSON接口：查询某新闻网今日/指定日热点榜，便于前端异步加载或扩展使用
     */
    @GetMapping("/api/hot")
    @ResponseBody
    public Result<SourceHotResult> apiHotList(@RequestParam(value = "source", required = false) String source,
                                              @RequestParam(value = "date", required = false)
                                              @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        return Result.success(newsService.listHotBySource(source, date));
    }
}
