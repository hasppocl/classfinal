package com.hz.classfinal;


import com.hz.classfinal.util.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * java class解密
 *
 * @author roseboy
 */
public class JarDecryptor {
    private static final JarDecryptor INSTANCE = new JarDecryptor();
    private final char[] code;

    /**
     * 加密后文件存放位置
     */
    private static final String ENCRYPT_PATH = "META-INF/" + Constants.FILE_NAME + "/";

    /**
     * 单例
     *
     * @return 单例
     */
    public static JarDecryptor getInstance() {
        return INSTANCE;
    }

    JarDecryptor() {
        this.code = SysUtils.makeMarchinCode();
    }

    /**
     * 根据名称解密出一个文件
     *
     * @param jarPath     jar包路径
     * @param fileName 文件名
     * @param password 密码
     * @return 解密后的字节
     */
    public byte[] doDecrypt(String jarPath, String fileName, char[] password) throws Exception {
        long t1 = System.currentTimeMillis();
        File jarFile = new File(jarPath);
        byte[] bytes = readEncryptedFile(jarFile, fileName);
        if (bytes == null) {
            return null;
        }
        //读取机器码，有机器码，先用机器码解密
        byte[] codeBytes = readEncryptedFile(jarFile, Constants.CONFIG_CODE);
        if (codeBytes != null) {
            if (!Arrays.equals(EncryptUtils.md5(code), StrUtils.toChars(codeBytes))) {
                ConsoleLog.println("该项目不可在此机器上运行!\n");
                System.exit(-1);
            }

            //用机器码解密
            char[] pass = StrUtils.merger(fileName.toCharArray(), code);
            bytes = EncryptUtils.decryption(bytes, pass, Constants.ENCRYPT_TYPE);
        }

        //无密码启动,读取隐藏的密码
        if (password.length == 1 && password[0] == '#') {
            password = readPassFromJar(jarFile);
        }

        //密码解密
        char[] pass = StrUtils.merger(password, fileName.toCharArray());
        bytes = EncryptUtils.decryption(bytes, pass, Constants.ENCRYPT_TYPE);
        long t2 = System.currentTimeMillis();
        ConsoleLog.debug("解密: %s (%d ms)", fileName, t2 - t1);
        return bytes;

    }

    /**
     * 在 jar文件或目录中读取加密目录下的文件内容
     *
     * @param workDir jar文件或目录
     * @param name    加密目录下的文件
     * @return 文件字节数组
     */
    public static byte[] readEncryptedFile(File workDir, String name) throws IOException {
        String fileName = ENCRYPT_PATH + name;
        if (workDir.isFile()) {
            return JarUtils.extractFile(workDir, fileName);
        }
        File file = new File(workDir, fileName);
        return file.exists() ? IoUtils.readBytes(file) : null;
    }

    /**
     * 读取 jar 包中的密码文件
     *
     * @param workDir jar路径
     * @return 密码
     * @see Constants#CONFIG_PASS
     */
    public static char[] readPassFromJar(File workDir) throws IOException {
        byte[] passBytes = readEncryptedFile(workDir, Constants.CONFIG_PASS);
        return passBytes != null ? EncryptUtils.md5(StrUtils.toChars(passBytes)) : null;
    }

    /**
     * 解密配置文件
     *
     * @param path 配置文件路径
     * @param in   输入流
     * @return 解密的输入流
     */
    public InputStream decryptConfigFile(String path, InputStream in, char[] pass) throws Exception {
        if (path.endsWith(Constants.CLASS_EXT)) {
            return in;
        }
        String rootPath = JarUtils.getRootPath();
        if (StrUtils.isEmpty(rootPath)) {
            return in;
        }
        byte[] bytes = IoUtils.readBytes(in);
        if (bytes.length == 0) {
            bytes = doDecrypt(rootPath, path, pass);
        }
        if (bytes == null) {
            return in;
        }
        IoUtils.close(in);
        return new ByteArrayInputStream(bytes);
    }
}
