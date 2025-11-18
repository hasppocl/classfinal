package com.hz.classfinal;

import javassist.ClassPool;
import javassist.NotFoundException;
import com.hz.classfinal.util.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * java class加密
 *
 * @author roseboy
 */
public class JarEncryptor {
    static final Pattern SYNTHETIC_PATTERN = Pattern.compile("([\\w$]+)(?:\\$\\$Lambda)?\\$\\d.class");
    //加载配置文件是注入解密代码的配置
    static final Map<String, InjectInfo> injectMap = new HashMap<>();

    static {
        //org.springframework.core.io.ClassPathResource#getInputStream 注入解密功能
        injectMap.put("spring", new InjectInfo(
                "org.springframework.core.io.ClassPathResource#getInputStream",
                "char[] c=${passchar};is=com.hz.classfinal.JarDecryptor.getInstance().decryptConfigFile(this.path,is,c);",
                999));

        //com.jfinal.kit.Prop#getInputStream
        injectMap.put("jfinal", new InjectInfo(
                "com.jfinal.kit.Prop#<Prop>(java.lang.String,java.lang.String)",
                "char[] c=${passchar};inputStream=com.hz.classfinal.JarDecryptor.getInstance().decryptConfigFile(fileName,inputStream,c);",
                62
        ));
    }

    /**
     * 要加密的 jar或 war
     */
    private String zipPath = null;
    /**
     * 要加密的包名，多个用逗号隔开
     */
    private List<String> packages = null;
    /**
     * 要加密的依赖 jar，在 jar包中是 {@code META-INF/lib}，在 war包中是 {@code WEB-INF/lib}
     */
    private List<String> includeJars = null;
    /**
     * 排除加密的类名（全类名）
     */
    private List<String> excludeClass = null;
    /**
     * 外部依赖 jar路径
     */
    private List<String> classPath = null;
    /**
     * 需要加密的配置文件
     */
    private List<String> cfgfiles = null;
    /**
     * 加密的密码，使用 {@code "#"} 则为无密码模式
     */
    private char[] password;
    /**
     * 机器码
     */
    private char[] code = null;

    //--------------- 内部字段 -------------------

    /**
     * 文件后缀名，多数情况下是 jar 或 war
     */
    private String extName = null;
    /**
     * 表示加密的文件是 jar包还是 war包
     */
    private Boolean jarOrWar = null;
    /**
     * 临时工作目录
     */
    private File tempWork = null;
    /**
     * 依赖目录，在 jar包中为 {@code META-INF/lib}，在 war包中是 {@code WEB-INF/lib}目录
     */
    private File libDir = null;
    /**
     * classes字节码根目录，在 jar包中为 {@code META-INF/classes}，在 war包中是 {@code WEB-INF/classes}目录
     */
    private File classesDir = null;
    /**
     * 加密的文件数量
     */
    private Integer encryptFileCount = null;
    /**
     * 存储解析出来的类名和路径
     */
    private final Map<String, Map<Boolean, String>> resolveClassInfo = new HashMap<>();

    /**
     * 构造方法
     *
     * @param zipPath  要加密的jar或war
     * @param password 密码
     */
    public JarEncryptor(String zipPath, char[] password) {
        super();
        this.zipPath = zipPath;
        this.password = password;
    }

    /**
     * 加密jar的主要过程
     *
     * @return 解密后生成的文件的绝对路径
     */
    public String doEncryptJar() throws Exception {
        initParam();

        //[1]释放所有文件
        List<String> allFilePath = JarUtils.uncompressJar(zipPath, tempWork.getAbsolutePath());
        for (String path : allFilePath) {
            ConsoleLog.debug("释放：%s", path);
        }

        //[1.1] 内部jar只释放需要加密的jar
        List<String> libJars = new ArrayList<>();
        for (String path : allFilePath) {
            if (path.endsWith(Constants.JAR_EXT)) {
                String jarFileName = path.substring(path.lastIndexOf(File.separator) + 1);
                if (StrUtils.isMatchAny(includeJars, jarFileName, false)) {
                    String libTempWork = path.substring(0, path.length() - 4) + Constants.TEMP_SUFFIX;
                    List<String> libJarAllFilePath = JarUtils.uncompressJar(path, libTempWork);
                    for (String libPath : libJarAllFilePath) {
                        ConsoleLog.debug("释放：%s", libPath);
                    }
                    libJars.add(path);
                    libJars.addAll(libJarAllFilePath);
                }
            }
        }

        allFilePath.addAll(libJars);

        //[2]提取所有需要加密的class文件
        List<File> classFiles = extractClasses(allFilePath);

        //[3]将本项目的代码添加至jar中
        addClassFinalAgent();

        //[4]将正常的class加密，压缩另存
        List<String> encrypts = encryptClass(classFiles);
        this.encryptFileCount = encrypts.size();

        //[5]清空class方法体，并保存文件
        clearClassMethod(classFiles);

        //[6]加密配置文件
        encryptConfigFile();

        //[7]打包回去
        return packageJar(libJars);
    }


    private void initParam() {
        if (null == zipPath) {
            throw new RuntimeException("请指定要加密的jar或war");
        }
        if (!IoUtils.exists(zipPath)) {
            throw new RuntimeException("文件不存在:" + zipPath);
        }
        int extIndex = zipPath.lastIndexOf(".");
        if (extIndex == -1) {
            throw new RuntimeException("jar/war文件格式有误");
        }
        this.extName = zipPath.substring(extIndex + 1);
        ConsoleLog.debug("加密类型：" + extName);
        if ("jar".equals(extName)) {
            this.jarOrWar = true;
        } else if ("war".equals(extName)) {
            this.jarOrWar = false;
        } else {
            throw new RuntimeException("jar/war文件格式有误");
        }
        if (password == null || password.length == 0) {
            throw new RuntimeException("密码不能为空");
        }
        if (isNoPwdMode()) {
            ConsoleLog.debug("加密模式：无密码");
        }
        ConsoleLog.debug("机器绑定：" + (StrUtils.isEmpty(this.code) ? "否" : "是"));

        this.tempWork = new File(zipPath.replace(
                jarOrWar ? Constants.JAR_EXT : Constants.WAR_EXT,
                Constants.TEMP_SUFFIX));
        ConsoleLog.debug("临时工作目录：" + tempWork);

        this.libDir = new File(this.tempWork,
                (jarOrWar ? "BOOT-INF" : "WEB-INF") + File.separator + "lib");
        this.classesDir = new File(this.tempWork,
                (jarOrWar ? "BOOT-INF" : "WEB-INF") + File.separator + "classes");
    }

    /**
     * 当前加密文件是否是 jar 包
     */
    protected boolean isJar() {
        Objects.requireNonNull(jarOrWar, "jarOrWar 参数未初始化");
        return jarOrWar;
    }

    /**
     * 当前加密文件是否是 war 包
     */
    protected boolean isWar() {
        Objects.requireNonNull(jarOrWar, "jarOrWar 参数未初始化");
        return !jarOrWar;
    }

    /**
     * 当前是否是无密码模式
     */
    protected boolean isNoPwdMode() {
        return password.length == 1 && password[0] == '#';
    }

    /**
     * 找出所有需要加密的class文件
     *
     * @param allFilePath 所有文件
     * @return 待加密的class列表
     */
    public List<File> extractClasses(List<String> allFilePath) {
        List<File> classFiles = new ArrayList<>();
        allFilePath.forEach(path -> {
            if (!path.endsWith(Constants.CLASS_EXT)) {
                return;
            }
            String className = resolveClassName(path, true);
            //判断包名相同和是否排除的类
            if (StrUtils.isMatchAny(packages, className, false)
                    && !StrUtils.isMatchAny(excludeClass, className, false)) {
                classFiles.add(new File(path));
                ConsoleLog.debug("待加密：" + path);
            }
        });
        return classFiles;
    }

    /**
     * 加密 class文件，放在 META-INF/.classes 里
     *
     * @param classFiles jar/war 下需要加密的class文件
     * @return 已经加密的类名
     */
    private List<String> encryptClass(List<File> classFiles) throws Exception {
        List<String> encryptClasses = new ArrayList<>();
        //加密后存储的位置 META-INF/.classes/
        File metaDir = new File(tempWork, "META-INF" + File.separator + Constants.FILE_NAME);
        IoUtils.mkdir(metaDir);

        //无密码模式,自动生成一个密码
        if (isNoPwdMode()) {
            char[] randChars = EncryptUtils.randomChars(32);
            this.password = EncryptUtils.md5(randChars);
            //META-INF/.classes/org.springframework.config.Pass
            File configPass = new File(metaDir, Constants.CONFIG_PASS);
            IoUtils.writeBytes(configPass, StrUtils.toBytes(randChars));
        }

        //有机器码
        if (StrUtils.isNotEmpty(code)) {
            //META-INF/.classes/org.springframework.config.Code
            File configCode = new File(metaDir, Constants.CONFIG_CODE);
            IoUtils.writeBytes(configCode, StrUtils.toBytes(EncryptUtils.md5(code)));
        }

        //加密另存
        for (File classFile : classFiles) {
            String className = classFile.getName();
            if (className.endsWith(Constants.CLASS_EXT)) {
                className = resolveClassName(classFile.getAbsolutePath(), true);
            }
            byte[] classBytes = IoUtils.readBytes(classFile);
            char[] pass = StrUtils.merger(password, className.toCharArray());
            classBytes = EncryptUtils.encryption(classBytes, pass, Constants.ENCRYPT_TYPE);
            //有机器码，再用机器码加密一遍
            if (StrUtils.isNotEmpty(code)) {
                pass = StrUtils.merger(className.toCharArray(), code);
                classBytes = EncryptUtils.encryption(classBytes, pass, Constants.ENCRYPT_TYPE);
            }
            IoUtils.writeBytes(new File(metaDir, className), classBytes);
            encryptClasses.add(className);
            ConsoleLog.debug("加密：%s", className);
        }

        //加密密码hash存储，用来验证密码是否正确
        char[] pchar = EncryptUtils.md5(StrUtils.merger(password, EncryptUtils.SALT));
        pchar = EncryptUtils.md5(StrUtils.merger(EncryptUtils.SALT, pchar));
        IoUtils.writeBytes(new File(metaDir, Constants.CONFIG_PASSHASH), StrUtils.toBytes(pchar));

        return encryptClasses;
    }

    /**
     * 清空class文件的方法体，并保留参数信息
     *
     * @param classFiles jar/war 下需要加密的class文件
     */
    private void clearClassMethod(List<File> classFiles) throws IOException {
        //初始化javassist
        ClassPool pool = ClassPool.getDefault();
        //[1]把所有涉及到的类加入到ClassPool的classpath
        //[1.1]lib目录所有的jar加入classpath
        ClassUtils.loadClassPath(pool, libDir);
        ConsoleLog.debug("ClassPath：%s", libDir.getAbsolutePath());

        //[1.2]外部依赖的lib加入classpath
        for (String path : classPath) {
            ClassUtils.loadClassPath(pool, new File(path));
            ConsoleLog.debug("ClassPath：%s", path);
        }
        //[1.3]要修改的class所在的目录（-INF/classes 和 libjar）加入classpath
        List<String> classPaths = new ArrayList<>();
        classFiles.forEach(classFile -> {
            String classPath = resolveClassName(classFile.getAbsolutePath(), false);
            if (classPaths.contains(classPath)) {
                return;
            }
            try {
                pool.insertClassPath(classPath);
            } catch (NotFoundException e) {
                //Ignore
            }
            classPaths.add(classPath);
            ConsoleLog.debug("ClassPath：%s", classPath);
        });

        //[2]修改class方法体，并保存文件
        for (File classFile : classFiles) {
            //解析出类全名
            String className = resolveClassName(classFile.getAbsolutePath(), true);
            byte[] bts = null;
            try {
                bts = ClassUtils.rewriteAllMethods(pool, className);
            } catch (Exception e) {
                ConsoleLog.debug("ERROR:" + e.getMessage());
            }
            if (bts != null) {
                ConsoleLog.debug("清除方法体：%s", className);
                IoUtils.writeBytes(classFile, bts);
            }
        }
    }

    /**
     * 向jar文件中添加classfinal的代码
     */
    public void addClassFinalAgent() throws IOException {
        List<String> filePath = new ArrayList<>();
        filePath.add(this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath());

        //把本项目的class文件打包进去
        for (String path : filePath) {
            File currentFile = new File(path);
            //jar 包环境
            if (isJar() && path.endsWith(Constants.JAR_EXT)) {
                final List<String> includeFiles = Arrays.asList(Constants.CLASSFINAL_FILES);
                JarUtils.uncompressJar(path, this.tempWork.getAbsolutePath(),
                        name -> !matchClassFile(name, includeFiles));
            }
            //war 包环境
            else if (isWar() && path.endsWith(Constants.WAR_EXT)) {
                File classFinalJar = new File(this.libDir, currentFile.getName());
                Files.copy(currentFile.toPath(), classFinalJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            //开发环境
            else if (path.endsWith("/classes/")) {
                List<File> files = IoUtils.listAllFile(new File(path));
                for (File file : files) {
                    String className = file.getAbsolutePath().substring(currentFile.getAbsolutePath().length());
                    File targetFile = jarOrWar ? this.tempWork : this.classesDir;
                    targetFile = new File(targetFile, className);
                    if (file.isDirectory()) {
                        IoUtils.mkdir(targetFile);
                    } else if (StrUtils.containsAny(file.getAbsolutePath(), Constants.CLASSFINAL_FILES)) {
                        Files.copy(file.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }

        //把 javaagent 信息加入到 MANIFEST.MF
        File manifest = new File(this.tempWork, "META-INF/MANIFEST.MF");
        String preMain = "Premain-Class: " + CoreAgent.class.getName();
        String[] content = {};
        if (manifest.exists()) {
            content = IoUtils.readUtf8(manifest).split("\r\n");
        }

        String str = StrUtils.insertBefore(content, preMain, s -> s.startsWith("Main-Class:"));
        IoUtils.writeUtf8(manifest, str + "\r\n\r\n");
    }

    /**
     * 匹配指定类的 class 文件
     *
     * @param fileName 简单文件名，如：Test.class
     * @param classes  简单类名，如： Test
     * @return 是否匹配
     */
    private boolean matchClassFile(String fileName, List<String> classes) {
        if (fileName.endsWith(Constants.CLASS_EXT)) {
            for (String className : classes) {
                if (fileName.equals(className + Constants.CLASS_EXT)) {
                    return true;
                }
                //支持合成类匹配
                Matcher matcher = SYNTHETIC_PATTERN.matcher(fileName);
                if (matcher.find()) {
                    String sourceName = matcher.group(1);
                    if (sourceName.endsWith("$$Lambda")) {
                        sourceName = sourceName.substring(0,
                                sourceName.length() - "$$Lambda".length());
                    }
                    if (sourceName.startsWith(className)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 加密classes下的配置文件
     */
    private void encryptConfigFile() throws Exception {
        if (cfgfiles == null || cfgfiles.size() == 0) {
            return;
        }

        //支持的框架
        String[] supportFrameworks = {"spring"};
        //需要注入解密功能的class
        List<File> aopClass = new ArrayList<>(supportFrameworks.length);

        for (String framework : supportFrameworks) {
            InjectInfo injectInfo = injectMap.get(framework);
            String classMethod = injectInfo.getClassMethod();
            int line = injectInfo.getLine();
            String javaCode = injectInfo.getCode("${passchar}", toCharArrayCode(password));
            byte[] bytes = null;
            try {
                String currentPath = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
                ClassUtils.loadClassPath(new File(currentPath));
                bytes = ClassUtils.insertCode(classMethod, javaCode, line, libDir);
            } catch (Exception e) {
                ConsoleLog.log("%s:%s", e.getClass().getName(), e.getMessage());
            }
            if (bytes != null) {
                File classFile = new File(tempWork, classMethod.split("#")[0] + ".class");
                IoUtils.writeBytes(classFile, bytes);
                aopClass.add(classFile);
            }
        }

        //加密读取配置文件的类
        encryptClass(aopClass);
        aopClass.forEach(File::delete);


        //[2].加密配置文件
        List<File> configFiles = new ArrayList<>();
        File[] files = classesDir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isFile() && StrUtils.isMatchAny(cfgfiles, file.getName(), false)) {
                configFiles.add(file);
            }
        }
        //加密
        encryptClass(configFiles);
        //清空
        for (File file : configFiles) {
            IoUtils.writeUtf8(file, "");
        }
    }


    /**
     * 转换成 char 数组创建内容代码
     * <pre> new char[]{'a','b', 'c'} => "{'a', 'b', 'c'}"</pre>
     *
     * @param chars 字符数组
     * @return 字符串
     */
    private String toCharArrayCode(char[] chars) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        boolean first = true;
        for (char c : chars) {
            if (!first) {
                sb.append(",");
            }
            sb.append("'").append(c).append("'");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * 打包 jar 文件
     *
     * @param libJars lib下的jar文件
     * @return 打包后的jar绝对路径
     */
    private String packageJar(List<String> libJars) throws IOException {
        //[1]先打包lib下的jar
        libJars.forEach(libJar -> {
            if (!libJar.endsWith(Constants.JAR_EXT)) {
                return;
            }
            //上次解压的目录
            String srcDir = libJar.substring(0, libJar.length() - 4) + Constants.TEMP_SUFFIX;
            if (!IoUtils.exists(srcDir)) {
                return;
            }
            try {
                JarUtils.compressJar(srcDir, libJar);
                IoUtils.delete(new File(srcDir));
                ConsoleLog.debug("打包: %s", libJar);
            } catch (IOException e) {
                throw new RuntimeException(
                        String.format("打包: %s 失败", libJar), e);
            }
        });

        //删除 META-INF下 的 maven
        IoUtils.delete(new File(this.tempWork, "META-INF/maven"));

        //[2]再打包jar
        String outJar = zipPath.replace("." + extName, "-encrypted." + extName);
        String path = JarUtils.compressJar(this.tempWork.getAbsolutePath(), outJar);
        IoUtils.delete(this.tempWork);
        ConsoleLog.debug("打包: %s", outJar);
        return path;
    }

    /**
     * 根据 class 的绝对路径解析出 class名称和 class包所在的路径，根据参数控制返回
     *
     * @param fileName    class绝对路径
     * @param classOrPath true：返回 class名称；false：返回包所在路径
     * @return class名称|包所在的路径
     */
    private String resolveClassName(String fileName, boolean classOrPath) {
        String result = resolveClassInfo.getOrDefault(fileName, Collections.emptyMap()).get(classOrPath);
        if (result != null) {
            return result;
        }
        // fileName.substring(0, fileName.length() - Constants.CLASS_EXT.length() - 1)
        // com/hz/classfinal/Constants
        String filename = fileName.substring(0, fileName.length() - 6);
        String K_CLASSES = File.separator + "classes" + File.separator;
        String K_LIB = File.separator + "lib" + File.separator;

        String codeLocation;
        String className;
        //lib内的的jar包
        if (filename.contains(K_LIB)) {
            // 例如：app__temp__/BOOT-INF/lib/classfinal-core__temp__/com/hz/classfinal/Constants
            //  filename.indexOf(K_LIB) 切分 [app__temp__/BOOT-INF,classfinal-core__temp__/com/hz/classfinal/Constants]
            //  filename.indexOf(Constants.TEMP_SUFFIX, ...) 切分 [classfinal-core, com/hz/classfinal/Constants]
            //  filename.substring(...) 截取 com/hz/classfinal/Constants
            className = filename.substring(filename.indexOf(Constants.TEMP_SUFFIX, filename.indexOf(K_LIB))
                    + Constants.TEMP_SUFFIX.length() + 1);

            // 例如：app__temp__/BOOT-INF/classes/com/hz/classfinal/Constants
            // path: app__temp__/BOOT-INF/lib/classfinal-core__temp__
            codeLocation = filename.substring(0, filename.length() - className.length() - 1);
        }
        //jar/war包-INF/classes下的class文件
        else if (filename.contains(K_CLASSES)) {
            className = filename.substring(filename.indexOf(K_CLASSES) + K_CLASSES.length());
            codeLocation = filename.substring(0, filename.length() - className.length() - 1);
        }
        //jar包下的class文件
        else {
            className = filename.substring(filename.indexOf(Constants.TEMP_SUFFIX) + Constants.TEMP_SUFFIX.length() + 1);
            codeLocation = filename.substring(0, filename.length() - className.length() - 1);
        }
        String resolvedClassName = className.replace(File.separator, ".");
        Map<Boolean, String> node = new HashMap<>(2);
        node.put(Boolean.TRUE, resolvedClassName);
        node.put(Boolean.FALSE, codeLocation);
        resolveClassInfo.put(fileName, node);
        return classOrPath ? resolvedClassName : codeLocation;
    }


    public Integer getEncryptFileCount() {
        return encryptFileCount;
    }

    public void setPackages(List<String> packages) {
        this.packages = packages;
    }

    public void setIncludeJars(List<String> includeJars) {
        this.includeJars = includeJars;
    }

    public void setExcludeClass(List<String> excludeClass) {
        this.excludeClass = excludeClass;
    }

    public void setClassPath(List<String> classPath) {
        this.classPath = classPath;
    }

    public void setCfgfiles(List<String> cfgfiles) {
        this.cfgfiles = cfgfiles;
    }

    public void setCode(char[] code) {
        this.code = code;
    }

    static class InjectInfo {
        private final String classMethod;
        private final String code;
        private final int line;

        public InjectInfo(String classMethod, String code, int line) {
            this.classMethod = classMethod;
            this.code = code;
            this.line = line;
        }

        String getCode(String placeholder, String value){
           return code.replace(placeholder, value);
        }

        String getClassMethod(){
            return classMethod;
        }

        int getLine(){
            return line;
        }
    }
}
