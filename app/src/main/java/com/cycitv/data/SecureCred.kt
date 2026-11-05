package com.cycitv.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 登录凭据本地加密(Android Keystore, AES-256-GCM)。
 *
 * 用途: 保存账号密码, 站方 token(7 天有效)过期时静默重登, 免去用户手动"退出登录再登录"。
 * 密钥由系统 Keystore 持有且不可导出; 密文存 DataStore。
 * 清除 App 数据/卸载后密钥一并失效, 此时 [decrypt] 返回 null, 调用方按"无凭据"处理。
 */
object SecureCred {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "cycitv_cred_v1"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    /** 加密为 "base64(iv):base64(密文)"; 失败返回 null(绝不落明文) */
    fun encrypt(plain: String): String? = runCatching {
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.ENCRYPT_MODE, secretKey())
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP)
    }.getOrNull()

    /** 解密; 密文损坏或密钥失效返回 null */
    fun decrypt(blob: String?): String? = runCatching {
        if (blob.isNullOrBlank()) return null
        val idx = blob.indexOf(':')
        if (idx <= 0) return null
        val iv = Base64.decode(blob.substring(0, idx), Base64.NO_WRAP)
        val ct = Base64.decode(blob.substring(idx + 1), Base64.NO_WRAP)
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        String(c.doFinal(ct), Charsets.UTF_8)
    }.getOrNull()
}

/**
 * JWT 载荷解析(仅取 exp/iat, 不验签)。
 * 站方 token 是 HS256 JWT, exp - iat = 7 天; 用来在临期前主动续登。
 */
object JwtExp {
    /** 返回 exp(Unix 秒); 非 JWT 或解析失败返回 null */
    fun of(token: String?): Long? {
        if (token.isNullOrBlank()) return null
        val parts = token.split('.')
        if (parts.size < 2) return null
        return runCatching {
            val b64 = parts[1].replace('-', '+').replace('_', '/')
            val pad = (4 - b64.length % 4) % 4
            val raw = Base64.decode(b64 + "=".repeat(pad), Base64.NO_WRAP)
            val exp = org.json.JSONObject(String(raw, Charsets.UTF_8)).optLong("exp", 0L)
            exp.takeIf { it > 0L }
        }.getOrNull()
    }

    /** 剩余有效秒数; 无法解析返回 null, 已过期返回负数 */
    fun leftSeconds(token: String?): Long? =
        of(token)?.let { it - System.currentTimeMillis() / 1000 }
}
