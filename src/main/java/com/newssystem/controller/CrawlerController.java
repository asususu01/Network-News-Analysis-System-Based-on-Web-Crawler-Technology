package com.newssystem.controller;

import com.newssystem.common.Result;
import com.newssystem.entity.CrawlerSite;
import com.newssystem.service.CrawlerService;
import com.newssystem.service.CrawlerSiteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/crawler")
public class CrawlerController {

    private static final Logger log = LoggerFactory.getLogger(CrawlerController.class);

    @Autowired
    private CrawlerService crawlerService;

    @Autowired
    private CrawlerSiteService crawlerSiteService;

    @GetMapping({"/manage", "/", ""})
    public String managePage(Model model) {
        // 页面渲染时把当前全部爬取站点交给下拉列表（避免首屏再发一次AJAX）
        model.addAttribute("sites", crawlerSiteService.listAll());
        return "crawler_manage";
    }

    /**
     * 启动爬虫。
     *
     * @param maxPerSite 每个种子站点本次爬取的新闻条数，由页面下拉选择，
     *                   仅允许 5/10/20/30/40/50，未传或非法时后端回退为默认5条
     * @param siteIds    选中的爬取站点ID，多个用逗号分隔；不传或空串表示爬取全部站点
     */
    @PostMapping("/start")
    @ResponseBody
    public Result<String> start(@RequestParam(required = false) Integer maxPerSite,
                                @RequestParam(required = false) String siteIds) {
        List<Long> idList = parseSiteIds(siteIds);
        boolean ok = crawlerService.startCrawler(maxPerSite, idList);
        return ok ? Result.success("爬虫启动成功", null)
                : Result.fail("爬虫已经在运行中，或没有可爬取的站点");
    }

    @PostMapping("/stop")
    @ResponseBody
    public Result<String> stop() {
        boolean ok = crawlerService.stopCrawler();
        return ok ? Result.success("已发出停止指令", null)
                : Result.fail("爬虫当前未运行");
    }

    @GetMapping("/status")
    @ResponseBody
    public Result<Map<String, Object>> status() {
        Map<String, Object> status = crawlerService.getStatus();
        return Result.success(status);
    }

    // ==================== 爬取站点管理 ====================

    /** 查询全部爬取站点（添加/删除站点后刷新下拉列表用） */
    @GetMapping("/sites")
    @ResponseBody
    public Result<List<CrawlerSite>> listSites() {
        List<CrawlerSite> sites = crawlerSiteService.listAll();
        // 敏感程度低，但仍显式不返回多余字段；这里实体本身只有id/name/url/内置标记
        return Result.success(sites);
    }

    /**
     * 新增自定义爬取站点。
     *
     * @param name 站点名称（必填）
     * @param url  站点种子网址（必填，后端校验http/https格式与重复性）
     */
    @PostMapping("/site/add")
    @ResponseBody
    public Result<CrawlerSite> addSite(@RequestParam String name,
                                       @RequestParam String url) {
        try {
            CrawlerSite site = crawlerSiteService.addSite(name, url);
            return Result.success("站点添加成功", site);
        } catch (IllegalArgumentException e) {
            // 业务校验失败（名称/URL不合法或重复），消息直接回显给管理员
            return Result.fail(e.getMessage());
        }
    }

    /** 删除指定爬取站点 */
    @PostMapping("/site/delete")
    @ResponseBody
    public Result<String> deleteSite(@RequestParam Long id) {
        if (id == null || id <= 0) {
            return Result.fail("站点ID不合法");
        }
        boolean ok = crawlerSiteService.deleteSite(id);
        return ok ? Result.success("站点已删除", null)
                : Result.fail("站点不存在或已被删除");
    }

    /**
     * 将页面传入的逗号分隔站点ID字符串解析为Long列表。
     * 忽略空白项与非数字项；显式限制数量防止伪造超长参数。
     */
    private List<Long> parseSiteIds(String siteIds) {
        if (siteIds == null || siteIds.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return Arrays.stream(siteIds.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::valueOf)
                    .filter(id -> id > 0)
                    .distinct()
                    .limit(50)
                    .collect(Collectors.toList());
        } catch (NumberFormatException e) {
            log.warn("站点ID参数格式非法：{}", siteIds);
            return Collections.emptyList();
        }
    }
}
