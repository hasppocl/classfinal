package com.hz.classfinal.util;

import com.hz.classfinal.Constants;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 控制台打印日志工具
 *
 * @author roseboy
 */
public class ConsoleLog {

    static final SimpleDateFormat datetimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    static final String DEBUG = "DEBUG";
    static final String INFO = "INFO";
    static final String ERROR = "ERROR";


    /**
     * 输出
     */
    public static void println(String msg){
        System.out.println(msg);
    }

    /**
     * 输出
     */
    public static void println() {
        System.out.println();
    }

    /**
     * 输出
     */
    public static void println(String msg, Object... args) {
        System.out.println(String.format(msg, args));
    }

    /**
     * 输出debug信息
     *
     * @param msg 信息
     */
    public static void debug(String msg) {
        if (Constants.DEBUG) {
            System.out.println(baseFormat(msg, DEBUG));
        }
    }

    /**
     * 输出debug信息
     *
     * @param template 模板
     * @param arg      参数
     */
    public static void debug(String template, Object... arg) {
        if (Constants.DEBUG) {
            System.out.println(baseFormat(String.format(template, arg), DEBUG));
        }
    }

    /**
     * 输出
     *
     * @param msg 内容
     */
    public static void log(String msg) {
        System.out.println(baseFormat(msg, INFO));
    }

    /**
     * 输出
     *
     * @param template 模板
     * @param args     参数
     */
    public static void log(String template, Object... args) {
        System.out.println(baseFormat(String.format(template, args), INFO));
    }

    /**
     * 输出
     *
     * @param msg 内容
     */
    public static void error(String msg) {
        System.out.println(baseFormat(msg, ERROR));
    }

    /**
     * 输出
     *
     * @param template 模板
     * @param args     参数
     */
    public static void error(String template, Object... args) {
        System.out.println(baseFormat(String.format(template, args), ERROR));
    }



    private static String baseFormat(Object obj, String level) {
        return String.format("%s [%s]: %s", datetimeFormat.format(new Date()), level, obj);
    }
}
