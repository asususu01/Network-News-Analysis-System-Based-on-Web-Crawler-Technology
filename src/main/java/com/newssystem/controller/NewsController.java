package com.newssystem.controller;

import com.newssystem.analyzer.NewsClassifier;
import com.newssystem.common.PageResult;
import com.newssystem.common.Result;
import com.newssystem.entity.News;
import com.newssystem.service.AdminService;
import com.newssystem.service.NewsService;
import com.newssystem.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Controller
public class NewsController {

    private static final Logger log = LoggerFactory.getLogger(NewsController.class);

    @Autowired
    private NewsService newsService;

    @Autowired
    private AdminService adminService;

    @Autowired
    private UserService userService;

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    /**
     * 统一登录：根据账号自动判断身份。
     * 先尝试管理员账号（admin 表），命中则跳管理端 /index；
     * 否则尝试普通用户账号（user 表），命中则跳用户端 /client；
     * 两者都失败则返回登录页并提示账号或密码错误。
     */
    @PostMapping("/doLogin")
    public String unifiedLogin(@RequestParam String username,
                               @RequestParam String password,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {
        // 1. 优先匹配管理员
        if (adminService.login(username, password, session)) {
            log.info("统一登录：管理员 {} 登录成功，跳转管理端", username);
            return "redirect:/index";
        }
        // 2. 再匹配普通用户
        if (userService.login(username, password, session)) {
            log.info("统一登录：用户 {} 登录成功，跳转用户端", username);
            return "redirect:/client";
        }
        redirectAttributes.addFlashAttribute("errorMsg", "账号或密码错误");
        redirectAttributes.addFlashAttribute("username", username);
        return "redirect:/login";
    }

    @GetMapping({"/index", "/news/list"})
    public String index(@RequestParam(required = false, defaultValue = "1") Integer pageNum,
                        // 一次性展示全部新闻：单页条数取足够大的值，不再提供翻页
                        @RequestParam(required = false, defaultValue = "10000") Integer pageSize,
                        @RequestParam(required = false) String keyword,
                        // 表头"来源"下拉：可勾选单个或多个来源（同名参数重复，如 sources=新浪&sources=网易）
                        @RequestParam(required = false) List<String> sources,
                        // 右侧"分类"筛选：可勾选一个或多个新闻分类（财经/体育/科技等）
                        @RequestParam(required = false) List<String> categories,
                        // 表头"情感"下拉：可勾选一个或多个情感类型（1正面/0中性/-1负面）
                        @RequestParam(required = false) List<Integer> sentiments,
                        @RequestParam(required = false) String sortField,
                        @RequestParam(required = false) String sortOrder,
                        Model model) {
        // 入参清洗（去空白/去重），供查询与页面勾选回显共用
        List<String> selectedSources = cleanStrings(sources);
        List<String> selectedCategories = cleanStrings(categories);
        List<Integer> selectedSentiments = cleanInts(sentiments);

        PageResult<News> page = newsService.pageNews(pageNum, pageSize, keyword,
                selectedSources, selectedCategories, selectedSentiments, sortField, sortOrder);
        model.addAttribute("page", page);
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        // 表头下拉的勾选项回显
        model.addAttribute("selectedSources", selectedSources);
        model.addAttribute("selectedCategories", selectedCategories);
        model.addAttribute("selectedSentiments", selectedSentiments);
        // 分类筛选选项：系统固定的10个新闻分类
        model.addAttribute("categoryOptions", NewsClassifier.ALL_CATEGORIES);
        // 来源下拉选项：系统中实际存在的全部新闻网（含数量）
        model.addAttribute("sourceOptions", newsService.listSources());
        // 排序参数回显：与Service白名单归一化结果保持一致
        model.addAttribute("sortField", normalizeSortField(sortField));
        model.addAttribute("sortOrder", normalizeSortOrder(sortOrder));
        return "index";
    }

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

    /** 排序字段白名单归一化（复用NewsService白名单常量） */
    private String normalizeSortField(String sortField) {
        for (String allowed : NewsService.ALLOWED_SORT_FIELDS) {
            if (allowed.equals(sortField)) {
                return allowed;
            }
        }
        return NewsService.DEFAULT_SORT_FIELD;
    }

    /** 排序方向归一化：asc/desc 原样返回（用于页面箭头回显），为空或非法时回退默认方向 */
    private String normalizeSortOrder(String sortOrder) {
        return "asc".equalsIgnoreCase(sortOrder) || "desc".equalsIgnoreCase(sortOrder)
                ? sortOrder.toLowerCase() : NewsService.DEFAULT_SORT_ORDER;
    }

    @GetMapping("/api/news/page")
    @ResponseBody
    public Result<PageResult<News>> apiPageNews(
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            // 支持重复参数做多来源/多分类/多情感筛选：sources=新浪&sources=网易
            @RequestParam(required = false) List<String> sources,
            @RequestParam(required = false) List<String> categories,
            @RequestParam(required = false) List<Integer> sentiments,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder) {
        PageResult<News> page = newsService.pageNews(pageNum, pageSize, keyword,
                sources, categories, sentiments, sortField, sortOrder);
        return Result.success(page);
    }

    @GetMapping("/api/news/{id}")
    @ResponseBody
    public Result<News> apiGetNews(@PathVariable Long id) {
        News news = newsService.getNewsById(id);
        if (news == null) {
            return Result.fail("新闻不存在");
        }
        return Result.success(news);
    }

    @PostMapping("/api/news/delete")
    @ResponseBody
    public Result<Void> apiDeleteNews(@RequestParam Long id) {
        boolean ok = newsService.deleteNews(id);
        return ok ? Result.success() : Result.fail("删除失败");
    }

    /**
     * 批量删除新闻（管理端）
     * 前端以表单参数提交逗号分隔的ID串，如 ids=1,2,3
     */
    @PostMapping("/api/news/deleteBatch")
    @ResponseBody
    public Result<Void> apiDeleteNewsBatch(@RequestParam String ids) {
        if (ids == null || ids.trim().isEmpty()) {
            return Result.fail("请先勾选要删除的新闻");
        }
        List<Long> idList = new ArrayList<>();
        for (String s : ids.split(",")) {
            if (s == null || s.trim().isEmpty()) {
                continue;
            }
            try {
                idList.add(Long.parseLong(s.trim()));
            } catch (NumberFormatException e) {
                return Result.fail("非法的新闻ID：" + s);
            }
        }
        boolean ok = newsService.deleteNewsBatch(idList);
        return ok ? Result.success() : Result.fail("批量删除失败");
    }
}
