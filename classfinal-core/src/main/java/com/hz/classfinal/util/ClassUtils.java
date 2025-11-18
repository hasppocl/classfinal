package com.hz.classfinal.util;

import com.hz.classfinal.Constants;
import javassist.*;
import javassist.bytecode.*;
import javassist.compiler.CompileError;
import javassist.compiler.Javac;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * 字节码操作工具类
 *
 * @author roseboy
 */
public class ClassUtils {

    /**
     * main 方法重写的方法体
     */
    static final String MAIN_REWRITE_BODY = "System.out.println(\"\\nStartup failed, invalid password.\\n\");";

    static final String CLASS_FROZEN_MSG = "类已被冻结: \"%s\"";
    static final String NO_BODY_MSG = "没有方法体1: \"%s\"";
    static final String CANNOT_COMPILE_MSG = "构建方法失败: \"%s\"";
    static final String NOT_FOUND_MSG = "找不到类: \"%s\"";

    /**
     * 清空类中所有方法体
     *
     * @param pool      类字节码池
     * @param classname 全类名
     * @return 返回方法体的字节
     */
    public static byte[] rewriteAllMethods(ClassPool pool, String classname) {
        String name = null;
        try {
            CtClass poolClass = pool.getCtClass(classname);
            CtMethod[] methods = poolClass.getDeclaredMethods();

            for (CtMethod method : methods) {
                name = method.getName();
                if (method.getLongName().startsWith(poolClass.getName())) {
                    CodeAttribute ca = method.getMethodInfo().getCodeAttribute();
                    if (ca == null || ca.getCodeLength() == 1 || ca.getCode()[0] == -79) {
                        continue;
                    }
                    ClassUtils.setBodyKeepParamInfos(method, null, true);
                    if (isMainMethod(method)) {
                        method.insertBefore(MAIN_REWRITE_BODY);
                    }
                }
            }
            return poolClass.toBytecode();
        } catch (Exception e) {
            throw new RuntimeException("无法重写方法: " + classname + "(" + name + ")", e);
        }
    }

    /**
     * 判断方法是否是 main 方法
     *
     * @param method 方法
     * @return 若为main方法返回true，否则返回false
     */
    public static boolean isMainMethod(CtMethod method) {
        try {
            String returnType = method.getReturnType().getName();
            return "void".equalsIgnoreCase(returnType) &&
                    method.getLongName().endsWith(".main(java.lang.String[])") &&
                    method.getMethodInfo().getAccessFlags() == (AccessFlag.PUBLIC & AccessFlag.STATIC);
        } catch (NotFoundException e) {
            return false;
        }
    }

    /**
     * 判断方法是否是构造方法
     *
     * @param methodName 方法名
     * @return 若是构造方法返回true，否则返回false
     */
    public static boolean isConstructor(String methodName) {
        return methodName.startsWith("<init>");
    }

    /**
     * 修改方法体，保留参数信息
     *
     * @param method  方法
     * @param src     方法内容
     * @param rebuild 重新构建
     * @throws CannotCompileException 编译异常
     */
    public static void setBodyKeepParamInfos(CtMethod method, String src, boolean rebuild) throws CannotCompileException {
        CtClass declaringClass = method.getDeclaringClass();
        String longName = method.getLongName();
        if (declaringClass.isFrozen()) {
            throw new RuntimeException(String.format(CLASS_FROZEN_MSG, declaringClass.getName()));
        }
        CodeAttribute attribute = method.getMethodInfo().getCodeAttribute();
        if (attribute == null) {
            throw new CannotCompileException(String.format(NO_BODY_MSG, longName));
        } else {
            CodeIterator iterator = attribute.iterator();
            Javac jc = new Javac(declaringClass);
            try {
                int nvars = jc.recordParams(method.getParameterTypes(), Modifier.isStatic(method.getModifiers()));
                jc.recordParamNames(attribute, nvars);
                jc.recordLocalVariables(attribute, 0);
                jc.recordReturnType(Descriptor.getReturnType(method.getMethodInfo().getDescriptor(), declaringClass.getClassPool()), false);
                Bytecode b = jc.compileBody(method, src);
                int stack = b.getMaxStack();
                int locals = b.getMaxLocals();
                if (stack > attribute.getMaxStack()) {
                    attribute.setMaxStack(stack);
                }
                if (locals > attribute.getMaxLocals()) {
                    attribute.setMaxLocals(locals);
                }
                int pos = iterator.insertEx(b.get());
                iterator.insert(b.getExceptionTable(), pos);
                if (rebuild) {
                    method.getMethodInfo().rebuildStackMapIf6(declaringClass.getClassPool(), declaringClass.getClassFile2());
                }
            } catch (Exception e) {
                throw new CannotCompileException(String.format(CANNOT_COMPILE_MSG, longName), e);
            }
        }
    }

    /**
     * 加载指定 jar 包内的 Class
     *
     * @param paths jar 包路径
     */
    public static void loadClassPath(List<String> paths) {
        loadClassPath(ClassPool.getDefault(), paths);
    }

    /**
     * 加载指定 jar 包内的 Class
     *
     * @param pool  类字节码池
     * @param paths jar 包路径
     */
    public static void loadClassPath(ClassPool pool, List<String> paths) {
        for (String path : paths) {
            loadClassPath(pool, new File(path));
        }
    }

    /**
     * 加载指定 jar 包内的 Class
     *
     * @param dir jar 包路径
     */
    public static void loadClassPath(File dir) {
        loadClassPath(ClassPool.getDefault(), dir);
    }

    /**
     * 加载指定 jar 包内的 Class
     *
     * @param pool 类字节码池
     * @param dir  jar 包路径
     */
    public static void loadClassPath(ClassPool pool, File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }

        if (dir.isDirectory()) {
            List<File> jars = IoUtils.listFile(dir, Constants.JAR_EXT);
            for (File jar : jars) {
                try {
                    pool.insertClassPath(jar.getAbsolutePath());
                } catch (NotFoundException e) {
                    //ignore
                }
            }
        } else if (dir.getName().endsWith(Constants.JAR_EXT)) {
            try {
                pool.insertClassPath(dir.getAbsolutePath());
            } catch (NotFoundException e) {
                //ignore
            }
        }
    }

    /**
     * 给指定方法插入代码并返回类的字节数组。传入的方法名格式需要包含完整的方法签名，例如:
     * {@code java.lang.String#concat(java.lang.String)   }
     *
     * @param classMethod 方法名
     * @param javaCode    代码
     * @param line        行数
     * @param libPath     jar包路径
     * @return 字节数组
     * @throws Exception Exception
     */
    public static byte[] insertCode(String classMethod, String javaCode, int line, File libPath) throws Exception {
        // classname#methodname
        String[] cmp = classMethod.split("#");
        if (cmp.length != 2) {
            throw new IllegalArgumentException("方法名格式错误: " + classMethod);
        }
        String className = cmp[0];
        String methodName = cmp[1];
        ClassPool pool = ClassPool.getDefault();
        loadClassPath(pool, libPath);
        CtClass poolClass = pool.getCtClass(className);
        if (isConstructor(methodName)) {
            //eg (Ljava/lang/String)
            final String signature = methodName.substring(6);
            for (CtConstructor constructor : poolClass.getConstructors()) {
                if (constructor.getLongName().endsWith(signature)) {
                    constructor.insertAt(line, javaCode);
                }
            }
        } else {
            poolClass.getDeclaredMethod(methodName).insertAt(line, javaCode);
        }
        return poolClass.toBytecode();
    }


}
