package com.hz.classfinal.util;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * 字符串工具
 *
 * @author roseboy
 */
public class StrUtils {

    /**
     * 将字符串以 {@code ","} 进行分割，去除空子串，并以List方式返回
     *
     * @param str 字符串
     * @return list
     */
    public static List<String> toList(String str) {
        List<String> list = new ArrayList<>();
        if (str != null) {
            String[] split = str.split(",");
            for (String s : split) {
                s = s.trim();
                if (s.length() > 0) {
                    list.add(s);
                }
            }
        }
        return list;
    }

    /**
     * 判断字符串是否为空
     *
     * @param str 字符串
     * @return 是否是空的
     */
    public static boolean isEmpty(String str) {
        return str == null || str.length() == 0;
    }

    /**
     * 判断字符串是否为空
     *
     * @param str 字符串
     * @return 是否是空的
     */
    public static boolean isEmpty(char[] str) {
        return str == null || str.length == 0;
    }

    /**
     * 判断字符串是否不为空
     *
     * @param str 字符串
     * @return 是否不是空的
     */
    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    /**
     * 判断字符串是否不为空
     *
     * @param str 字符串
     * @return 是否不是空的
     */
    public static boolean isNotEmpty(char[] str) {
        return !isEmpty(str);
    }

    /**
     * 拼接多个 char[]
     *
     * @param bts 字节数组
     * @return 合并后的字节
     */
    public static char[] merger(char[]... bts) {
        StringBuilder sb = new StringBuilder();
        for (char[] bt : bts) {
            sb.append(bt);
        }
        char[] chars = new char[sb.length()];
        sb.getChars(0, sb.length(), chars, 0);
        return chars;
    }

    /**
     * 字节转char数组
     *
     * @param bytes 字节数组
     * @return chars
     */
    public static char[] toChars(byte[] bytes) {
        CharBuffer buffer = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(bytes));
        char[] chars = new char[buffer.remaining()];
        buffer.get(chars);
        return chars;
    }

    /**
     * 字符数组转成字节数组，使用 UTF-8 编码
     *
     * @param chars 字符数组
     * @return 字节数组
     */
    public static byte[] toBytes(char[] chars) {
        ByteBuffer buffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars));
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

    /**
     * char数组比较
     *
     * @param cs1 char1
     * @param cs2 char2
     * @return 是否相等
     */
    public static boolean equals(char[] cs1, char[] cs2) {
        if (cs1.length != cs2.length) {
            return false;
        }

        for (int i = 0; i < cs1.length; i++) {
            if (cs1[i] != cs2[i]) {
                return false;
            }
        }
        return true;
    }


    /**
     * 字符串是否包含数组中的任意元素
     *
     * @param str   字符串
     * @param array 数组
     */
    public static boolean containsAny(String str, String... array) {
        for (String s : array) {
            if (str.contains(s)) {
                return true;
            }
        }
        return false;
    }


    /**
     * 将字符串数组的元素使用换行符拼接，并在筛选器匹配的第一个元素前插入指定字串，
     * 若没有任何元素符合条件，则添加在拼接的字符串末尾，并返回拼接后的字符串
     *
     * @param array  字符串数组
     * @param insert 插入的子串
     * @param filter 筛选器
     * @return 字符串
     */
    public static String insertBefore(String[] array,
                                      String insert,
                                      Predicate<String> filter) {
        StringBuilder sb = new StringBuilder();

        boolean inserted = false;
        for (String str : array) {
            if (!inserted && filter.test(str)) {
                sb.append(insert).append("\r\n");
                inserted = true;
            }
            sb.append(str).append("\r\n");
        }
        if (!inserted) {
            sb.append(insert).append("\r\n");
        }
        return sb.toString();
    }

    /**
     * 通配符匹配
     *
     * @param match      匹配字符串
     * @param testString 待匹配字符窜
     * @return 是否匹配
     */
    public static boolean isMatch(String match, String testString) {
        String regex = match.replaceAll("\\?", "(.?)")
                .replaceAll("\\*+", "(.*?)");
        return Pattern.matches(regex, testString);
    }

    /**
     * 通配符匹配
     *
     * @param matches    匹配字符串
     * @param testString 待匹配字符窜
     * @param dv         默认值
     * @return 是否匹配
     */
    public static boolean isMatchAny(List<String> matches, String testString, boolean dv) {
        if (matches == null || matches.size() == 0) {
            return dv;
        }

        for (String m : matches) {
            if (StrUtils.isMatch(m, testString) || testString.startsWith(m) || testString.endsWith(m)) {
                return true;
            }
        }
        return false;
    }
}
