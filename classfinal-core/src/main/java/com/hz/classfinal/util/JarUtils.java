package com.hz.classfinal.util;

import com.hz.classfinal.Constants;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * jar/war 操作工具类
 */
public class JarUtils extends Constants {

    public static final String CLASSES = "/classes/";
    //打包时需要删除的文件
    public static final String[] STYLE_FILES = {".DS_Store", "Thumbs.db"};

    /**
     * 把目录压缩成压缩文件
     *
     * @param dir    目录
     * @param output 压缩文件路径
     * @return 压缩文件路径
     * @throws IOException IO异常
     */
    public static String compressJar(String dir, String output) throws IOException {
        File dirFile = new File(dir);
        String absolutePath = dirFile.getAbsolutePath();
        List<File> allFiles = IoUtils.listAllFile(dirFile);


        File outFile = new File(output);
        if (outFile.exists()) {
            //重建输出文件
            //noinspection ResultOfMethodCallIgnored
            outFile.delete();
        }
        ZipEntry ze;
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(outFile.toPath()))) {
            for (File file : allFiles) {
                if (isStyleFile(file)) {
                    continue;
                }
                String entryName = file.getAbsolutePath().substring(absolutePath.length() + 1);
                entryName = entryName.replace(File.separator, "/");
                //目录，添加一个目录entry
                if (file.isDirectory()) {
                    ze = new ZipEntry(entryName + "/");
                    ze.setTime(System.currentTimeMillis());
                    zos.putNextEntry(ze);
                    zos.closeEntry();
                }
                //jar文件，需要写crc信息
                else if (entryName.endsWith(Constants.JAR_EXT)) {
                    byte[] bytes = IoUtils.readBytes(file);
                    ze = new ZipEntry(entryName);
                    ze.setMethod(ZipEntry.STORED);
                    ze.setSize(bytes.length);
                    ze.setTime(System.currentTimeMillis());
                    ze.setCrc(IoUtils.crc32(bytes));
                    zos.putNextEntry(ze);
                    zos.write(bytes);
                    zos.closeEntry();
                }
                //其他文件直接写入
                else {
                    ze = new ZipEntry(entryName);
                    ze.setTime(System.currentTimeMillis());
                    zos.putNextEntry(ze);
                    zos.write(IoUtils.readBytes(file));
                    zos.closeEntry();
                }
            }
            return output;
        }
    }


    /**
     * 释放压缩文件内的所有内容
     *
     * @param zip    压缩文件路径
     * @param output 释放文件夹
     * @return 释放文件夹
     * @throws IOException IO异常
     */
    public static List<String> uncompressJar(String zip, String output) throws IOException {
        return uncompressJar(zip, output, null);
    }

    /**
     * 释放压缩文件内的所有文件
     *
     * @param zip           压缩文件
     * @param output        释放文件夹
     * @param excludeFilter 排除文件（简单文件名）
     * @return 所有文件的完整路径，包含目录
     * @throws IOException IOException
     */
    public static List<String> uncompressJar(String zip, String output, Predicate<String> excludeFilter) throws IOException {
        List<String> result = new ArrayList<>();
        File outDir = IoUtils.mkdir(output);

        ZipEntry entry;
        File file;
        try (ZipFile zf = new ZipFile(new File(zip))) {
            //创建文件夹
            Enumeration<?> entries = zf.entries();
            while (entries.hasMoreElements()) {
                entry = (ZipEntry) entries.nextElement();
                if (entry.isDirectory()) {
                    file = new File(outDir, entry.getName());
                    IoUtils.mkdir(file);
                } else {
                    int index = entry.getName().lastIndexOf(File.separator);
                    if (index > 0) {
                        IoUtils.mkdir(new File(outDir, entry.getName().substring(0, index)));
                    }
                }
            }

            //释放文件
            entries = zf.entries();
            while (entries.hasMoreElements()) {
                entry = (ZipEntry) entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                file = new File(outDir, entry.getName());
                if (excludeFilter != null && excludeFilter.test(file.getName())) {
                    continue;
                }
                try (InputStream stream = zf.getInputStream(entry)) {
                    Files.copy(stream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                result.add(file.getAbsolutePath());
            }
            return result;
        }
    }

    /**
     * 从压缩文件中提取指定文件
     *
     * @param zip      压缩文件
     * @param fileName 内部文件名
     * @param output   释放的目标文件
     * @return 释放出的文件的绝对路径
     * @throws IOException IO异常
     */
    public static String extractFile(File zip, String fileName, File output) throws IOException {
        byte[] bytes = extractFile(zip, fileName);
        if (bytes != null) {
            Files.write(IoUtils.touch(output).toPath(), bytes);
            return output.getAbsolutePath();
        }
        return null;
    }

    /**
     * 从压缩文件中提取指定文件的内容
     *
     * @param zip      压缩文件
     * @param fileName 文件名
     * @return 字节数组
     * @throws IOException IO异常
     */
    public static byte[] extractFile(File zip, String fileName) throws IOException {
        if (IoUtils.exists(zip)) {
            try (ZipFile zf = new ZipFile(zip)) {
                ZipEntry zipEntry = zf.getEntry(fileName);
                if (zipEntry != null) {
                    return IoUtils.readBytes(zf.getInputStream(zipEntry));
                }
            }
        }
        return null;

    }

    /**
     * 是否是系统样式文件，例如windows系统的 Thumbs.db 文件，macos 系统的 .DS_Store 文件
     *
     * @param file 文件
     */
    public static boolean isStyleFile(File file) {
        for (String name : STYLE_FILES) {
            if (file.getAbsolutePath().endsWith(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取当前 class 运行根目录
     *
     * @return 路径字符串
     */
    public static String getRootPath() {
        return getRootPath(null);
    }

    /**
     * 获取 class 运行根目录
     * <li/> 标准 jar、war 包直接返回，因为包所在路径就是 classpath
     * <li/> jar文件内部路径返回 jar 包路径，如：demo.jar!/xxx 返回 demo.jar
     * <li/> {@code ...\webapp\WEB-INF\com\...} 返回 {@code ...\webapp}（未打包的 WEB 程序）
     * <li/> {@code ...\classes\com\...} 返回 {@code ...\classes}（未打包的 JAVA 程序）
     *
     * @return 路径字符串
     */
    public static String getRootPath(String path) {
        if (path == null) {
            //使用当前运行根目录
            URL resource = JarUtils.class.getResource("");
            if (null == resource) {
                return null;
            }
            path = resource.getPath();
        }
        try {
            path = java.net.URLDecoder.decode(path, "utf-8");
        } catch (UnsupportedEncodingException ignored) {
        }

        if (path.startsWith(JAR_SCHEME)) {
            path = path.substring(JAR_SCHEME.length());
        }
        if (path.startsWith(WAR_SCHEME)) {
            path = path.substring(WAR_SCHEME.length());
        }
        if (path.startsWith(FILE_SCHEME)) {
            path = path.substring(FILE_SCHEME.length());
        }
        //兼容 SpringBoot 风格嵌套 jar
        if (path.startsWith(NESTED_SCHEME)) {
            path = path.substring(NESTED_SCHEME.length());
        }

        //没解压的war包
        if (path.contains("*")) {
            return path.substring(0, path.indexOf("*"));
        }
        // war /WEB-INF
        else if (path.contains(WEB_INF)) {
            return path.substring(0, path.indexOf(WEB_INF));
        }
        // compressed jar
        else if (path.contains("!")) {
            return path.substring(0, path.indexOf("!"));
        }
        //jar/war
        else if (path.endsWith(JAR_EXT) || path.endsWith(WAR_EXT)) {
            return path;
        }
        // classes
        else if (path.contains(CLASSES)) {
            return path.substring(0, path.indexOf(CLASSES) + CLASSES.length());
        }
        return null;
    }

}
