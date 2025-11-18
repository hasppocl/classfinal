package com.hz.classfinal.util;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 命令行配置解析工具
 *
 * <pre>{@code
 *  pwd=123456
 *  file=D:/test.jar
 *  jars=a.jar,b.jar,c.jar
 * }</pre>
 *
 * @author hz
 */
public class CmdLineOption {

    static final String[] EMPTY = new String[0];
    private final List<String> options = new ArrayList<>();
    private final List<Boolean> hasArgs = new ArrayList<>();
    private final List<String> descriptions = new ArrayList<>();
    private final Map<String, List<String>> optionsMap = new HashMap<>();

    /**
     * 添加配置
     *
     * @param opt         配置名
     * @param hasArg      是否有参数
     * @param description 描述
     */
    public CmdLineOption addOption(String opt, boolean hasArg, String description) {
        opt = resolveOption(opt);
        if (!options.contains(opt)) {
            options.add(opt);
            hasArgs.add(hasArg);
            descriptions.add(description);
        }
        return this;
    }


    public CmdLineOption parse(String[] args) {
        List<String> values;
        for (String argument : args) {
            String[] kvp = argument.split("=");
            if (kvp.length != 2) {
                continue;
            }
            String option = resolveOption(kvp[0]);
            if (StrUtils.isEmpty(option)) {
                continue;
            }

            if (options.contains(option)) {
                values = Arrays.stream(kvp[1].split(","))
                        .map(String::trim)
                        .collect(Collectors.toList());
                optionsMap.put(option, values);
            }
        }
        return this;
    }

    public String getOptionValue(String opt) {
        return getOptionValue(opt, null);
    }

    public String getOptionValue(String opt, String dv) {
        String[] values = getOptionValues(opt);
        return values.length == 0 ? dv : values[0];
    }


    public String[] getOptionValues(String opt) {
        List<String> values = optionsMap.get(resolveOption(opt));
        return null == values ? EMPTY : values.toArray(new String[0]);
    }


    public boolean hasOption(String opt) {
        return optionsMap.containsKey(resolveOption(opt));
    }


    private static String resolveOption(String str) {
        if (StrUtils.isNotEmpty(str)) {
            char[] chars = str.toCharArray();
            for (int i = 0; i < chars.length; i++) {
                if (chars[i] != '-') {
                    return new String(chars, i, chars.length - i);
                }
            }
        }
        return null;
    }
}
