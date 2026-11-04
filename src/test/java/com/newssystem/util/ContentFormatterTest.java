package com.newssystem.util;

import com.newssystem.common.FormattedContent;
import com.newssystem.common.FormattedContent.Item;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ContentFormatter} 单元测试。
 * 使用新浪、网易频道页的真实爬取样本（含[详细]标记、自媒体前缀、
 * 栏目竖线、无标记头条、整段重复、备案页脚噪声等典型形态）验证排版切分。
 *
 * @author NewsSystem Dev
 */
class ContentFormatterTest {

    /** 新浪频道页真实样本（聚合条目 + [详细] + 自媒体前缀 + 友情链接/版权页脚） */
    private static final String SINA_SAMPLE =
            "[习近平:中厄自贸协定释放红利、彰显潜力 一习话] 热爱不拼身体,熬夜观赛回血指南来了丨图数室 [详细] " +
            "【No.324】史上最贵世界杯,连特朗普都不想掏这个钱丨图数室 [详细] " +
            "【No.323】图数室丨181款新车首发,北京车展热门车型速览 [详细] " +
            "【No.322】图数室丨一图看懂2026年3·15晚会 [详细] " +
            "直播吧:三进宫!官方:巴萨免签32岁边后卫坎塞洛,球员与新月提前解约 " +
            "咪咕体育:#王祉怡回应抽筋# 王祉怡谈及决胜局腿部抽筋的过程:16分那个球, " +
            "懂球帝:【许家印&恒大案时间线:2023年9月许家印被捕,今天案件宣判】 " +
            "央视体育:【中国羽毛球队·初心】 石宇奇、李诗沣、陈雨菲、王祉怡、谭宁、 " +
            "新浪体育:#林祥毅胡珂源2比1伦加德麦斯# 2026年印度羽毛球世锦赛男双,林祥 " +
            "新浪乒乓球:#樊振东正式亮相杜塞尔多夫##和樊振东一起心相印# @樊振东 亮相 " +
            "新浪NBA:球星中国行门票继续送!76人球星马克西即将开启中国行,进入#76人 " +
            "新浪羽毛球:#王祉怡2比1辛杜# 2026年印度羽毛球世锦赛女单,王祉怡21-11、15 " +
            "新浪网球:#萨巴伦卡无缘辛辛那提站8强##wta[超话]# 萨巴伦卡又输了!辛辛 " +
            "新浪排球:#2026女排亚锦赛# 小组赛赛程: " +
            "新浪高尔夫:达利父子挥杆对比[围观] #高尔夫挥杆# " +
            "新浪游戏:Steam#今日游戏折扣#—— 【僵尸部队4】 新史低8元 【全面战争: " +
            "中国政府网 | 中国网 | 人民网 | 新华网 | 中新网 | 央视网 | 央广网 " +
            "24小时客户服务热线:4001102288 010-82623378 常见问题解答 " +
            "Copyright © 1996-2026 SINA Corporation, All Rights Reserved";

    /** 网易频道页真实样本（无标记头条 + 栏目竖线 + 头条区整段重复 + 备案页脚） */
    private static final String NETEASE_SAMPLE =
            "习近平致电祝贺希奇莱马当选连任赞比亚总统 习近平:中厄自贸协定释放红利、彰显潜力 | “培育发展新质生产力的新动能” " +
            "今年前7个月 社会消费商品和服务零售总额同比增长2.6% 朱雀三号成功回收 中国航天硬核技术再添新突破 " +
            "长江十年行 | 文润大江,解锁千年文脉的焕新密码 解锁沉浸式游玩新体验 多元业态激活暑期文旅市场 " +
            "60天,跨越2200公里去追“新”丨活力中国调研行 【图解】三个“用力”持续释放农村消费潜力 " +
            "肯尼亚官员:“中国市场让我们更懂什么是竞争力” " +
            "全球媒体聚焦 | 外媒热评中国可复用火箭发展成就 " +
            "澳门三五规划|吴惠娴:推进经济适度\n多元发展是关键 经济困局之下 高市政府“没东墙可拆却要捅漏西墙” " +
            "日本加速研发潜射导弹 “专守防卫”已名存实亡 认知工作室 | 漫评日本军事野心:“拓展地图” " +
            "高市政府危险动向引多方担忧 日本民众举行抗议集会 日子晃晃悠悠!杏树、花田与13个民族的慢时光 " +
            "谁说戈壁滩没有“桃花源”?各民族携手9年治荒6万亩 " +
            // —— 以下为频道页头条区的整段重复 ——
            "习近平致电祝贺希奇莱马当选连任赞比亚总统 习近平:中厄自贸协定释放红利、彰显潜力 | “培育发展新质生产力的新动能” " +
            "今年前7个月 社会消费商品和服务零售总额同比增长2.6% 朱雀三号成功回收 中国航天硬核技术再添新突破 " +
            "长江十年行 | 文润大江,解锁千年文脉的焕新密码 " +
            "澳门三五规划|吴惠娴:推进经济适度多元发展是关键 经济困局之下 " +
            // —— 栏目区 ——
            "财经 | 许家印被判处无期 恒大集团被罚88.2亿 " +
            "科技 | DeepSeek Harness更新多模态支持 " +
            "体育 | 何志伟英超新赛季前瞻 各队预测和隐忧 " +
            "网易号 | 航天没有标准答案:复用火箭技术多线并举 " +
            // —— 备案页脚噪声 ——
            "增值电信业务经营许可证粤B2-20090191 粤公网安备 44010602006299号 " +
            "违法和不良信息举报电话:0571-89853167 工业和信息化部备案管理系统网站 " +
            "Operated by Guangzhou NetEase Computer System Co., Ltd.";

    @Test
    @DisplayName("空内容安全返回空结果，不抛异常")
    void testEmptyContent() {
        FormattedContent r1 = ContentFormatter.format(null);
        FormattedContent r2 = ContentFormatter.format("   \n\t ");
        assertNotNull(r1);
        assertNotNull(r2);
        assertTrue(r1.isEmpty());
        assertTrue(r2.isEmpty());
        assertEquals(0, r1.getTotalChars());
    }

    @Test
    @DisplayName("新浪样本：识别为聚合条目模式，正确切分并清洗页脚噪声")
    void testSinaAggregated() {
        FormattedContent result = ContentFormatter.format(SINA_SAMPLE);

        assertEquals(FormattedContent.MODE_AGGREGATED, result.getMode());
        List<Item> items = result.getItems();
        assertTrue(items.size() >= 12, "新浪样本切分条目数应≥12，实际=" + items.size());

        // [详细] 标记必须全部清除
        for (Item item : items) {
            assertFalse(item.getTitle().contains("[详细]"), "条目标题残留[详细]：" + item.getTitle());
        }

        // 页脚噪声必须截断：友情链接/客服热线/Copyright 不出现在任何条目中
        for (Item item : items) {
            assertFalse(item.getTitle().contains("中国政府网"), "页脚友情链接未截断：" + item.getTitle());
            assertFalse(item.getTitle().contains("客户服务热线"), "客服热线未截断：" + item.getTitle());
            assertFalse(item.getTitle().contains("Copyright"), "版权页脚未截断：" + item.getTitle());
        }

        // 自媒体前缀应被拆分为来源
        assertTrue(hasSource(items, "直播吧"), "缺少来源=直播吧的条目");
        assertTrue(hasSource(items, "懂球帝"), "缺少来源=懂球帝的条目");
        assertTrue(hasSource(items, "新浪NBA"), "缺少来源=新浪NBA的条目");

        // 【No.324】栏目条目保留栏目标记
        assertTrue(items.stream().anyMatch(i -> i.getTitle().startsWith("【No.324】")),
                "【No.324】栏目条目缺失");

        // 页脚截断处的未闭合残片"【全面战争"不应成为独立噪声条目
        assertFalse(items.stream().anyMatch(i -> "【全面战争".equals(i.getTitle())),
                "未闭合栏目残片未被截除");

        // 总字数应为正数
        assertTrue(result.getTotalChars() > 100);
    }

    @Test
    @DisplayName("网易样本：无标记头条成条、栏目竖线拆来源、整段重复去重、备案页脚截断")
    void testNeteaseAggregated() {
        FormattedContent result = ContentFormatter.format(NETEASE_SAMPLE);

        assertEquals(FormattedContent.MODE_AGGREGATED, result.getMode());
        List<Item> items = result.getItems();
        assertTrue(items.size() >= 10, "网易样本切分条目数应≥10，实际=" + items.size());

        // 栏目竖线形态：栏目应拆为来源
        assertTrue(hasSource(items, "财经"), "缺少来源=财经的条目");
        assertTrue(hasSource(items, "科技"), "缺少来源=科技的条目");
        assertTrue(hasSource(items, "网易号"), "缺少来源=网易号的条目");

        // 备案页脚噪声不得出现在条目中
        for (Item item : items) {
            assertFalse(item.getTitle().contains("经营许可证"), "备案许可证噪声未截断：" + item.getTitle());
            assertFalse(item.getTitle().contains("公网安备"), "公网安备噪声未截断：" + item.getTitle());
            assertFalse(item.getTitle().contains("Operated by"), "英文版权页脚未截断：" + item.getTitle());
            assertFalse(item.getTitle().contains("举报电话"), "举报电话噪声未截断：" + item.getTitle());
        }

        // 整段重复去重：头条"习近平致电祝贺希奇莱马当选连任赞比亚总统"只能出现一次
        long dupCount = items.stream()
                .filter(i -> i.getTitle().contains("习近平致电祝贺希奇莱马当选连任赞比亚总统"))
                .count();
        assertEquals(1, dupCount, "重复头条未去重，出现次数=" + dupCount);

        // 被换行切断的标题应被拼回（包含关系去重保留完整长版本）
        assertTrue(items.stream().anyMatch(i -> i.getTitle().contains("推进经济适度多元发展是关键")),
                "换行切断的标题未拼回完整版本");

        // 无标记头条应独立成条
        assertTrue(items.stream().anyMatch(i -> i.getTitle().startsWith("朱雀三号成功回收")),
                "无标记头条'朱雀三号'未独立成条");
    }

    @Test
    @DisplayName("纯文章形态：按句子重组为自然段，不被误判为聚合模式")
    void testArticleMode() {
        // 无任何条首标记、无[详细]的完整短文
        String article = "这是一篇关于网络新闻分析系统的测试文章。系统采用网络爬虫技术采集公开新闻网页。"
                + "采集后经过数据清洗去除噪声内容。再通过中文分词提取新闻热词。"
                + "最后基于情感词典进行简单的情感倾向分析。整个流程自动化运行并定期更新数据。";
        FormattedContent result = ContentFormatter.format(article);

        assertEquals(FormattedContent.MODE_ARTICLE, result.getMode());
        assertFalse(result.getParagraphs().isEmpty());
        // 全部句子内容应被保留（字数无损）
        String joined = String.join("", result.getParagraphs());
        assertTrue(joined.contains("网络爬虫技术采集公开新闻网页"));
        assertTrue(joined.contains("情感倾向分析"));
    }

    /** 判断条目列表中是否存在指定来源 */
    private boolean hasSource(List<Item> items, String source) {
        return items.stream().anyMatch(i -> source.equals(i.getSource()));
    }
}
