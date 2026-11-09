package com.hebeu.emptyclassroom.data.remote

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import javax.crypto.Cipher

/**
 * 教务系统登录密码加密（与网页 `rsa.js` 的 jsbn 实现等价）。
 *
 * 真实协议（docs/recon/site-contract.md §2.1）：
 * - `GET /xtgl/login_getPublicKey.html` 返回 `{"modulus":"<base64>","exponent":"AQAB"}`
 * - modulus 的 base64 解码后可能带前导 0x00（Java BigInteger 符号位），按**无符号**大整数处理
 * - 加密 = RSA/ECB/PKCS1Padding（PKCS#1 v1.5）+ 标准 Base64
 *
 * 注意：PKCS#1 v1.5 填充含随机数，**同一密码每次密文不同**，测试需用私钥解密校验。
 */
object RsaCrypto {

    private const val TRANSFORMATION = "RSA/ECB/PKCS1Padding"

    /**
     * 用服务端公钥加密密码，返回标准 Base64 字符串（网页端 `hex2b64` 的结果）。
     *
     * @throws IllegalArgumentException 公钥格式非法
     */
    fun encryptPassword(password: String, modulusBase64: String, exponentBase64: String): String {
        val key = parsePublicKey(modulusBase64, exponentBase64)
        return encrypt(password, key)
    }

    fun encrypt(password: String, publicKey: PublicKey): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val encrypted = cipher.doFinal(password.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(encrypted)
    }

    /** 解析服务端下发的公钥。base64 解码后按无符号大整数处理。 */
    fun parsePublicKey(modulusBase64: String, exponentBase64: String): PublicKey {
        val modulus = decodeUnsignedBigInteger(modulusBase64)
        val exponent = decodeUnsignedBigInteger(exponentBase64)
        require(modulus.bitLength() >= 512) { "RSA modulus too short: ${modulus.bitLength()}" }
        val spec = RSAPublicKeySpec(modulus, exponent)
        return KeyFactory.getInstance("RSA").generatePublic(spec)
    }

    /**
     * base64 -> 无符号大整数。
     *
     * 服务端 modulus 常见带 1 字节前导 0x00（Java 有符号表示的符号位），
     * [BigInteger] 的 `signum=1` 构造器本身就按无符号解释，因此无需手工裁掉。
     */
    fun decodeUnsignedBigInteger(base64: String): BigInteger {
        val bytes = Base64.getMimeDecoder().decode(base64.trim())
        return BigInteger(1, bytes)
    }
}
