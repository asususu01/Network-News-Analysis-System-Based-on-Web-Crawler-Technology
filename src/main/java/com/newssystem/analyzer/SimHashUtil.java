package com.newssystem.analyzer;

import com.newssystem.util.HashUtil;

import java.util.List;
import java.util.Map;

/**
 * SimHash 指纹工具。
 * 复用项目已有的 HashUtil.md5（32位十六进制），取前16个字符转 long 即得64位哈希，
 * 无需引入 MurmurHash 等新依赖。
 *
 * @author NewsSystem Dev
 */
public final class SimHashUtil {

    private SimHashUtil() {
    }

    /**
     * 计算64位 SimHash 指纹：对每个词的哈希按位加权投票，最终取符号为正的位
     */
    public static long simHash(Map<String, Integer> wordFreq) {
        if (wordFreq == null || wordFreq.isEmpty()) {
            return 0L;
        }
        int[] bits = new int[64];
        for (Map.Entry<String, Integer> e : wordFreq.entrySet()) {
            long h = hash64(e.getKey());
            int weight = e.getValue();
            for (int i = 0; i < 64; i++) {
                bits[i] += ((h >>> i) & 1L) == 1L ? weight : -weight;
            }
        }
        long fingerprint = 0L;
        for (int i = 0; i < 64; i++) {
            if (bits[i] > 0) {
                fingerprint |= (1L << i);
            }
        }
        return fingerprint;
    }

    /**
     * 汉明距离：两个指纹不同位的个数
     */
    public static int hamming(long a, long b) {
        return Long.bitCount(a ^ b);
    }

    /**
     * 取第 seg 段（0~3）的16位值，用于分桶索引。
     * 鸽笼原理：汉明距离<=3时，4段中至少有一段完全相同，因此按段等值查询不会漏召回。
     */
    public static int segment(long fingerprint, int seg) {
        return (int) ((fingerprint >>> (seg * 16)) & 0xFFFFL);
    }

    /**
     * 由分词结果统计词频
     */
    public static Map<String, Integer> wordFrequency(List<String> words) {
        Map<String, Integer> freq = new java.util.HashMap<>();
        if (words != null) {
            for (String w : words) {
                freq.merge(w, 1, Integer::sum);
            }
        }
        return freq;
    }

    private static long hash64(String word) {
        String md5 = HashUtil.md5(word);
        return Long.parseUnsignedLong(md5.substring(0, 16), 16);
    }
}
