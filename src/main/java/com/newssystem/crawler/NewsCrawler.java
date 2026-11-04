package com.newssystem.crawler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.newssystem.analyzer.DataCleaner;
import com.newssystem.analyzer.EntityExtractor;
import com.newssystem.analyzer.EventClusterer;
import com.newssystem.analyzer.HotScoreCalculator;
import com.newssystem.analyzer.NewsClassifier;
import com.newssystem.analyzer.SimpleSentimentAnalyzer;
import com.newssystem.analyzer.TextRankSummarizer;
import com.newssystem.entity.News;
import com.newssystem.mapper.NewsMapper;
import com.newssystem.util.ArticleUrlDetector;
import com.newssystem.util.HashUtil;
import com.newssystem.util.HttpClientUtil;
import com.newssystem.util.JsoupUtil;
import org.apache.ibatis.exceptions.PersistenceException;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 新闻爬虫核心组件。
 *
 * 采集策略（多线程BFS + 每站点独立配额）：
 * 1) 每个种子站点（新浪/网易/搜狐…）独立计数，最多入库 {@code crawler.max-news-per-site}
 *    条新闻（默认5条；手动任务可在爬虫管理页下拉选择5~50条），互不挤占配额；
 * 2) 只跟随与父页面同站点的链接，避免从一个门户爬到无关外站；
 * 3) 链接在父页面上的位置序号(position)与BFS深度(depth)随任务透传，
 *    作为入库时热度得分 {@link HotScoreCalculator} 的计算依据；
 * 4) 种子页强制视为列表页（首页本身不当作新闻入库），只从中抽取子链接；
 * 5) 全局仍保留 {@code crawler.max-news-per-task} 作为页面抓取总量安全上限。
 *
 * @author NewsSystem Dev
 */
@Component
public class NewsCrawler {

    private static final Logger log = LoggerFactory.getLogger(NewsCrawler.class);

    /** BFS最大深度：种子页=0，新闻文章深度1~2，超过2层不再扩散 */
    private static final int MAX_CRAWL_DEPTH = 2;
    /** 每个列表页最多入队的同站文章链接数（位置序号即热度依据，取页面靠前文章） */
    private static final int MAX_ARTICLE_LINKS_PER_PAGE = 30;
    /** 每个列表页最多入队的同站频道链接数（仅用于扩散一层发现文章） */
    private static final int MAX_CHANNEL_LINKS_PER_PAGE = 10;

    @Autowired
    private HttpClientUtil httpClientUtil;

    @Autowired
    private UrlDeduplication urlDeduplication;

    @Autowired
    private CrawlerTaskManager taskManager;

    @Autowired
    private CrawlerThreadPool threadPool;

    @Autowired
    private NewsMapper newsMapper;

    @Autowired
    private DataCleaner dataCleaner;

    @Autowired
    private SimpleSentimentAnalyzer sentimentAnalyzer;

    @Autowired
    private HotScoreCalculator hotScoreCalculator;

    @Autowired
    private NewsClassifier newsClassifier;

    /** 事件聚类器：计算 SimHash 指纹并分配事件簇ID */
    @Autowired
    private EventClusterer eventClusterer;

    /** 实体抽取器：规则+词典抽取机构/人名/地点，写入 news_entity */
    @Autowired
    private EntityExtractor entityExtractor;

    /** TextRank 摘要器：为新闻生成抽取式摘要 */
    @Autowired
    private TextRankSummarizer textRankSummarizer;

    /** 单次任务每个网站最多入库的新闻条数（配置默认值，定时任务使用） */
    @Value("${crawler.max-news-per-site:5}")
    private int maxNewsPerSite;

    /**
     * 本次手动任务每站点实际生效的入库配额（由爬虫管理页下拉选择，范围5~50）。
     * 同一时刻仅允许一个爬虫任务运行（{@link CrawlerTaskManager#isRunning()} 守卫），
     * 因此在 startCrawl 入口重置该字段是安全的。
     */
    private volatile int effectiveMaxPerSite = 5;

    /**
     * 本次任务"站点标识 -> 管理员配置的站点名称"映射。
     * 自定义站点的域名无法被 {@link #resolveSiteKey} 识别时，其入库新闻 source
     * 仍使用管理员填写的名称；每次任务开始时重置（单任务运行，无并发问题）。
     */
    private final java.util.Map<String, String> taskSiteNames = new java.util.concurrent.ConcurrentHashMap<>();

    /** 单次任务抓取页面总数的全局安全上限 */
    @Value("${crawler.max-news-per-task:100}")
    private int maxNewsPerTask;

    @Value("${crawler.request-delay-min:500}")
    private int delayMin;

    @Value("${crawler.request-delay-max:1500}")
    private int delayMax;

    /** 已提交的页面抓取任务数（全局安全计数） */
    private final AtomicInteger submittedCounter = new AtomicInteger(0);

    /** 每个站点本次任务已成功入库的新闻条数 */
    private final java.util.Map<String, AtomicInteger> siteSavedCounters =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 爬取任务队列元素：URL + 所属站点 + BFS深度 + 在父页面上的链接位置。
     */
    private static class CrawlTarget {
        final String url;
        final String siteKey;
        final int depth;
        final int linkPosition;

        CrawlTarget(String url, String siteKey, int depth, int linkPosition) {
            this.url = url;
            this.siteKey = siteKey;
            this.depth = depth;
            this.linkPosition = linkPosition;
        }
    }

    /**
     * 爬取种子（网址 + 管理员配置的站点名称）。
     * name 为 null 时来源名回退为 {@link #resolveSiteKey} 的识别结果。
     */
    public static class Seed {
        private final String url;
        private final String name;

        public Seed(String url, String name) {
            this.url = url;
            this.name = name;
        }

        public String getUrl() { return url; }
        public String getName() { return name; }
    }

    /**
     * 兼容入口：使用 application.yml 配置的默认每站配额启动（定时任务调用）。
     */
    public void startCrawl(List<String> seedUrls, String triggerType) {
        startCrawl(seedUrls, triggerType, maxNewsPerSite);
    }

    /**
     * 兼容入口：仅种子URL列表（定时任务/旧调用），站点名称按域名自动识别。
     */
    public void startCrawl(List<String> seedUrls, String triggerType, int maxPerSite) {
        List<Seed> seeds = new java.util.ArrayList<>();
        if (seedUrls != null) {
            for (String u : seedUrls) {
                seeds.add(new Seed(u, null));
            }
        }
        startCrawlWithNames(seeds, triggerType, maxPerSite);
    }

    /**
     * 启动爬虫任务（携带管理员配置的站点名称）。
     *
     * @param seeds       种子列表（URL + 站点名称）
     * @param triggerType 触发方式描述（手动触发/定时触发）
     * @param maxPerSite  每个种子站点本次任务最多入库的新闻条数（页面下拉选择，允许范围5~50，
     *                    非法值回退到配置默认值5）
     */
    public void startCrawlWithNames(List<Seed> seeds, String triggerType, int maxPerSite) {
        if (seeds == null || seeds.isEmpty()) {
            log.warn("种子URL列表为空，爬虫任务取消");
            return;
        }
        if (taskManager.isRunning()) {
            log.warn("爬虫任务正在运行中，本次启动请求忽略");
            return;
        }

        // 本次任务每站点配额：只接受5~50之间的整数，其余回退配置默认值
        int quota = (maxPerSite >= 5 && maxPerSite <= 50) ? maxPerSite : maxNewsPerSite;
        this.effectiveMaxPerSite = quota;

        taskManager.startTask(triggerType);
        urlDeduplication.clear();
        submittedCounter.set(0);
        siteSavedCounters.clear();
        // 重置本次任务的自定义站点名称映射
        taskSiteNames.clear();

        LinkedBlockingQueue<CrawlTarget> urlQueue = new LinkedBlockingQueue<>();
        for (Seed seed : seeds) {
            String url = seed.getUrl();
            if (url == null || url.trim().isEmpty()) {
                continue;
            }
            String trimUrl = url.trim();
            String siteKey = resolveSiteKey(trimUrl);
            // 管理员配置了名称时登记映射（同key多名称时以后登记的为准）
            if (seed.getName() != null && !seed.getName().trim().isEmpty()) {
                taskSiteNames.put(siteKey, seed.getName().trim());
            }
            // 预登记各站点计数器，配额判定只针对这些种子站点
            siteSavedCounters.putIfAbsent(siteKey, new AtomicInteger(0));
            urlQueue.offer(new CrawlTarget(trimUrl, siteKey, 0, 0));
            urlDeduplication.addUrl(trimUrl);
        }
        taskManager.incrementTotal(urlQueue.size());
        // 全局页面抓取安全上限随配额放大：每个入库名额预留3个页面抓取预算
        // （文章页+频道扩散页+失败重试），保证选50条/站时不会被默认100的上限提前截断
        final int effectiveTaskPageCap = Math.max(maxNewsPerTask,
                siteSavedCounters.size() * quota * 3 + 20);
        log.info("爬虫任务启动：{}个种子站点，每站点配额{}条，全局页面抓取上限{}",
                siteSavedCounters.size(), quota, effectiveTaskPageCap);
        taskManager.appendLog("本次任务爬取站点（" + siteSavedCounters.size()
                + "个）：" + siteDisplayNameSummary());
        taskManager.appendLog("本次任务每站点采集配额：" + quota + " 条");

        final int submitIntervalMs = 200;
        try {
            while (taskManager.isRunning()
                    && submittedCounter.get() < effectiveTaskPageCap
                    && anySiteHasQuota()
                    && (!urlQueue.isEmpty() || threadPool.getActiveCount() > 0)) {

                CrawlTarget target;
                // 丢弃已满额站点的排队任务，只提交仍有配额站点的页面
                while ((target = urlQueue.poll()) != null
                        && submittedCounter.get() < effectiveTaskPageCap
                        && taskManager.isRunning()) {

                    if (getSiteSaved(target.siteKey) >= quota) {
                        // 该站点已满额，直接跳过其排队URL
                        continue;
                    }

                    final CrawlTarget theTarget = target;
                    submittedCounter.incrementAndGet();
                    threadPool.submit(new Runnable() {
                        @Override
                        public void run() {
                            crawlOnePage(theTarget, urlQueue);
                        }
                    });

                    sleepRandom(delayMin / 2, delayMax / 2);
                }

                TimeUnit.MILLISECONDS.sleep(submitIntervalMs);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("爬虫主循环被中断");
        } catch (Exception e) {
            log.error("爬虫主循环异常:", e);
        }

        log.info("爬虫主循环结束，等待剩余子任务完成...");
        int waitTimes = 0;
        while (taskManager.isRunning() && threadPool.getActiveCount() > 0 && waitTimes < 60) {
            try {
                TimeUnit.SECONDS.sleep(1);
                waitTimes++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        log.info("各站点入库统计：{}", siteSavedSummary());
        taskManager.appendLog("各站点入库统计：" + siteSavedSummary());

        // 任务结束前汇总每个站点的入库情况，识别失败站点并推断原因，
        // 供前端爬虫管理页结束后弹窗展示。
        java.util.List<CrawlerTaskManager.SiteFailureInfo> all = new java.util.ArrayList<>();
        java.util.List<CrawlerTaskManager.SiteFailureInfo> failures = new java.util.ArrayList<>();
        // 全部站点的日志（用于失败原因推断）
        String fullLog = taskManager.getLatestLogText();
        for (java.util.Map.Entry<String, java.util.concurrent.atomic.AtomicInteger> e : siteSavedCounters.entrySet()) {
            String siteKey = e.getKey();
            String siteDisplay = taskSiteNames.getOrDefault(siteKey, siteKey);
            int saved = e.getValue().get();
            boolean failed = saved == 0;
            String reason = "";
            if (failed) {
                reason = inferFailureReason(siteDisplay, siteKey, fullLog);
            }
            CrawlerTaskManager.SiteFailureInfo info =
                    new CrawlerTaskManager.SiteFailureInfo(siteDisplay, saved, reason, failed);
            all.add(info);
            if (failed) failures.add(info);
        }
        taskManager.setSiteSummaries(all, failures);

        // 任务结束后清理没有新闻的空事件簇（新闻可能因重复/删除未落入簇）
        eventClusterer.cleanEmptyClusters();

        taskManager.stopTask();
    }

    /**
     * 推断某站点"入库量为 0"的具体失败原因。
     * 依据本次任务的实时日志，按优先级匹配最具诊断性的失败线索：
     *
     * 1. 首页/种子页只识别到频道链接、没有任何文章 → 可能 JS 动态渲染
     *    （腾讯新闻 news.qq.com、今日头条等典型特征）
     * 2. 种子页直接抓取失败（空页面 / HTTP 错误 / HTML 解析错误）
     * 3. 能识别出文章链接但正文普遍过短（≤50字）被判空 → 反爬/正文结构特殊
     * 4. 兜底：未能识别到可入库文章
     */
    private String inferFailureReason(String siteDisplay, String siteKey, String fullLog) {
        if (fullLog == null || fullLog.isEmpty()) {
            return "爬虫未能识别到该网站可入库的新闻文章";
        }
        int emptyArticleInQueue = 0;   // "新入队[site]链接：文章0条" 的行数
        int totalEnqueueLines = 0;     // 本站"新入队[...]"日志总行数
        int totalEmptyPage = 0;
        int totalParseErr = 0;
        int totalCrawlFailed = 0;
        boolean touched = false;       // 本站是否有任何日志行（入队/抓取/入库）出现
        for (String line : fullLog.split("\\r?\\n")) {
            if (line == null || line.isEmpty()) continue;
            boolean mine = line.contains("[" + siteDisplay + "]")
                    || line.contains(siteKey);
            if (!mine) continue;
            touched = true;
            // 入队日志行形如 "[2026-09-27T...] 新入队[站点名]链接：文章X条 + 频道Y个"
            if (line.contains("新入队[") && line.contains("链接：")) {
                totalEnqueueLines++;
                if (line.contains("文章0条")) {
                    emptyArticleInQueue++;
                }
            }
            if (line.contains("失败(空页面)")) {
                totalEmptyPage++;
                totalCrawlFailed++;
            }
            if (line.contains("失败(解析HTML错误)")) {
                totalParseErr++;
                totalCrawlFailed++;
            }
            if (line.contains("空页面")) {
                totalCrawlFailed++;
            }
        }
        // 优先级1：所有入队都是"文章0条"——典型 JS 动态渲染
        // （腾讯 news.qq.com、今日头条等，HttpClient+Jsoup 不执行 JS，
        //    抓到的空壳首页里没有任何 <a href="...article...">）
        if (totalEnqueueLines > 0 && emptyArticleInQueue == totalEnqueueLines
                && totalEnqueueLines > 0) {
            return "首页未识别到新闻文章链接，页面可能为JavaScript动态渲染";
        }
        // 优先级2：识别到文章链接但全部因正文过短被丢弃
        if (totalEnqueueLines > 0 && emptyArticleInQueue == 0) {
            return "识别到文章链接但正文普遍过短（少于50字），可能被反爬返回空壳或页面结构特殊";
        }
        // 优先级3：页面抓取失败
        if (totalCrawlFailed > 0) {
            if (totalEmptyPage >= totalParseErr && totalEmptyPage > 0) {
                return totalEmptyPage + " 个页面内容为空或连接失败（请检查网址是否正确，或该网站是否限制了爬虫）";
            }
            if (totalParseErr > 0) {
                return "HTML解析失败 " + totalParseErr + " 次（该网站页面结构可能特殊）";
            }
            return totalCrawlFailed + " 个页面抓取失败（请检查网站是否可正常访问）";
        }
        // 兜底：本站没有任何实际入队/抓取日志
        if (!touched) {
            return "爬虫未能识别到该网站可入库的新闻文章，请检查网址是否为新闻频道首页";
        }
        return "爬虫未能识别到该网站可入库的新闻文章";
    }

    private void crawlOnePage(CrawlTarget target, LinkedBlockingQueue<CrawlTarget> urlQueue) {
        String url = target.url;
        String siteKey = target.siteKey;
        try {
            if (!taskManager.isRunning()) {
                return;
            }
            // 二次校验配额：任务排队期间该站点可能已采满
            if (getSiteSaved(siteKey) >= effectiveMaxPerSite) {
                return;
            }

            sleepRandom(delayMin, delayMax);

            taskManager.appendLog("开始抓取：" + url);

            String html = httpClientUtil.doGet(url);
            if (html == null || html.isEmpty()) {
                taskManager.incrementFail();
                taskManager.appendLog("失败(空页面)：" + url);
                return;
            }

            Document doc = JsoupUtil.parseHtml(html, url);
            if (doc == null) {
                taskManager.incrementFail();
                taskManager.appendLog("失败(解析HTML错误)：" + url);
                return;
            }

            String title = JsoupUtil.extractTitle(doc);
            String content = JsoupUtil.extractContent(doc);
            LocalDateTime publishTime = JsoupUtil.extractPublishTime(doc);
            // 来源：优先使用管理员在爬虫管理页配置的站点名称（自定义站点），
            // 未配置时回退为按域名判定的站点标识（新浪新闻/网易新闻/搜狐新闻），
            // 不采用页面 og:site_name 等自称名（如"手机新浪网"），保证"每站N条"
            // 配额与客户端按来源分组/筛选口径一致
            String source = taskSiteNames.getOrDefault(siteKey, siteKey);

            // 入库三条件：①URL必须是新闻文章形态（排除频道首页/导航/专题等落地页）
            // ②标题非空 ③正文长度达标；种子页(depth=0)不入库
            boolean isDetailPage = target.depth > 0
                    && ArticleUrlDetector.isArticleUrl(url)
                    && title != null && !title.isEmpty()
                    && content != null && content.length() >= 100;

            if (isDetailPage) {
                boolean saved = saveNewsToDb(title, url, source, publishTime, content,
                        siteKey, target.linkPosition, target.depth);
                if (saved) {
                    taskManager.incrementSuccess();
                    String dispTitle = title.length() > 20 ? title.substring(0, 20) + "..." : title;
                    String siteDisplay = taskSiteNames.getOrDefault(siteKey, siteKey);
                    taskManager.appendLog("入库成功[" + siteDisplay + " " + getSiteSaved(siteKey)
                            + "/" + effectiveMaxPerSite + "]：《" + dispTitle + "》");
                }
            } else {
                taskManager.appendLog("识别为列表页/首页，开始抽取同站子链接：" + url);
                enqueueSubLinks(doc, url, siteKey, target.depth, urlQueue);
            }

        } catch (Exception e) {
            taskManager.incrementFail();
            taskManager.appendLog("异常：" + url + " -> " + e.getClass().getSimpleName() + ":" + e.getMessage());
            log.error("抓取页面异常 url={}", url, e);
        }
    }

    /**
     * 抽取列表页上的同站链接并入队，分两类处理：
     * 1) 文章链接（{@link ArticleUrlDetector} 识别）：规范化去除跟踪参数后入队，
     *    按页面出现顺序赋予位置序号（热度排序依据），每页最多
     *    {@link #MAX_ARTICLE_LINKS_PER_PAGE} 条；
     * 2) 频道链接：非文章形态的同站链接（如 mil.news.sina.com.cn/ 频道首页），
     *    仅从种子页(depth=0)扩散一层，用于发现更多文章，每页最多
     *    {@link #MAX_CHANNEL_LINKS_PER_PAGE} 条，避免在导航页间漂移。
     * 深度达到 {@link #MAX_CRAWL_DEPTH} 后不再扩散任何链接。
     */
    private void enqueueSubLinks(Document doc, String pageUrl, String siteKey,
                                 int parentDepth, LinkedBlockingQueue<CrawlTarget> urlQueue) {
        if (parentDepth >= MAX_CRAWL_DEPTH) {
            return;
        }
        List<String> subLinks = JsoupUtil.extractNewsLinks(doc, pageUrl, null);

        int articlePosition = 0;
        int articleAdded = 0;
        int channelAdded = 0;
        // 频道链接第二遍处理时使用，先收集可避免打乱文章位置序号
        List<String> channelCandidates = new java.util.ArrayList<>();

        for (String rawLink : subLinks) {
            // 同站过滤：链接所属站点必须与当前种子站点一致
            if (!siteKey.equals(resolveSiteKey(rawLink))) {
                continue;
            }

            if (ArticleUrlDetector.isArticleUrl(rawLink)) {
                if (articleAdded >= MAX_ARTICLE_LINKS_PER_PAGE) {
                    continue;
                }
                // 规范化：去掉 ?scm= / ?clickfrom= 等跟踪参数，防止同文重复
                String link = ArticleUrlDetector.normalizeArticleUrl(rawLink);
                if (urlDeduplication.isDuplicated(link)) {
                    continue;
                }
                urlDeduplication.addUrl(link);
                if (urlQueue.offer(new CrawlTarget(link, siteKey, parentDepth + 1, articlePosition))) {
                    articlePosition++;
                    articleAdded++;
                }
            } else if (parentDepth == 0 && channelAdded < MAX_CHANNEL_LINKS_PER_PAGE) {
                // 只有种子页允许扩散频道链接（一层），且跳过带锚点的纯导航链接
                if (rawLink.contains("#")) {
                    continue;
                }
                if (!urlDeduplication.isDuplicated(rawLink)) {
                    channelCandidates.add(rawLink);
                }
            }
        }

        // 入队频道链接（位置序号对频道页无意义，赋0；其页面上的文章再从0编号）
        for (String channel : channelCandidates) {
            if (channelAdded >= MAX_CHANNEL_LINKS_PER_PAGE) {
                break;
            }
            urlDeduplication.addUrl(channel);
            if (urlQueue.offer(new CrawlTarget(channel, siteKey, parentDepth + 1, 0))) {
                channelAdded++;
            }
        }

        taskManager.incrementTotal(articleAdded + channelAdded);
        taskManager.appendLog("新入队[" + taskSiteNames.getOrDefault(siteKey, siteKey)
                + "]链接：文章" + articleAdded + "条 + 频道" + channelAdded + "个");
    }

    /**
     * 保存新闻到数据库，含"配额预留-失败释放"机制与热度打分。
     */
    private boolean saveNewsToDb(String title, String url, String source,
                                 LocalDateTime publishTime, String content,
                                 String siteKey, int linkPosition, int depth) {
        AtomicInteger counter = siteSavedCounters.computeIfAbsent(siteKey, k -> new AtomicInteger(0));

        // 预留一个该站点的名额
        int reservedSeq;
        while (true) {
            int current = counter.get();
            if (current >= effectiveMaxPerSite) {
                taskManager.appendLog("站点[" + siteKey + "]已满" + effectiveMaxPerSite
                        + "条，丢弃：" + url);
                return false;
            }
            if (counter.compareAndSet(current, current + 1)) {
                reservedSeq = current + 1;
                break;
            }
        }

        boolean committed = false;
        try {
            String cleanTitle = dataCleaner.cleanText(title);
            String cleanContent = dataCleaner.cleanText(content);

            if (cleanTitle == null || cleanTitle.isEmpty() || cleanContent == null || cleanContent.length() < 50) {
                taskManager.appendLog("清洗后内容不合格，跳过入库");
                return false;
            }

            // 内容级去重：同来源、同标题、24小时内已入库则跳过。
            // URL级去重无法覆盖同一文章被不同栏目页以不同URL链接的情况，此处用标题做兜底。
            if (newsMapper.countSameTitle(source, cleanTitle, LocalDateTime.now().minusHours(24)) > 0) {
                taskManager.incrementDuplicate();
                taskManager.appendLog("同源同标题已存在(跳过)：" + cleanTitle);
                return false;
            }

            String urlHash = HashUtil.md5(url);
            // 情感：先算连续强度值(-100~100)，再映射为三档极性，两个字段同时入库
            int sentimentScore = sentimentAnalyzer.analyzeScore(cleanTitle, cleanContent);
            int sentiment = sentimentAnalyzer.toPolarity(sentimentScore);
            // 热度得分：链接页面位置(60) + 标题特征(25) + 正文质量(15)
            int hotScore = hotScoreCalculator.calculate(cleanTitle, cleanContent, linkPosition, depth);
            // 新闻分类：URL频道特征 + 标题/正文关键词自动判定
            String category = newsClassifier.classify(url, cleanTitle, cleanContent);
            // TextRank 抽取式摘要
            String summary = textRankSummarizer.summarize(cleanContent);

            News news = new News();
            news.setTitle(cleanTitle);
            news.setUrl(url);
            news.setUrlHash(urlHash);
            news.setSource(source);
            news.setCategory(category);
            news.setPublishTime(publishTime == null ? LocalDateTime.now() : publishTime);
            news.setContent(cleanContent);
            news.setSentiment(sentiment);
            news.setSentimentScore(sentimentScore);
            news.setHotScore(hotScore);
            news.setSummary(summary);
            news.setCreateTime(LocalDateTime.now());
            // 入库前计算 SimHash 指纹（4段分桶值一并入库），聚类在入库后进行
            java.util.Map<String, Integer> simFreq = eventClusterer.fillFingerprint(news);

            newsMapper.insert(news);
            committed = true;
            // 入库后的智能分析：事件聚类 + 实体抽取。失败不影响新闻入库，只记录日志
            analyzeAfterInsert(news, simFreq);
            taskManager.appendLog("热度评分=" + hotScore + " 分类=" + category + "（站点第" + reservedSeq + "条）：《"
                    + (cleanTitle.length() > 16 ? cleanTitle.substring(0, 16) + "..." : cleanTitle) + "》");
            return true;

        } catch (DuplicateKeyException e) {
            taskManager.incrementDuplicate();
            taskManager.appendLog("数据库重复(跳过)：" + url);
            return false;
        } catch (PersistenceException e) {
            Throwable cause = e.getCause();
            if (cause instanceof DuplicateKeyException
                    || (cause != null && cause.getMessage() != null && cause.getMessage().contains("Duplicate entry"))) {
                taskManager.incrementDuplicate();
                taskManager.appendLog("数据库重复(跳过)：" + url);
                return false;
            }
            log.error("新闻入库失败 url={}", url, e);
            return false;
        } catch (Exception e) {
            log.error("新闻入库异常 url={}", url, e);
            return false;
        } finally {
            // 未真正入库成功（重复/不合格/异常）时释放预留名额，让后续新闻补位
            if (!committed) {
                counter.decrementAndGet();
            }
        }
    }

    /**
     * 新闻入库后的智能分析：事件聚类 + 实体抽取。
     * 这些分析失败不影响新闻本身入库，因此单独 try/catch 并只记录日志。
     *
     * @param news    已入库的新闻（含ID与SimHash指纹）
     * @param simFreq SimHash 分词词频，用于新建事件簇时提取关键词
     */
    private void analyzeAfterInsert(News news, java.util.Map<String, Integer> simFreq) {
        try {
            Long clusterId = eventClusterer.assignCluster(news, simFreq);
            if (clusterId != null) {
                newsMapper.assignClusterId(news.getId(), clusterId);
                news.setClusterId(clusterId);
                // 按簇内实际新闻重新统计报道数/媒体数/平均情感
                eventClusterer.refreshStats(clusterId);
            }
        } catch (Exception e) {
            log.error("事件聚类失败 newsId={}", news.getId(), e);
        }
        try {
            entityExtractor.extractAndSave(news);
        } catch (Exception e) {
            log.error("实体抽取失败 newsId={}", news.getId(), e);
        }
    }

    /**
     * 判断是否还有任一站点未采满。
     */
    private boolean anySiteHasQuota() {
        for (AtomicInteger c : siteSavedCounters.values()) {
            if (c.get() < effectiveMaxPerSite) {
                return true;
            }
        }
        return false;
    }

    /**
     * 读取某站点已入库条数（未登记站点返回0）。
     */
    private int getSiteSaved(String siteKey) {
        AtomicInteger c = siteSavedCounters.get(siteKey);
        return c == null ? 0 : c.get();
    }

    /**
     * 本次任务站点显示名称清单文本，自定义站点使用管理员配置的名称。
     */
    private String siteDisplayNameSummary() {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String key : siteSavedCounters.keySet()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(taskSiteNames.getOrDefault(key, key));
            first = false;
        }
        return sb.append("]").toString();
    }

    /**
     * 各站点入库统计文本，如：[新浪新闻=5, 网易新闻=5, 腾讯新闻=3]。
     */
    private String siteSavedSummary() {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (java.util.Map.Entry<String, AtomicInteger> e : siteSavedCounters.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(taskSiteNames.getOrDefault(e.getKey(), e.getKey()))
              .append("=").append(e.getValue().get());
            first = false;
        }
        return sb.append("]").toString();
    }

    /**
     * 根据URL推断站点标识（同站BFS过滤与来源回退均使用）。
     * 已知门户直接返回友好名称；管理员自定义的陌生站点按"注册主域名"归一化，
     * 使 www / m / news / politics 等不同子域的链接归属同一站点，
     * 避免自定义站点的子域文章链接在同站过滤时被全部丢弃。
     */
    public static String resolveSiteKey(String url) {
        if (url == null) return "未知";
        try {
            if (url.contains("sina.com.cn") || url.contains("sina.cn")) return "新浪新闻";
            if (url.contains("163.com")) return "网易新闻";
            if (url.contains("qq.com")) return "腾讯新闻";
            if (url.contains("sohu.com")) return "搜狐新闻";
            if (url.contains("ifeng.com")) return "凤凰新闻";
            if (url.contains("people.com.cn")) return "人民网";
            if (url.contains("xinhuanet.com")) return "新华网";
            if (url.contains("thepaper.cn")) return "澎湃新闻";
            int start = url.indexOf("://");
            if (start < 0) return "未知";
            String tmp = url.substring(start + 3);
            int slashIdx = tmp.indexOf('/');
            String host = slashIdx > 0 ? tmp.substring(0, slashIdx) : tmp;
            // 去掉可能的端口号与userInfo（正常新闻URL不会有，做防御性处理）
            int atIdx = host.indexOf('@');
            if (atIdx >= 0) host = host.substring(atIdx + 1);
            int colonIdx = host.indexOf(':');
            if (colonIdx > 0) host = host.substring(0, colonIdx);
            String mainDomain = registrableDomain(host);
            return mainDomain == null ? "未知" : mainDomain;
        } catch (Exception e) {
            return "未知";
        }
    }

    /** 国内常见的"双段后缀"，命中时注册主域需取最后三段（如 news.qq.com.cn 假设场景） */
    private static final java.util.Set<String> MULTI_PART_SUFFIX = new java.util.HashSet<>(java.util.Arrays.asList(
            "com.cn", "net.cn", "org.cn", "gov.cn", "edu.cn", "ac.cn", "mil.cn",
            "co.uk", "org.uk", "ac.uk", "com.hk", "com.tw"));

    /**
     * 提取host的注册主域名：
     * www.chinanews.com.cn → chinanews.com.cn；politics.example.com → example.com。
     * 提取失败（IP地址、localhost、无点主机名等）时原样返回host。
     */
    private static String registrableDomain(String host) {
        if (host == null || host.isEmpty()) return null;
        String h = host.toLowerCase();
        int dotCount = 0;
        for (int i = 0; i < h.length(); i++) {
            if (h.charAt(i) == '.') dotCount++;
        }
        // IP地址或无点主机名无法提取主域，原样返回
        if (dotCount == 0) return h;
        String[] parts = h.split("\\.");
        String lastTwo = parts[parts.length - 2] + "." + parts[parts.length - 1];
        if (MULTI_PART_SUFFIX.contains(lastTwo) && parts.length >= 3) {
            return parts[parts.length - 3] + "." + lastTwo;
        }
        return lastTwo;
    }

    private void sleepRandom(int min, int max) {
        try {
            int range = Math.max(1, max - min);
            int delay = min + (int) (Math.random() * range);
            TimeUnit.MILLISECONDS.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean existsInDb(String urlHash) {
        QueryWrapper<News> qw = new QueryWrapper<>();
        qw.eq("url_hash", urlHash);
        return newsMapper.selectCount(qw) > 0;
    }
}
