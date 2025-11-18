package com.hz.classfinal.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/**
 * 工具
 *
 * @author roseboy
 */
public class IoUtils {

    /**
     * 写入文件
     *
     * @param file      文件
     * @param fileBytes 字节
     * @throws IOException IOException
     */
    public static void writeBytes(File file, byte[] fileBytes) throws IOException {
        Files.write(touch(file).toPath(), fileBytes);
    }

    /**
     * 读取文件
     *
     * @param file 文件
     * @return 字节
     * @throws IOException IOException
     */
    public static byte[] readBytes(File file) throws IOException {
        return Files.readAllBytes(file.toPath());
    }

    /**
     * 从流中读取字节数组
     *
     * @param input 输入流
     * @return 字节数组
     * @throws IOException IOException
     */
    public static byte[] readBytes(InputStream input) throws IOException {
        ByteArrayOutputStream output = null;
        try {
            if (input instanceof FileInputStream) {
                FileInputStream fileInput = (FileInputStream) input;
                output = new ByteArrayOutputStream(fileInput.available());
            } else {
                output = new ByteArrayOutputStream();
            }
            byte[] buffer = new byte[4096];
            int i;
            while (-1 != (i = input.read(buffer))) {
                output.write(buffer, 0, i);
            }
            return output.toByteArray();
        } finally {
            close(output);
            close(input);
        }
    }

    /**
     * 文件是否存在
     *
     * @param path 路径
     * @return 存在为 true，否则为 false
     */
    public static boolean exists(String path) {
        return exists(new File(path));
    }

    /**
     * 文件是否存在
     *
     * @param file 文件
     * @return 存在为 true，否则为 false
     */
    public static boolean exists(File file) {
        return null != file && file.exists();
    }

    /**
     * 创建文件，并返回原文件对象
     *
     * @param path 文件
     * @return 文件
     * @throws IOException IOException
     */
    public static File touch(String path) throws IOException {
        return touch(new File(path));
    }

    /**
     * 创建文件，并返回原文件对象
     *
     * @param file 文件
     * @return 文件
     * @throws IOException IOException
     */
    public static File touch(File file) throws IOException {
        if (file != null && !file.exists()) {
            mkdir(file.getParentFile());
            //noinspection ResultOfMethodCallIgnored
            file.createNewFile();
        }
        return file;
    }


    /**
     * 创建目录，并返回原目录对象
     *
     * @param path 目录
     * @return 目录
     */
    public static File mkdir(String path) {
        return mkdir(new File(path));
    }

    /**
     * 创建目录，并返回原目录对象
     *
     * @param file 目录
     * @return 目录
     */
    public static File mkdir(File file) {
        if (file != null && !file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.mkdirs();
        }
        return file;
    }

    /**
     * 递归遍历目录，返回目录及子目录中的文件
     *
     * @param dir     目录
     * @param endWith 文件后缀
     */
    public static List<File> listFile(File dir, String endWith) {
        List<File> result = new ArrayList<>();
        if (exists(dir) && dir.isDirectory()) {
            //noinspection ConstantConditions
            for (File file : dir.listFiles()) {
                if (file.isDirectory()) {
                    result.addAll(listFile(file, endWith));
                } else if (file.isFile() && file.getName().endsWith(endWith)) {
                    result.add(file);
                }
            }
        }
        return result;
    }

    /**
     * 枚举目录下所有文件及文件夹
     *
     * @param dir      目录
     */
    public static List<File> listAllFile(File dir) {
        List<File> result = new ArrayList<>();
        if (exists(dir) && dir.isDirectory()) {
            //noinspection ConstantConditions
            for (File file : dir.listFiles()) {
                result.add(file);
                if (file.isDirectory()) {
                    result.addAll(listAllFile(file));
                }
            }
        }
        return result;
    }

    /**
     * 删除整个目录或文件
     *
     * @param file 目录或文件
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void delete(File file) {
        if (exists(file)) {
            if (file.isFile()) {
                file.delete();
            } else {
                //noinspection ConstantConditions
                for (File f : file.listFiles()) {
                    delete(f);
                }
            }
            file.delete();
        }
    }

    /**
     * 复制输入输出流
     *
     * @param input  输入流
     * @param output 输出流
     * @return 字节大小
     * @throws IOException IOException
     */
    public static int copy(InputStream input, OutputStream output)
            throws IOException {
        byte[] buffer = new byte[4096];
        int count = 0;
        int n;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

    /**
     * 计算 CRC32 校验和
     *
     * @param bytes 字节数组
     * @return 校验和
     */
    public static long crc32(byte[] bytes) {
        CRC32 crc = new CRC32();
        crc.update(bytes, 0, bytes.length);
        return crc.getValue();
    }


    /**
     * 关闭流
     *
     * @param closeable Closeable
     */
    public static void close(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 读取 UTF-8 文本文件
     *
     * @param file 文件
     * @return 内容
     * @throws IOException IOException
     */
    public static String readUtf8(File file) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 写入 UTF-8 内容到文件
     *
     * @param file 文件
     * @param txt  内容
     * @throws IOException IOException
     */
    public static void writeUtf8(File file, String txt) throws IOException {
        Files.write(touch(file).toPath(), txt.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 合并字节数组
     *
     * @param bts 字节数组
     * @return 合并后的字节
     */
    public static byte[] merger(byte[]... bts) {
        int lenght = 0;
        for (byte[] b : bts) {
            lenght += b.length;
        }

        byte[] bt = new byte[lenght];
        int lastLen = 0;
        for (byte[] b : bts) {
            System.arraycopy(b, 0, bt, lastLen, b.length);
            lastLen += b.length;
        }
        return bt;
    }

}
