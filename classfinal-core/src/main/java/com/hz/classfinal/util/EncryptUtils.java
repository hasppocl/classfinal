package com.hz.classfinal.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 简单加密解密
 *
 * @author roseboy
 */
public class EncryptUtils {

    //盐
    public static final char[] SALT = "com.hz.classFinal".toCharArray();
    static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();
    //rsa 长度
    static final int KEY_LENGTH = 1024;
    //基础字符数组
    static final Character[] chars = new Character[]{
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
            'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
            'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
            '!', '@', '#', '$', '%', '^', '&', '*', '(', ')', '-', '=', '_', '+', '.'};


    /**
     * 加密
     *
     * @param msg  内容
     * @param key  密钥
     * @param type 类型
     * @return 密文
     */
    public static byte[] encryption(byte[] msg, char[] key, EncryptType type) throws GeneralSecurityException {
        switch (type) {
            case MD5:
                return xorMd5byte(msg, 0, msg.length, key);
            case AES:
                return enAES(msg, md5(StrUtils.merger(key, SALT), true));
            case RSA:
                return enRSA(msg, md5(StrUtils.merger(key, SALT), true));
            default:
                throw new IllegalArgumentException("不支持的加密类型");
        }
    }

    /**
     * 解密
     *
     * @param msg  密文
     * @param key  密钥
     * @param type 类型
     * @return 明文
     */
    public static byte[] decryption(byte[] msg, char[] key, EncryptType type) throws GeneralSecurityException {
        switch (type) {
            case MD5:
                return xorMd5byte(msg, 0, msg.length, key);
            case AES:
                return deAES(msg, md5(StrUtils.merger(key, SALT), true));
            case RSA:
                return deRSA(msg, md5(StrUtils.merger(key, SALT), true));
            default:
                throw new IllegalArgumentException("不支持的加密类型");
        }
    }

    /**
     * md5加密
     *
     * @param chars 字符数组
     * @return 字节数组
     */
    public static byte[] md5byte(char[] chars) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(StrUtils.toBytes(chars));
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * md5
     *
     * @param str 字串
     * @return 32位md5
     */
    public static char[] md5(char[] str) {
        return md5(str, false);
    }

    /**
     * md5
     *
     * @param str   字串
     * @param small 是否16位
     * @return 32位/16位md5
     */
    public static char[] md5(char[] str, boolean small) {
        byte[] bytes = md5byte(str);
        if (bytes == null) return null;

        int begin = small ? 8 : 0;
        int end = small ? 16 : bytes.length;
        int len = end - begin;
        char[] result = new char[len * 2];
        for (int i = 0; i < len; i++) {
            byte b = bytes[begin + i];
            result[i * 2] = HEX_DIGITS[(b >> 4) & 0xF];
            result[i * 2 + 1] = HEX_DIGITS[b & 0xF];
        }
        return result;
    }

    /**
     * 将加密内容与密钥 MD5 编码后进行异或操作
     *
     * @param msg   加密内容
     * @param start 起始位置
     * @param end   结束位置
     * @param key   密钥
     * @return 密文
     */
    private static byte[] xorMd5byte(byte[] msg, int start, int end, char[] key) {
        byte[] bs1 = md5byte(StrUtils.merger(key, SALT));
        byte[] bs2 = md5byte(StrUtils.merger(SALT, key));
        byte[] keys = IoUtils.merger(bs1, bs2);
        for (int i = start; i < end; i++) {
            msg[i] = (byte) (msg[i] ^ keys[i % keys.length]);
        }
        return msg;
    }


    /**
     * RSA 公钥加密
     *
     * @param msg       加密内容
     * @param publicKey base64编码的公钥
     * @return 密文
     */
    public static byte[] enRSA(byte[] msg, char[] publicKey) throws GeneralSecurityException {
        return enRSA(msg, StrUtils.toBytes(publicKey));
    }

    /**
     * RSA公钥加密
     *
     * @param msg       加密内容
     * @param publicKey base64编码的公钥
     * @return 密文
     */
    public static byte[] enRSA(byte[] msg, byte[] publicKey) throws GeneralSecurityException {
        byte[] decoded = Base64.getDecoder().decode(publicKey);
        RSAPublicKey pubKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(decoded));
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, pubKey);
        return cipherDoFinal(cipher, msg, Cipher.ENCRYPT_MODE);
    }

    /**
     * RSA 私钥解密
     *
     * @param msg        解密内容
     * @param privateKey base64编码的私钥
     * @return 明文
     */
    public static byte[] deRSA(byte[] msg, char[] privateKey) throws GeneralSecurityException {
        return deRSA(msg, StrUtils.toBytes(privateKey));
    }

    /**
     * RSA私钥解密
     *
     * @param msg        解密内容
     * @param privateKey base64编码的私钥
     * @return 明文
     */
    public static byte[] deRSA(byte[] msg, byte[] privateKey) throws GeneralSecurityException {
        byte[] decoded = Base64.getDecoder().decode(privateKey);
        RSAPrivateKey priKey = (RSAPrivateKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(decoded));
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, priKey);
        return cipherDoFinal(cipher, msg, Cipher.DECRYPT_MODE);
    }

    /**
     * 调用加密解密
     *
     * @param cipher Cipher
     * @param msg    要加密的字节
     * @param mode   解密/解密
     * @return 结果
     */
    private static byte[] cipherDoFinal(Cipher cipher, byte[] msg, int mode) throws GeneralSecurityException {
        int length = 0;
        if (mode == Cipher.ENCRYPT_MODE) {
            length = KEY_LENGTH / 8 - 11;
        } else if (mode == Cipher.DECRYPT_MODE) {
            length = KEY_LENGTH / 8;
        }

        byte[] in = new byte[length];
        byte[] out = new byte[0];

        for (int i = 0; i < msg.length; i++) {
            if (msg.length - i < length && i % length == 0) {
                in = new byte[msg.length - i];
            }
            in[i % length] = msg[i];
            if (i == (msg.length - 1) || (i % length + 1 == length)) {
                out = IoUtils.merger(out, cipher.doFinal(in));
            }
        }
        return out;
    }

    /**
     * 生成 RSA 密钥对
     *
     * @return 密钥信息
     * @throws NoSuchAlgorithmException NoSuchAlgorithmException
     */
    public static Map<Integer, String> genRSAKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
        keyPairGen.initialize(KEY_LENGTH, new SecureRandom());
        KeyPair keyPair = keyPairGen.generateKeyPair();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();   // 得到私钥
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();  // 得到公钥
        BigInteger publicExponent = publicKey.getPublicExponent();
        BigInteger modulus = publicKey.getModulus();

        String publicKeyString = new String(Base64.getEncoder().encode(publicKey.getEncoded()));
        String privateKeyString = new String(Base64.getEncoder().encode((privateKey.getEncoded())));

        Map<Integer, String> keyMap = new HashMap<>();
        keyMap.put(0, publicKeyString);  //0表示公钥
        keyMap.put(1, privateKeyString);  //1表示私钥
        keyMap.put(2, modulus.toString(16));//modulus
        keyMap.put(3, publicExponent.toString(16));//e
        return keyMap;
    }

    /**
     * AES加密字节
     *
     * @param msg 加密内容
     * @param key 密钥
     * @return 密文
     */
    public static byte[] enAES(byte[] msg, char[] key) throws GeneralSecurityException {
        SecretKeySpec spec = new SecretKeySpec(StrUtils.toBytes(key), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, spec);
        return cipher.doFinal(msg);
    }

    /**
     * AES解密
     *
     * @param msg 解密内容
     * @param key 密钥
     * @return 明文
     */
    public static byte[] deAES(byte[] msg, char[] key) throws GeneralSecurityException {
        SecretKeySpec spec = new SecretKeySpec(StrUtils.toBytes(key), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, spec);
        return cipher.doFinal(msg);
    }


    /**
     * 随机字串串
     *
     * @param length 长度
     * @return 字符数组
     */
    public static char[] randomChars(int length) {
        char[] result = new char[length];
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int maxLength = chars.length;
        for (int i = 0; i < length; i++) {
            result[i] = chars[random.nextInt(maxLength)];
        }
        return result;
    }
}
