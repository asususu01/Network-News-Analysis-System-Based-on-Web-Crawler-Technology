package com.newssystem.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 哈希工具类
 * 提供MD5、SHA等哈希算法，主要用于URL去重时生成url_hash
 *
 * @author NewsSystem Dev
 */
public class HashUtil {

    /** 十六进制字符映射 */
    private static final char[] HEX_CHARS = {
            '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    };

    /**
     * 对字符串进行MD5哈希（32位小写）
     * 用于URL去重：相同URL生成相同MD5，存入数据库唯一索引即可去重
     *
     * @param input 原字符串（如URL）
     * @return MD5哈希值；输入为空则返回空串
     */
    public static String md5(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] bytes = md5.digest(input.getBytes());
            return bytesToHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            // MD5是JDK内置算法，一般不会抛此异常
            throw new RuntimeException("MD5算法不存在", e);
        }
    }

    /**
     * 字节数组转十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        if (bytes == null) {
            return "";
        }
        char[] result = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            byte b = bytes[i];
            result[i * 2] = HEX_CHARS[(b >>> 4) & 0x0f];
            result[i * 2 + 1] = HEX_CHARS[b & 0x0f];
        }
        return new String(result);
    }
}
