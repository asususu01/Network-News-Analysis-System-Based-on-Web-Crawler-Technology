package com.newssystem.controller;

import com.newssystem.common.Result;
import com.newssystem.entity.EventCluster;
import com.newssystem.entity.HotWord;
import com.newssystem.entity.News;
import com.newssystem.service.AnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/analysis")
public class AnalysisController {

    private static final Logger log = LoggerFactory.getLogger(AnalysisController.class);

    @Autowired
    private AnalysisService analysisService;

    @GetMapping({"/view", "/", ""})
    public String analysisPage() {
        return "analysis";
    }

    @GetMapping("/hotWords")
    @ResponseBody
    public Result<List<HotWord>> hotWords(
            @RequestParam(required = false, defaultValue = "7") Integer recentDays,
            @RequestParam(required = false, defaultValue = "20") Integer topN) {
        List<HotWord> list = analysisService.getTopHotWords(recentDays == null ? 7 : recentDays,
                topN == null ? 20 : topN);
        return Result.success(list);
    }

    @PostMapping("/doAnalyze")
    @ResponseBody
    public Result<Integer> doAnalyze(
            @RequestParam(required = false, defaultValue = "3") Integer recentDays) {
        int cnt = analysisService.doHotWordAnalysis(recentDays == null ? 3 : recentDays);
        return Result.success("分析完成，入库热词" + cnt + "条", cnt);
    }

    @GetMapping("/countBySource")
    @ResponseBody
    public Result<List<Map<String, Object>>> countBySource(
            @RequestParam(required = false, defaultValue = "7") Integer recentDays) {
        List<Map<String, Object>> list = analysisService.countBySource(recentDays == null ? 7 : recentDays);
        return Result.success(list);
    }

    /** 按新闻分类（类型）统计新闻数量（横向柱状图） */
    @GetMapping("/countByCategory")
    @ResponseBody
    public Result<List<Map<String, Object>>> countByCategory(
            @RequestParam(required = false, defaultValue = "7") Integer recentDays) {
        List<Map<String, Object>> list = analysisService.countByCategory(recentDays == null ? 7 : recentDays);
        return Result.success(list);
    }

    @GetMapping("/countByDate")
    @ResponseBody
    public Result<List<Map<String, Object>>> countByDate(
            @RequestParam(required = false, defaultValue = "15") Integer recentDays) {
        List<Map<String, Object>> list = analysisService.countByDate(recentDays == null ? 15 : recentDays);
        return Result.success(list);
    }

    @GetMapping("/countBySentiment")
    @ResponseBody
    public Result<List<Map<String, Object>>> countBySentiment(
            @RequestParam(required = false, defaultValue = "7") Integer recentDays) {
        List<Map<String, Object>> list = analysisService.countBySentiment(recentDays == null ? 7 : recentDays);
        return Result.success(list);
    }

    /** 情感走势（双轴折线图）：按天平均情感强度 + 报道量 */
    @GetMapping("/sentimentTrend")
    @ResponseBody
    public Result<List<Map<String, Object>>> sentimentTrend(
            @RequestParam(required = false, defaultValue = "15") Integer recentDays) {
        return Result.success(analysisService.sentimentTrend(recentDays == null ? 15 : recentDays));
    }

    /** 热词爆发榜：今日相对昨日增长100%以上的词 */
    @GetMapping("/burstWords")
    @ResponseBody
    public Result<List<Map<String, Object>>> burstWords(
            @RequestParam(required = false, defaultValue = "10") Integer topN) {
        return Result.success(analysisService.getBurstWords(topN == null ? 10 : topN));
    }

    /** 事件簇列表页面（管理端/用户端共用模板，clientMode 标记渲染用户端导航） */
    @GetMapping("/cluster/view")
    public String clusterPage(org.springframework.ui.Model model) {
        model.addAttribute("clientMode", false);
        return "event_cluster";
    }

    /** 事件簇列表（按报道数倒序）；minNewsCount=2 时只看多源报道 */
    @GetMapping("/clusters")
    @ResponseBody
    public Result<List<EventCluster>> clusters(
            @RequestParam(required = false, defaultValue = "7") Integer days,
            @RequestParam(required = false, defaultValue = "100") Integer limit,
            @RequestParam(required = false) Integer minNewsCount) {
        return Result.success(analysisService.listClusters(days == null ? 7 : days,
                limit == null ? 100 : limit, minNewsCount));
    }

    /** 聚类统计口径：区间内簇总数 / 多源簇数 / 覆盖新闻数 */
    @GetMapping("/cluster/stats")
    @ResponseBody
    public Result<Map<String, Object>> clusterStats(
            @RequestParam(required = false, defaultValue = "7") Integer days) {
        return Result.success(analysisService.clusterStats(days == null ? 7 : days));
    }

    /** 簇内所有报道（多源对比） */
    @GetMapping("/cluster/detail/{id}")
    @ResponseBody
    public Result<List<News>> clusterDetail(@PathVariable Long id) {
        return Result.success(analysisService.listClusterNews(id));
    }

    /** 实体热度榜数据（分类型 Top N + 共现对 + 覆盖统计） */
    @GetMapping("/entityGraph")
    @ResponseBody
    public Result<Map<String, Object>> entityGraph(
            @RequestParam(required = false, defaultValue = "7") Integer days) {
        return Result.success(analysisService.entityGraph(days == null ? 7 : days));
    }

    // ========== 用户数据统计接口 ==========

    /** 用户统计独立页面（管理端）：用户维度图表与新闻分析页分离，保持分析页主题一致 */
    @GetMapping("/user/view")
    public String userStatsPage() {
        return "user_stats";
    }

    /** 用户总数 */
    @GetMapping("/user/total")
    @ResponseBody
    public Result<Long> userTotal() {
        return Result.success(analysisService.countUsersTotal());
    }

    /** 用户性别分布 */
    @GetMapping("/user/byGender")
    @ResponseBody
    public Result<List<Map<String, Object>>> userByGender() {
        return Result.success(analysisService.countUsersByGender());
    }

    /** 用户职业分布 Top N */
    @GetMapping("/user/byOccupation")
    @ResponseBody
    public Result<List<Map<String, Object>>> userByOccupation(
            @RequestParam(required = false, defaultValue = "10") Integer topN) {
        return Result.success(analysisService.countUsersByOccupation(topN == null ? 10 : topN));
    }

    /** 用户年龄段分布 */
    @GetMapping("/user/byAgeGroup")
    @ResponseBody
    public Result<List<Map<String, Object>>> userByAgeGroup() {
        return Result.success(analysisService.countUsersByAgeGroup());
    }

    /** 用户注册趋势（最近N天） */
    @GetMapping("/user/byDate")
    @ResponseBody
    public Result<List<Map<String, Object>>> userByDate(
            @RequestParam(required = false, defaultValue = "15") Integer recentDays) {
        return Result.success(analysisService.countUsersByRegisterDate(recentDays == null ? 15 : recentDays));
    }
}
