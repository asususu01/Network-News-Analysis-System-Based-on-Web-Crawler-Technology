package com.newssystem.analyzer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HotScoreCalculator} 热度打分器单元测试。
 *
 * @author NewsSystem Dev
 */
class HotScoreCalculatorTest {

    private final HotScoreCalculator calculator = new HotScoreCalculator();

    /** 构造指定长度的中文正文 */
    private String contentOfLength(int len) {
        StringBuilder sb = new StringBuilder(len);
        while (sb.length() < len) {
            sb.append("新闻正文内容用于测试热度打分的正文质量分项。");
        }
        return sb.substring(0, len);
    }

    @Test
    @DisplayName("满分场景：首页第一条+热门词+栏目标记+规范长度+长正文，得分100")
    void testTopScore() {
        String title = "【突发】重磅发布会现场独家直播回应热点问题";
        int score = calculator.calculate(title, contentOfLength(600), 0, 1);
        // 位置60 + 标题(3热门词15+栏目5+长度5=25) + 正文15 = 100
        assertEquals(100, score);
    }

    @Test
    @DisplayName("页面位置越靠前热度越高，同条件下位置严格递减")
    void testPositionMonotonic() {
        String title = "一条普通规范长度的新闻标题";
        int first = calculator.calculate(title, contentOfLength(300), 0, 1);
        int fifth = calculator.calculate(title, contentOfLength(300), 4, 1);
        int tenth = calculator.calculate(title, contentOfLength(300), 10, 1);
        assertTrue(first > fifth, "第1条应比第5条热：" + first + "/" + fifth);
        assertTrue(fifth > tenth, "第5条应比第11条热：" + fifth + "/" + tenth);
    }

    @Test
    @DisplayName("深度系数：种子页直接链接(depth1)热度高于深层页面(depth3)同位置链接")
    void testDepthFactor() {
        String title = "一条普通规范长度的新闻标题";
        int shallow = calculator.calculate(title, contentOfLength(300), 2, 1);
        int deep = calculator.calculate(title, contentOfLength(300), 2, 3);
        assertTrue(shallow > deep, "浅层链接应比深层链接热：" + shallow + "/" + deep);
    }

    @Test
    @DisplayName("热门词标题比普通标题得分高")
    void testHotKeywordBonus() {
        String content = contentOfLength(300);
        int normal = calculator.calculate("城市轨道交通新线路今日开通运营", content, 5, 1);
        int hot = calculator.calculate("突发！城市轨道交通新线路今日开通", content, 5, 1);
        assertTrue(hot > normal, "含热门词标题得分应更高：" + hot + "/" + normal);
    }

    @Test
    @DisplayName("正文越长质量分越高")
    void testContentLengthBonus() {
        String title = "一条普通规范长度的新闻标题";
        int short_ = calculator.calculate(title, contentOfLength(120), 0, 1);
        int long_ = calculator.calculate(title, contentOfLength(600), 0, 1);
        assertTrue(long_ > short_, "长正文得分应更高：" + long_ + "/" + short_);
    }

    @Test
    @DisplayName("边界保护：空标题/空正文不报错；得分始终在0~100区间")
    void testBoundarySafe() {
        int s1 = calculator.calculate(null, null, 0, 1);
        int s2 = calculator.calculate("", "", 1000, 99);
        int s3 = calculator.calculate("突发重磅独家直播回应", contentOfLength(5000), -5, -1);
        assertTrue(s1 >= 0 && s1 <= 100);
        assertTrue(s2 >= 0 && s2 <= 100);
        // 非法负位置/负深度按最有利的0/1处理，得分仅受内容特征限制而不超过100
        assertTrue(s3 >= 90 && s3 <= 100, "极端输入应落在90~100，实际=" + s3);
    }
}
