package com.hz.classfinal;

import com.hz.classfinal.util.EncryptType;

/**
 * 常量
 *
 * @author roseboy
 */
public class Constants {

    public static final String JAR_SCHEME = "jar:";
    public static final String WAR_SCHEME = "war:";
    public static final String FILE_SCHEME = "file:";
    public static final String NESTED_SCHEME = "nested:";


    public static final String CLASS_EXT = ".class";
    public static final String JAVA_EXT = ".java";
    public static final String JAR_EXT = ".jar";
    public static final String WAR_EXT = ".war";
    public static final String WEB_INF = "WEB-INF";
    public static final String CLASSES_DIR = ".classes";
    public static final String META_INF = "META-INF";


    public static final String VERSION = "v1.2.1";

    //加密出来的文件名
    public static final String FILE_NAME = ".classes";

    //lib下的jar解压的目录名后缀
    public static final String TEMP_SUFFIX = "__temp__";

    //默认加密方式
    public static final EncryptType ENCRYPT_TYPE = EncryptType.MD5;

    //密码标记
    public static final String CONFIG_PASS = "org.springframework.config.Pass";
    //机器码标记
    public static final String CONFIG_CODE = "org.springframework.config.Code";
    //加密密码的hash
    public static final String CONFIG_PASSHASH = "org.springframework.config.PassHash";

    //本项目需要打包的代码
    public static final String[] CLASSFINAL_FILES = {
            "CoreAgent", "InputForm",
            "JarDecryptor", "AgentTransformer", "Constants", "CmdLineOption",
            "EncryptType", "EncryptUtils",
            "IoUtils", "JarUtils", "ConsoleLog", "StrUtils", "SysUtils"
    };


    //调试模式
    public static boolean DEBUG = false;
}
