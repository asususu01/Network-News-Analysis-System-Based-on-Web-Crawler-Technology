package com.newssystem.analyzer;

import cn.hutool.core.util.StrUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class DataCleaner {

    private static final Logger log = LoggerFactory.getLogger(DataCleaner.class);

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCRIPT_PATTERN = Pattern.compile(
            "<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern STYLE_PATTERN = Pattern.compile(
            "<style[^>]*>.*?</style>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HTML_ENTITY_PATTERN = Pattern.compile("&[a-zA-Z]{1,10};");
    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("[\\s\\u3000]+");
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(https?|ftp|file)://[-A-Za-z0-9+&@#/%?=~_|!:,.;]+[-A-Za-z0-9+&@#/%=~_|]");
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[\\w!#$%&'*+/=?^_`{|}~-]+(?:\\.[\\w!#$%&'*+/=?^_`{|}~-]+)*@(?:[\\w](?:[\\w-]*[\\w])?\\.)+[\\w](?:[\\w-]*[\\w])?");

    public String cleanText(String rawText) {
        if (StrUtil.isBlank(rawText)) {
            return null;
        }
        String text = rawText;

        text = SCRIPT_PATTERN.matcher(text).replaceAll("");
        text = STYLE_PATTERN.matcher(text).replaceAll("");
        text = HTML_TAG_PATTERN.matcher(text).replaceAll("");
        text = HTML_ENTITY_PATTERN.matcher(text).replaceAll(" ");
        text = URL_PATTERN.matcher(text).replaceAll(" ");
        text = EMAIL_PATTERN.matcher(text).replaceAll(" ");
        text = MULTI_SPACE_PATTERN.matcher(text).replaceAll(" ");
        text = StrUtil.trim(text);
        text = toDBC(text);

        return text;
    }

    private static String toDBC(String input) {
        if (input == null || input.isEmpty()) return input;
        char[] c = input.toCharArray();
        for (int i = 0; i < c.length; i++) {
            if (c[i] == '\u3000') {
                c[i] = ' ';
            } else if (c[i] > '\uFF00' && c[i] < '\uFF5F') {
                c[i] = (char) (c[i] - 65248);
            }
        }
        return new String(c);
    }

    public String cleanContent(String rawContent) {
        String cleaned = cleanText(rawContent);
        if (cleaned == null) {
            return null;
        }
        return cleaned;
    }

    public boolean isValidContent(String text, int minLength) {
        return text != null && text.length() >= minLength;
    }
}
