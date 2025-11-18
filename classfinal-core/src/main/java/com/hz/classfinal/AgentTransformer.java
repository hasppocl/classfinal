package com.hz.classfinal;

import com.hz.classfinal.util.ConsoleLog;
import com.hz.classfinal.util.JarUtils;
import com.hz.classfinal.util.StrUtils;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.Objects;


/**
 * AgentTransformer
 */
public class AgentTransformer implements ClassFileTransformer {

    private final char[] password;

    /**
     * 构造方法
     *
     * @param password 解密的密码
     */
    public AgentTransformer(char[] password) {
        this.password = Objects.requireNonNull(password);
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain domain, byte[] classBuffer) {

        if (className == null || domain == null || loader == null) {
            return classBuffer;
        }
        //eg  D:/project/test/target/classes/com/demo
        //eg  nested:D:/project/test/demo.jar!/com/demo
        String locationPath = domain.getCodeSource().getLocation().getPath();

        locationPath = JarUtils.getRootPath(locationPath);
        if (StrUtils.isEmpty(locationPath)) {
            return classBuffer;
        }
        className = className.replace("/", ".").replace("\\", ".");

        try {
            byte[] decryptBytes = JarDecryptor.getInstance().doDecrypt(locationPath, className, this.password);
            if (decryptBytes != null
                    && decryptBytes[0] == -54
                    && decryptBytes[1] == -2
                    && decryptBytes[2] == -70
                    && decryptBytes[3] == -66) {
                return decryptBytes;
            }
        } catch (Exception e) {
            ConsoleLog.debug(e.getMessage());
        }
        return classBuffer;

    }
}
