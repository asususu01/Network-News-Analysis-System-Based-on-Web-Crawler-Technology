package com.newssystem.analyzer;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.newssystem.entity.News;
import com.newssystem.entity.NewsEntity;
import com.newssystem.mapper.NewsEntityMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 实体抽取器（规则 + 词典，轻量方案，不引入 HanLP 大模型）。
 *
 * 抽取规则：
 * 1) 机构(ORG)：正则匹配"2~8个汉字 + 公司/集团/大学/学院/委员会/研究院/银行/医院/政府/协会/基金会/电视台/报社"；
 * 2) 地点(LOC)：正则匹配"2~6个汉字 + 省/市/区/县/州/自治区"，并过滤"城市/地区"等非地名词；
 * 3) 人名(PERSON)：分词结果中2~3字、首字为常见姓氏，且后接"表示/指出/称/任"等动词的词语。
 * 只保留出现次数 >= 2 的实体，过滤偶发噪声。
 *
 * @author NewsSystem Dev
 */
@Component
public class EntityExtractor {

    private static final Logger log = LoggerFactory.getLogger(EntityExtractor.class);

    /** 单篇新闻最多保留的实体数 */
    private static final int MAX_ENTITIES_PER_NEWS = 12;
    /** 实体最少出现次数 */
    private static final int MIN_FREQUENCY = 2;
    /** 正文参与抽取的最大长度 */
    private static final int MAX_CONTENT_LENGTH = 3000;

    /**
     * 机构后缀正则：长后缀在前，避免"有限公司"被拆成"有限"+"公司"；前缀至少 3 字，
     * 否则「有限」这种 2 字残片会与后缀拼成伪实体。
     */
    private static final Pattern ORG_PATTERN = Pattern.compile(
            "[\\u4e00-\\u9fa5]{3,10}(?:股份有限公司|有限责任公司|有限公司|公司|集团|大学|学院" +
            "|委员会|研究院|银行|医院|政府|协会|基金会|电视台|报社)");
    /** 地点后缀正则 */
    private static final Pattern LOC_PATTERN = Pattern.compile(
            "[\\u4e00-\\u9fa5]{2,6}(?:自治区|省|市|区|县|州)");

    /** 省级行政区名称（新闻标题中常省略"省/市/自治区"后缀，靠词典补齐） */
    private static final Set<String> REGIONS = new HashSet<>(Arrays.asList(
            "北京", "上海", "天津", "重庆", "河北", "山西", "辽宁", "吉林", "黑龙江",
            "江苏", "浙江", "安徽", "福建", "江西", "山东", "河南", "湖北", "湖南",
            "广东", "海南", "四川", "贵州", "云南", "陕西", "甘肃", "青海", "台湾",
            "内蒙古", "广西", "西藏", "宁夏", "新疆", "香港", "澳门"
    ));

    /** 常见国家/地区名称（新闻中的地点实体，用于补足正则召回） */
    private static final Set<String> COUNTRIES = new HashSet<>(Arrays.asList(
            "中国", "美国", "俄罗斯", "日本", "韩国", "朝鲜", "印度", "英国", "法国",
            "德国", "意大利", "西班牙", "乌克兰", "以色列", "伊朗", "土耳其", "巴西",
            "加拿大", "澳大利亚", "新加坡", "越南", "泰国", "菲律宾", "马来西亚"
    ));

    /** 地名黑名单：命中这些结尾的匹配串不是地点 */
    private static final Set<String> LOC_BLACKLIST = new HashSet<>(Arrays.asList(
            "城市", "都市", "地区", "市区", "郊区", "山区", "城区", "园区", "校区", "社区",
            "开发区", "小区", "超市", "股市", "上市", "夜市", "集市", "菜市", "市场", "州市"
    ));

    /** 常见姓氏（覆盖绝大多数中文人名） */
    private static final Set<String> SURNAMES = new HashSet<>(Arrays.asList(
            "赵", "钱", "孙", "李", "周", "吴", "郑", "王", "冯", "陈", "褚", "卫", "蒋", "沈",
            "韩", "杨", "朱", "秦", "尤", "许", "何", "吕", "施", "张", "孔", "曹", "严", "华",
            "金", "魏", "陶", "姜", "戚", "谢", "邹", "喻", "柏", "水", "窦", "章", "云", "苏",
            "潘", "葛", "奚", "范", "彭", "郎", "鲁", "韦", "昌", "马", "苗", "凤", "花", "方",
            "俞", "任", "袁", "柳", "唐", "罗", "薛", "雷", "贺", "倪", "汤", "滕", "殷", "罗",
            "毕", "郝", "邬", "安", "常", "乐", "于", "时", "傅", "皮", "卞", "齐", "康", "伍",
            "余", "元", "卜", "顾", "孟", "平", "黄", "和", "穆", "萧", "尹", "姚", "邵", "湛",
            "汪", "祁", "毛", "禹", "狄", "米", "贝", "明", "臧", "计", "伏", "成", "戴", "谈",
            "宋", "茅", "庞", "熊", "纪", "舒", "屈", "项", "祝", "董", "梁", "杜", "阮", "蓝",
            "闵", "席", "季", "麻", "强", "贾", "路", "娄", "危", "江", "童", "颜", "郭", "梅",
            "盛", "林", "刁", "钟", "徐", "邱", "骆", "高", "夏", "蔡", "田", "樊", "胡", "凌",
            "霍", "虞", "万", "支", "柯", "管", "卢", "莫", "经", "房", "裘", "缪", "干", "解",
            "应", "宗", "丁", "宣", "贲", "邓", "郁", "单", "杭", "洪", "包", "诸", "左", "石",
            "崔", "吉", "龚", "程", "嵇", "邢", "裴", "陆", "荣", "翁", "荀", "羊", "惠", "甄",
            "曲", "家", "封", "芮", "储", "靳", "汲", "邴", "糜", "松", "井", "段", "富", "巫",
            "乌", "焦", "巴", "弓", "牧", "隗", "山", "谷", "车", "侯", "宓", "蓬", "全", "郗",
            "班", "仰", "秋", "仲", "伊", "宫", "宁", "仇", "栾", "暴", "甘", "钭", "厉", "戎",
            "祖", "武", "符", "刘", "景", "詹", "束", "龙", "叶", "幸", "司", "韶", "郜", "黎",
            "蓟", "薄", "印", "宿", "白", "怀", "蒲", "邰", "从", "鄂", "索", "咸", "籍", "赖",
            "卓", "蔺", "屠", "蒙", "池", "乔", "阴", "郁", "胥", "能", "苍", "双", "闻", "莘",
            "党", "翟", "谭", "贡", "劳", "逄", "姬", "申", "扶", "堵", "冉", "宰", "郦", "雍",
            "却", "璩", "桑", "桂", "濮", "牛", "寿", "通", "边", "扈", "燕", "冀", "浦", "尚",
            "农", "温", "别", "庄", "晏", "柴", "瞿", "阎", "充", "慕", "连", "茹", "习", "宦",
            "艾", "鱼", "容", "向", "古", "易", "慎", "戈", "廖", "庾", "终", "暨", "居", "衡",
            "步", "都", "耿", "满", "弘", "匡", "国", "文", "寇", "广", "禄", "阙", "东", "欧",
            "殳", "沃", "利", "蔚", "越", "夔", "隆", "师", "巩", "厍", "聂", "晁", "勾", "敖",
            "融", "冷", "訾", "辛", "阚", "那", "简", "饶", "空", "曾", "毋", "沙", "乜", "养",
            "鞠", "须", "丰", "巢", "关", "蒯", "相", "查", "后", "荆", "红", "游", "竺", "权",
            "逯", "盖", "益", "桓", "公"
    ));

    /** 人名后置动词：用于降低"姓+名"误判 */
    private static final String[] PERSON_CONTEXT = {
            "表示", "指出", "称", "说", "认为", "强调", "介绍", "透露", "宣布",
            "呼吁", "回应", "出任", "担任", "任职", "接受", "告诉", "向记者"
    };

    /** 明显不是人名的姓氏开头词 */
    private static final Set<String> NON_PERSON = new HashSet<>(Arrays.asList(
            "华为", "华人", "商人", "工人", "老人", "新人", "名人", "本人", "行人", "别人",
            "国民", "农民", "市民", "人民", "单元", "胡同", "马路", "高手", "商家", "房东",
            "相关", "相同", "相似", "相对", "相应", "相互", "相爱", "相连", "相继", "相交",
            "相等", "相符", "相片", "相机", "相亲", "相伴", "相通", "相距", "相称",
            "常有", "常见", "常规", "常用", "长期", "长城", "长江", "黄河", "高山", "高速",
            // 常用词误判（首字恰为姓氏）：文章 / 简单 / 程度 等
            "文章", "简单", "解释", "说明", "结构", "程度", "方案", "方法", "方式", "方面",
            "方向", "范围", "标准", "条件", "情况", "问题", "结果", "过程", "内容", "信息",
            "常理", "常态", "高处", "高端", "高价", "黄金", "白宫", "石家"
    ));

    /** 音译人名后缀：jieba 常把"泽连斯基"切成"泽/连斯基"，截断结果不可用，直接丢弃 */
    private static final String[] TRANSLIT_SUFFIX = {
            "斯基", "诺夫", "维奇", "耶夫", "什维利", "伯格", "斯坦", "布什", "克林顿"
    };

    @Autowired
    private IkWordAnalyzer wordAnalyzer;

    @Autowired
    private NewsEntityMapper newsEntityMapper;

    /**
     * 从标题 + 正文中抽取实体，返回 实体名 -> 类型（PERSON/ORG/LOC）。
     */
    public Map<String, String> extract(String title, String content) {
        String safeTitle = title == null ? "" : title;
        String safeContent = content == null ? ""
                : (content.length() > MAX_CONTENT_LENGTH ? content.substring(0, MAX_CONTENT_LENGTH) : content);
        Map<String, Integer> freq = new HashMap<>();
        Map<String, String> typeMap = new HashMap<>();

        // 标题权重2、正文权重1：标题中的实体天然是新闻主体，单次出现也应保留；
        // 正文中的实体仍需出现2次，用于压制偶发噪声。
        collectAll(safeTitle, 2, freq, typeMap);
        collectAll(safeContent, 1, freq, typeMap);

        // 只保留加权出现 >= 2 次的实体，按权重倒序截断
        List<Map.Entry<String, Integer>> kept = new ArrayList<>();
        for (Map.Entry<String, Integer> e : freq.entrySet()) {
            if (e.getValue() >= MIN_FREQUENCY) {
                kept.add(e);
            }
        }
        kept.sort((a, b) -> b.getValue() - a.getValue());

        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : kept) {
            if (result.size() >= MAX_ENTITIES_PER_NEWS) {
                break;
            }
            result.put(e.getKey(), typeMap.get(e.getKey()));
        }
        return result;
    }

    /**
     * 抽取并写入 news_entity（新闻入库成功后调用）。
     *
     * @return 入库实体数
     */
    public int extractAndSave(News news) {
        if (news == null || news.getUrlHash() == null || news.getUrlHash().isEmpty()) {
            return 0;
        }
        try {
            Map<String, String> entities = extract(news.getTitle(), news.getContent());
            if (entities.isEmpty()) {
                return 0;
            }
            QueryWrapper<NewsEntity> delQw = new QueryWrapper<>();
            delQw.eq("url_hash", news.getUrlHash());
            newsEntityMapper.delete(delQw);

            LocalDateTime now = LocalDateTime.now();
            for (Map.Entry<String, String> e : entities.entrySet()) {
                NewsEntity ne = new NewsEntity();
                ne.setUrlHash(news.getUrlHash());
                ne.setName(e.getKey());
                ne.setType(e.getValue());
                ne.setCreateTime(now);
                newsEntityMapper.insert(ne);
            }
            return entities.size();
        } catch (Exception e) {
            log.error("实体抽取入库失败 url={}", news.getUrl(), e);
            return 0;
        }
    }

    /** 对一段文本执行机构/地点/国家/人名四类抽取，weight 为该段文本的权重 */
    private void collectAll(String text, int weight,
                            Map<String, Integer> freq, Map<String, String> typeMap) {
        if (text == null || text.isEmpty()) {
            return;
        }
        // 机构、地点：正则直接匹配，召回率高于分词后缀判断
        collectByPattern(ORG_PATTERN, text, "ORG", weight, freq, typeMap);
        collectByPattern(LOC_PATTERN, text, "LOC", weight, freq, typeMap);
        // 省级行政区/国家：标题中常省略后缀，用词典补齐
        collectFromDictionary(REGIONS, text, "LOC", weight, freq, typeMap);
        collectFromDictionary(COUNTRIES, text, "LOC", weight, freq, typeMap);
        // 人名：分词 + 姓氏表 + 后置动词上下文
        List<String> words = wordAnalyzer.segment(text);
        for (String w : words) {
            if (!looksLikePerson(w, text)) {
                continue;
            }
            freq.merge(w, weight, Integer::sum);
            typeMap.put(w, "PERSON");
        }
    }

    /** 正则匹配实体并按权重累加频次 */
    private void collectByPattern(Pattern pattern, String text, String type,
                                  int weight, Map<String, Integer> freq, Map<String, String> typeMap) {
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            String name = m.group();
            if ("LOC".equals(type) && isLocationBlacklisted(name)) {
                continue;
            }
            // 过滤"这个城市""相关公司"这类带指示词的伪实体
            if (name.startsWith("这个") || name.startsWith("那个") || name.startsWith("相关")
                    || name.startsWith("多家") || name.startsWith("该")) {
                continue;
            }
            freq.merge(name, weight, Integer::sum);
            // 同一实体可能同时被正则与词典命中，保留首次识别的类型
            typeMap.putIfAbsent(name, type);
        }
    }

    /** 词典匹配：统计实体在文本中的出现次数并按权重累加 */
    private void collectFromDictionary(Set<String> dictionary, String text, String type,
                                       int weight, Map<String, Integer> freq, Map<String, String> typeMap) {
        for (String name : dictionary) {
            int count = 0;
            int idx = text.indexOf(name);
            while (idx >= 0) {
                count++;
                idx = text.indexOf(name, idx + name.length());
            }
            if (count > 0) {
                freq.merge(name, count * weight, Integer::sum);
                typeMap.putIfAbsent(name, type);
            }
        }
    }

    private boolean isLocationBlacklisted(String name) {
        for (String bad : LOC_BLACKLIST) {
            if (name.endsWith(bad)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断一个词是否像人名：2~3字、首字为常见姓氏、且原文中其后紧跟"表示/指出/称"等动词
     */
    private boolean looksLikePerson(String word, String text) {
        if (word == null || word.length() < 2 || word.length() > 3) {
            return false;
        }
        // 音译人名被分词截断（如"连斯基"）时不保留，避免出现半截外国人名
        for (String suf : TRANSLIT_SUFFIX) {
            if (word.endsWith(suf)) {
                return false;
            }
        }
        if (NON_PERSON.contains(word) || !SURNAMES.contains(word.substring(0, 1))) {
            return false;
        }
        int idx = text.indexOf(word);
        if (idx < 0) {
            return false;
        }
        int end = Math.min(text.length(), idx + word.length() + 3);
        String after = text.substring(idx + word.length(), end);
        for (String ctx : PERSON_CONTEXT) {
            if (after.contains(ctx)) {
                return true;
            }
        }
        return false;
    }
}
