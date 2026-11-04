package com.newssystem.util;

import com.newssystem.common.FormattedContent;
import com.newssystem.common.FormattedContent.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 爬取新闻正文排版工具（展示层使用，不修改数据库原始内容）。
 *
 * 爬虫抓取的频道页正文通常是多种形态的文本聚合流，本工具负责：
 * 1) 截断页脚噪声：友情链接、备案/许可证号、举报电话、客服热线、Copyright 等；
 * 2) 智能切分条目：识别 [方括号标签]、【栏目标记】、"来源:"/"来源："、"栏目 | 标题"
 *    以及无标记头条之间的空白边界；
 * 3) 碎片合并：空格切分产生的过短片段并入相邻条目，避免标题被拦腰截断；
 * 4) 重复去重：部分频道页头条区会整段重复输出，按规范化文本去重并保持首次出现顺序；
 * 5) 模式判定：能切出多条 → 聚合条目模式；否则按文章自然段模式排版。
 *
 * @author NewsSystem Dev
 */
public final class ContentFormatter {

    private static final Logger log = LoggerFactory.getLogger(ContentFormatter.class);

    private ContentFormatter() {}

    /** 页脚噪声起始标记：命中任一标记后，其后内容全部截断（覆盖新浪/网易等主流门户页脚） */
    private static final String[] FOOTER_MARKERS = {
            "中国政府网 |", "中国政府网|",          // 新浪友情链接区起点
            "24小时客户服务热线", "客户服务热线",
            "常见问题解答", "违法和不良信息举报", "举报电话", "举报邮箱",
            "Copyright", "copyright", "©",
            "广告营销", "网站律师", "产品答疑", "通行证注册",
            "增值电信业务经营许可证", "经营许可证",
            "公网安备", "网药信备", "药品信息服务",
            "工业和信息化部备案", "备案管理系统", "备案凭证",
            "Operated by", "关于我们", "联系方式", "招聘信息"
    };

    /**
     * 已知媒体/自媒体账号关键词（冒号前缀必须命中其一才算条首）。
     * 作用：标题正文中大量存在"官方:…""过程:…""吴惠娴:…"等普通冒号表达，
     * 若一律视为条首会把标题拦腰切断，因此只承认媒体账号形态的前缀。
     */
    private static final String MEDIA_KEYWORDS =
            "新浪|央视|新华|人民|环球|凤凰|腾讯|搜狐|网易|澎湃|界面|虎扑|快手|抖音|知乎|中青|"
            + "光明网|参考消息|直播吧|懂球帝|咪咕|中国新闻网|新京报|经济观察|南方都市报|36氪";

    /**
     * 条首标记正则（匹配一条新闻的起始位置）。所有形态都要求位于串首或空白之后，
     * 避免标题内部的 [围观]、【时间线】、"官方:…" 等被误判为新条目。四种形态：
     * 1) [方括号标签]，如 [习近平:中厄自贸协定释放红利 一习话]
     * 2) 【中文方括号栏目】，如 【No.324】、【图解】
     * 3) 媒体账号前缀 + 冒号，如 直播吧:、咪咕体育:、新浪NBA:
     * 4) 栏目名 + 竖线，如 财经 |、网易号 |、全球媒体聚焦 |
     * 说明：栏目名限定2-6个中文，避免误伤标题正文中的短竖线表达。
     */
    private static final Pattern ITEM_START = Pattern.compile(
            "(?:^|(?<=\\s))(\\[[^\\]]{1,50}\\]|【[^】]{1,50}】|"
            + "(?:" + MEDIA_KEYWORDS + ")[\\u4e00-\\u9fa5A-Za-z]{0,8}\\s*[:：]|"
            + "[\\u4e00-\\u9fa5]{2,6}\\s*\\|)");

    /** 条目尾部噪声标记，如 [详细]（频道页"查看详细"链接残留） */
    private static final Pattern ITEM_TAIL_NOISE = Pattern.compile("\\[详细\\]|\\s*查看更多.*$|\\s*更多.*>>.*$");

    /**
     * 条目中的来源前缀（冒号形态）：仅承认媒体账号关键词开头的前缀 + 剩余标题。
     * 避免把"肯尼亚官员:…"、"官方:…"这类标题内普通引语误拆为来源。
     */
    private static final Pattern SOURCE_COLON = Pattern.compile(
            "^(?:" + MEDIA_KEYWORDS + ")[\\u4e00-\\u9fa5A-Za-z]{0,8}\\s*[:：]\\s*(.*)$");

    /** 提取冒号前缀中的来源名（用于生成 Item.source，不含正文） */
    private static final Pattern SOURCE_COLON_NAME = Pattern.compile(
            "^((" + MEDIA_KEYWORDS + ")[\\u4e00-\\u9fa5A-Za-z]{0,8})\\s*[:：]");

    /** 条目中的栏目前缀（竖线形态）：栏目 + 剩余标题 */
    private static final Pattern SOURCE_PIPE = Pattern.compile("^([\\u4e00-\\u9fa5]{2,6})\\s*\\|\\s*(.*)$");

    /** 无标记区中，独立成条的最小标题长度；短于此长度的空格片段与相邻片段拼接 */
    private static final int MIN_STANDALONE_LEN = 8;
    /** 保留条目的最小有效长度（去除空白后） */
    private static final int MIN_ITEM_LEN = 5;
    /** 聚合模式要求的最少条目数 */
    private static final int MIN_AGGREGATED_ITEMS = 2;
    /** 单个条目标题超过该长度时，按空白边界二次切分（兼容多条头条连写的频道页） */
    private static final int LONG_ITEM_SPLIT_LEN = 60;
    /** 文章模式分段时，单段最大字数（超出按句末标点重新组段） */
    private static final int ARTICLE_PARAGRAPH_MAX = 150;

    /**
     * 将爬取的原始正文格式化为可排版对象。
     *
     * @param rawContent 数据库中保存的原始正文
     * @return 格式化结果（永不为null；内容为空时得到空的文章模式对象）
     */
    public static FormattedContent format(String rawContent) {
        if (rawContent == null || rawContent.trim().isEmpty()) {
            return new FormattedContent(FormattedContent.MODE_ARTICLE);
        }

        // 第一步：截断页脚噪声
        String body = cutFooter(rawContent);
        // 统一空白：所有连续空白（含换行/制表符）规范为单个空格，便于后续切分
        body = body.replaceAll("\\s+", " ").trim();

        // 第二步：切分并提取条目（内部完成条尾清洗、前缀拆分、碎片合并）
        List<Item> items = extractItems(body);

        // 第三步：重复去重（网易等频道头条区会整段重复）
        items = deduplicate(items);

        FormattedContent result;
        if (items.size() >= MIN_AGGREGATED_ITEMS) {
            // 聚合条目模式
            result = new FormattedContent(FormattedContent.MODE_AGGREGATED);
            result.setItems(items);
        } else {
            // 文章段落模式：条目不足以判定为聚合流，按自然段/句子重新排版
            result = new FormattedContent(FormattedContent.MODE_ARTICLE);
            result.setParagraphs(buildParagraphs(body));
        }

        result.setTotalChars(countChars(result));
        log.debug("正文格式化完成：模式={}, 条目数={}, 段落数={}, 字数={}",
                result.getMode(), result.getItems().size(),
                result.getParagraphs().size(), result.getTotalChars());
        return result;
    }

    /**
     * 截断页脚：找到最早出现的页脚标记位置，只保留其之前的正文。
     */
    private static String cutFooter(String content) {
        int cut = -1;
        for (String marker : FOOTER_MARKERS) {
            int idx = content.indexOf(marker);
            if (idx >= 0 && (cut < 0 || idx < cut)) {
                cut = idx;
            }
        }
        return cut >= 0 ? content.substring(0, cut) : content;
    }

    /**
     * 从正文中切分新闻条目。
     * 以条首标记为边界扫描；两个边界之间的"间隙文本"按空白流式切分，
     * 短片段与相邻长片段拼接（兼容"今年前7个月 社会消费商品..."这类标题被空格切断），
     * 长片段独立成条（兼容无任何标记的头条列表）。
     */
    private static List<Item> extractItems(String body) {
        List<Item> items = new ArrayList<>();

        Matcher matcher = ITEM_START.matcher(body);
        List<int[]> bounds = new ArrayList<>();
        while (matcher.find()) {
            bounds.add(new int[]{matcher.start(), matcher.end()});
        }

        if (bounds.isEmpty()) {
            // 完全没有条首标记：整体按空白流式切分
            items.addAll(splitPlainText(body, null));
            return items;
        }

        // 第一个标记之前若有文本，多为无标记头条区，按空白切分
        if (bounds.get(0)[0] > 0) {
            items.addAll(splitPlainText(body.substring(0, bounds.get(0)[0]), null));
        }

        // 依次处理每个"条首标记 → 下一个条首标记"之间的片段
        for (int i = 0; i < bounds.size(); i++) {
            int segStart = bounds.get(i)[0];
            int segEnd = (i + 1 < bounds.size()) ? bounds.get(i + 1)[0] : body.length();
            String segment = body.substring(segStart, segEnd);
            Item item = toItem(segment);
            if (item == null) {
                continue;
            }
            // 标题过长说明该片段内可能连写了多条无标记新闻，按空白二次切分
            if (item.getTitle().length() > LONG_ITEM_SPLIT_LEN) {
                items.addAll(splitPlainText(item.getTitle(), item.getSource()));
            } else {
                items.add(item);
            }
        }
        return items;
    }

    /**
     * 将无标记文本按空白流式切分为条目：
     * 短片段（如"今年前7个月"）先缓存，与紧随其后的长片段拼接，
     * 从而把被换行/空格拦腰截断的标题尽量拼回；结尾残余短片段并入最后一条。
     *
     * @param text        待切分文本
     * @param firstSource 首个切分片段继承的来源前缀（来自父条目的"来源:"），可为null
     */
    private static List<Item> splitPlainText(String text, String firstSource) {
        List<Item> result = new ArrayList<>();
        String[] tokens = text.trim().split("\\s+");
        StringBuilder buffer = new StringBuilder();
        String pendingSource = firstSource;

        for (String token : tokens) {
            String t = cleanItemText(token);
            if (t.length() < MIN_ITEM_LEN) {
                continue;
            }
            if (t.length() < MIN_STANDALONE_LEN) {
                // 短片段缓存，等待与后续片段拼接
                buffer.append(t);
            } else {
                if (buffer.length() > 0) {
                    // 缓存的短片段与当前长片段组成完整标题
                    result.add(parsePrefixed(buffer + t, pendingSource));
                    buffer.setLength(0);
                } else {
                    result.add(parsePrefixed(t, pendingSource));
                }
                pendingSource = null;
            }
        }
        // 结尾残余短片段：并入最后一条末尾
        if (buffer.length() > 0) {
            if (result.isEmpty()) {
                Item tail = parsePrefixed(buffer.toString(), pendingSource);
                if (tail != null) {
                    result.add(tail);
                }
            } else {
                Item last = result.get(result.size() - 1);
                last.setTitle(last.getTitle() + buffer);
            }
        }
        return result;
    }

    /**
     * 二次切分片段的条目构造：若片段自身带"来源:"/"栏目 |"前缀则拆分，
     * 否则使用调用方传入的继承来源。
     */
    private static Item parsePrefixed(String text, String inheritedSource) {
        String t = cleanItemText(text);
        if (t.length() < MIN_ITEM_LEN) {
            return null;
        }
        // 媒体来源前缀（白名单）
        Item colonItem = splitColonSource(t);
        if (colonItem != null) {
            return colonItem;
        }
        // 栏目竖线前缀
        Matcher pipe = SOURCE_PIPE.matcher(t);
        if (pipe.matches()) {
            return buildItem(pipe.group(1), pipe.group(2));
        }
        return buildItem(inheritedSource, t);
    }

    /**
     * 将一个条目片段转换为 Item：清洗条尾噪声、拆出来源前缀。
     *
     * @return 有效条目；内容过短/为纯噪声时返回null
     */
    private static Item toItem(String segment) {
        String text = ITEM_TAIL_NOISE.matcher(segment).replaceAll("").trim();
        text = cleanItemText(text);
        if (text.length() < MIN_ITEM_LEN) {
            return null;
        }

        // 形态1：[方括号标签] 标题 —— 去掉外层方括号，标签整体作为标题一部分保留，
        // 不再拆分标签内部的"人物:引语"冒号（那是引语结构而非媒体来源）
        if (text.startsWith("[")) {
            int close = text.indexOf(']');
            if (close > 0 && close < text.length() - 1) {
                String tag = text.substring(1, close).trim();
                String rest = cleanItemText(text.substring(close + 1));
                return buildItem(null, rest.isEmpty() ? tag : tag + " " + rest);
            }
        }

        // 形态2：【栏目】标题 —— 栏目保留在标题开头（视觉上通过【】自然区分）
        if (text.startsWith("【")) {
            return buildItem(null, text);
        }

        // 形态3：媒体来源:标题（仅承认白名单内媒体账号前缀）
        Item colonItem = splitColonSource(text);
        if (colonItem != null) {
            return colonItem;
        }

        // 形态4：栏目 | 标题
        Matcher pipe = SOURCE_PIPE.matcher(text);
        if (pipe.matches()) {
            return buildItem(pipe.group(1), pipe.group(2));
        }

        return buildItem(null, text);
    }

    /**
     * 拆分"媒体来源:标题"形态。非媒体账号前缀（如"官方:""吴惠娴:"）一律返回null，
     * 保留原句作为标题，避免误切。
     */
    private static Item splitColonSource(String text) {
        Matcher nameM = SOURCE_COLON_NAME.matcher(text);
        if (!nameM.find()) {
            return null;
        }
        String sourceName = nameM.group(1);
        Matcher bodyM = SOURCE_COLON.matcher(text);
        if (!bodyM.matches()) {
            return null;
        }
        return buildItem(sourceName, bodyM.group(1));
    }

    /**
     * 构造条目并做最终校验：标题为空时用来源充当标题，过滤纯标点噪声。
     */
    private static Item buildItem(String source, String title) {
        String s = source == null ? null : cleanItemText(source);
        String t = cleanItemText(title);
        if (s != null && s.isEmpty()) {
            s = null;
        }
        if (t.isEmpty() && s != null) {
            t = s;
            s = null;
        }
        if (t.length() < MIN_ITEM_LEN) {
            return null;
        }
        return new Item(s, t);
    }

    /**
     * 条目去重：按"去空白去常见标点"后的规范化文本判重，保持首次出现的位置顺序。
     * 除完全相同外，还处理"包含关系"——频道页重复区因换行切断差异，可能出现
     * "吴惠娴:推进经济适度"与"吴惠娴:推进经济适度多元发展是关键"两条，
     * 此时保留信息更完整的较长版本。
     */
    private static List<Item> deduplicate(List<Item> items) {
        // key 顺序即最终展示顺序；value 保存规范化key与条目，便于包含关系替换
        LinkedHashMap<String, Item> uniq = new LinkedHashMap<>();
        for (Item item : items) {
            String key = normalizeForCompare(item.getTitle());
            if (key.length() < MIN_ITEM_LEN) {
                continue;
            }
            if (uniq.containsKey(key)) {
                continue;
            }

            // 与已有条目检查包含关系：若互为子串，用更长版本替换短版本（位置不变）
            String matchedKey = null;
            for (String existKey : uniq.keySet()) {
                if (existKey.contains(key) || key.contains(existKey)) {
                    matchedKey = existKey;
                    break;
                }
            }
            if (matchedKey != null) {
                if (key.length() > matchedKey.length()) {
                    // 替换为信息更完整的长版本（LinkedHashMap不支持直接改值，重建保序）
                    replacePreservingOrder(uniq, matchedKey, key, item);
                }
                continue;
            }
            uniq.put(key, item);
        }
        return new ArrayList<>(uniq.values());
    }

    /**
     * 在LinkedHashMap中以新键替换旧键的值，同时保持原有位置顺序。
     */
    private static void replacePreservingOrder(LinkedHashMap<String, Item> map,
                                               String oldKey, String newKey, Item newItem) {
        Map<String, Item> copy = new LinkedHashMap<>(map);
        map.clear();
        for (Map.Entry<String, Item> e : copy.entrySet()) {
            if (e.getKey().equals(oldKey)) {
                map.put(newKey, newItem);
            } else {
                map.put(e.getKey(), e.getValue());
            }
        }
    }

    /**
     * 文章模式分段：优先按换行/多空白切分；若整文无分隔，则按句末标点
     * （。！？…）断句并重新组合为不超过 {@link #ARTICLE_PARAGRAPH_MAX} 字的自然段。
     */
    private static List<String> buildParagraphs(String body) {
        List<String> paragraphs = new ArrayList<>();

        // 按2个及以上空白（原始换行已规范为单空格，这里主要处理异常残留）切分
        for (String p : body.split("\\s{2,}")) {
            String t = cleanItemText(p);
            if (t.length() >= MIN_ITEM_LEN) {
                paragraphs.add(t);
            }
        }
        if (!paragraphs.isEmpty()) {
            return paragraphs;
        }

        // 无段落分隔：按句末标点断句后重组
        String[] sentences = body.split("(?<=[。！？…])");
        StringBuilder buffer = new StringBuilder();
        for (String sentence : sentences) {
            String s = cleanItemText(sentence);
            if (s.isEmpty()) {
                continue;
            }
            if (buffer.length() + s.length() > ARTICLE_PARAGRAPH_MAX && buffer.length() > 0) {
                paragraphs.add(buffer.toString());
                buffer.setLength(0);
            }
            buffer.append(s);
        }
        if (buffer.length() > 0) {
            paragraphs.add(buffer.toString());
        }
        return paragraphs;
    }

    /**
     * 条目文本清洗：去条尾噪声标记、压缩空白、去除首尾孤立标点，
     * 并截除页脚截断处可能残留的未闭合栏目残片（如"Steam折扣 【全面战争:"）。
     */
    private static String cleanItemText(String text) {
        if (text == null) {
            return "";
        }
        String t = ITEM_TAIL_NOISE.matcher(text).replaceAll("");
        t = t.replaceAll("\\s+", " ").trim();
        // 去除首尾残留的分隔符/竖线/冒号等
        t = t.replaceAll("^[|｜:：,，、;；\\s]+|[|｜,，、;；\\s]+$", "");
        // 截除未闭合的中文栏目残片（存在【但其后无】）
        int lastCnOpen = t.lastIndexOf('【');
        if (lastCnOpen >= 0 && t.indexOf('】', lastCnOpen) < 0) {
            t = t.substring(0, lastCnOpen).trim();
        }
        // 截除未闭合的方括号残片（存在[但其后无]）
        int lastSqOpen = t.lastIndexOf('[');
        if (lastSqOpen >= 0 && t.indexOf(']', lastSqOpen) < 0) {
            t = t.substring(0, lastSqOpen).trim();
        }
        return t.trim();
    }

    /**
     * 去重比较用规范化：去全部空白与常见标点，统一小写。
     */
    private static String normalizeForCompare(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("[\\s\\p{P}|｜【】\\[\\]（）()\"'“”‘’#]", "")
                .toLowerCase();
    }

    /**
     * 统计格式化结果的有效字数（不含空白）。
     */
    private static int countChars(FormattedContent result) {
        int total = 0;
        if (FormattedContent.MODE_AGGREGATED.equals(result.getMode())) {
            for (Item item : result.getItems()) {
                total += item.getTitle() == null ? 0 : item.getTitle().replaceAll("\\s", "").length();
            }
        } else {
            for (String p : result.getParagraphs()) {
                total += p == null ? 0 : p.replaceAll("\\s", "").length();
            }
        }
        return total;
    }
}
