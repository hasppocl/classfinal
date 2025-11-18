package com.hz.classfinal;


import com.hz.classfinal.util.*;

import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;


/**
 * 监听类加载
 *
 * @author roseboy
 */
public class CoreAgent {
    /**
     * man方法执行前调用
     *
     * @param args 参数
     * @param inst inst
     */
    public static void premain(String args, Instrumentation inst) throws IOException {
        CmdLineOption options = new CmdLineOption();
        options.addOption("pwd", true, "密码");
        options.addOption("pwdname", true, "环境变量密码参数名");
        options.addOption("nopwd", false, "无密码启动");
        options.addOption("debug", false, "调试模式");
        options.addOption("del", true, "读取密码后删除密码");

        if (args != null) {
            options.parse(args.split(" "));
            Constants.DEBUG = options.hasOption("debug");
        }

        char[] password;

        //读取jar隐藏的密码，无密码启动模式(jar)
        String rootPath = JarUtils.getRootPath();
        if (null == rootPath) {
            ConsoleLog.log("无法获取 classPath 根路径");
            return;
        }
        File jarFile = new File(rootPath);
        password = JarDecryptor.readPassFromJar(jarFile);


        //参数标识 无密码启动
        if (options.hasOption("nopwd")) {
            password = new char[1];
            password[0] = '#';
        }

        //参数获取密码
        if (StrUtils.isEmpty(password)) {
            String value = options.getOptionValue("pwd");
            if (null != value) {
                password = value.toCharArray();
            }
        }

        //参数没密码，读取环境变量中的密码
        if (StrUtils.isEmpty(password)) {
            String envName = options.getOptionValue("pwdname");
            if (StrUtils.isNotEmpty(envName)) {
                String value = System.getenv(envName);
                if (null != value) {
                    password = value.toCharArray();
                }
            }
        }

        //参数、环境变量都没密码，读取密码配置文件
        if (StrUtils.isEmpty(password)) {
            ConsoleLog.debug("无法从环境中获取密码，读取密码文件");
            password = readPasswordFromFile(rootPath, options);
        }

        // 配置文件没密码，从控制台获取输入
        if (StrUtils.isEmpty(password)) {
            ConsoleLog.debug("无法在参数中获取密码，从控制台获取");
            Console console = System.console();
            if (console != null) {
                password = console.readPassword("Password:");
            }
        }

        //不支持控制台输入，弹出gui输入
        if (StrUtils.isEmpty(password)) {
            ConsoleLog.debug("无法从控制台中获取密码，从GUI获取");
            InputForm input = new InputForm();
            if (input.showForm()) {
                password = input.nextPasswordLine();
                input.closeForm();
            }
        }

        //还是没有获取密码，退出
        if (StrUtils.isEmpty(password)) {
            ConsoleLog.log("\nERROR: Startup failed, could not get the password.\n");
            System.exit(0);
        }

        //验证密码有效性
        byte[] passHash = JarDecryptor.readEncryptedFile(jarFile, Constants.CONFIG_PASSHASH);
        if (passHash != null) {
            char[] internal = StrUtils.toChars(passHash);
            char[] external = EncryptUtils.md5(StrUtils.merger(password, EncryptUtils.SALT));
            external = EncryptUtils.md5(StrUtils.merger(EncryptUtils.SALT, external));
            if (!StrUtils.equals(internal, external)) {
                ConsoleLog.log("\nERROR: Startup failed, invalid password.\n");
                System.exit(0);
            }
        }
        //注入解密转换器
        if (inst != null) {
            inst.addTransformer(new AgentTransformer(password));
        }
    }

    /**
     * 从 jar包同级目录下的外部文件中读取密码
     *
     * @param path    jar 路径
     * @param options 参数
     * @return 密码
     * @throws IOException 读取文件异常
     */
    public static char[] readPasswordFromFile(String path, CmdLineOption options) throws IOException {
        if (!path.endsWith(Constants.JAR_EXT)) {
            return null;
        }
        File pwdFile = null;
        File jarFile = new File(path);
        File parentFile;
        if (jarFile.exists() && IoUtils.exists(parentFile = jarFile.getParentFile())) {
            String jarName = jarFile.getName();
            String jarPrefixName = jarName.substring(0, jarName.length() - 4);
            pwdFile = new File(parentFile, jarPrefixName + "-password.txt");
            if (!pwdFile.exists()) {
                pwdFile = new File(parentFile, "password.txt");
            }
        }

        if (IoUtils.exists(pwdFile)) {
            String pwd = IoUtils.readUtf8(pwdFile);
            if (StrUtils.isNotEmpty(pwd)) {
                if (Boolean.parseBoolean(options.getOptionValue("del"))) {
                    IoUtils.writeUtf8(pwdFile, "");
                }
                return pwd.toCharArray();
            }
        }
        return null;
    }
}
