package com.newssystem.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;

/**
 * 日期时间工具类
 * 统一格式化日期、解析字符串，避免到处写SimpleDateFormat
 *
 * @author NewsSystem Dev
 */
public class DateUtil {

    /** 常用时间格式：年-月-日 时:分:秒 */
    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /** 常用日期格式：年-月-日 */
    public static final String DATE_PATTERN = "yyyy-MM-dd";

    /** 备用格式数组：爬虫抓取到的发布时间格式不统一，尝试多种格式解析 */
    private static final String[] PARSE_PATTERNS = {
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy/MM/dd HH:mm",
            "yyyy/MM/dd",
            "yyyy年MM月dd日 HH:mm",
            "yyyy年MM月dd日 HH:mm:ss",
            "yyyy年MM月dd日",
            "MM-dd HH:mm",
            "yyyy.MM.dd HH:mm"
    };

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DATE_PATTERN);

    private DateUtil() {}

    // =================== 格式化 ===================

    /**
     * LocalDateTime -> "yyyy-MM-dd HH:mm:ss"
     */
    public static String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(DATE_TIME_FORMATTER);
    }

    /**
     * LocalDate -> "yyyy-MM-dd"
     */
    public static String formatDate(LocalDate date) {
        if (date == null) {
            return "";
        }
        return date.format(DATE_FORMATTER);
    }

    /**
     * Date -> "yyyy-MM-dd HH:mm:ss"
     */
    public static String formatDate(Date date) {
        if (date == null) {
            return "";
        }
        return formatDateTime(toLocalDateTime(date));
    }

    // =================== 解析 ===================

    /**
     * 尝试多种格式解析时间字符串为 LocalDateTime
     * 用于爬虫抓取到的发布时间（格式不固定）
     *
     * @param dateStr 日期字符串
     * @return LocalDateTime；解析失败返回null
     */
    public static LocalDateTime parseAny(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        String str = dateStr.trim();
        for (String pattern : PARSE_PATTERNS) {
            try {
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern(pattern);
                // 有些格式没有时间，默认补 00:00
                if (pattern.length() <= "yyyy-MM-dd".length() || pattern.contains("日") && !pattern.contains(":")) {
                    LocalDate d = LocalDate.parse(str, fmt);
                    return d.atStartOfDay();
                } else {
                    return LocalDateTime.parse(str, fmt);
                }
            } catch (DateTimeParseException ignored) {
                // 尝试下一种格式
            }
        }
        return null;
    }

    // =================== 转换 ===================

    /** Date -> LocalDateTime */
    public static LocalDateTime toLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    /** LocalDateTime -> Date */
    public static Date toDate(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    /** 获取当天的LocalDate */
    public static LocalDate today() {
        return LocalDate.now();
    }
}
