package com.example.courseschedule.import

import com.example.courseschedule.data.Course
import com.example.courseschedule.data.WeekType
import com.example.courseschedule.ui.theme.courseColorArgb
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Base64

/**
 * WakeUp 课程表「分享口令」导入。
 *
 * 原理：口令是一段分享码，需要请求 WakeUp 的服务器换取课表数据。
 * 注意：这是对第三方服务的非公开接口调用，可能因对方调整而失效。
 */
object WakeUpImporter {

    private const val API_HOST = "https://api.wakeup.fun"
    private const val ANTISPAM_PATH = "/pluto/app/antispam"
    private const val SHARE_PATH = "/share_schedule/getv2"

    private const val APP_VERSION_CODE = 530
    private const val APP_VERSION_NAME = "6.4.0"
    private const val APP_PACKAGE = "com.suda.yzune.wakeupschedule"
    private const val APP_CHANNEL = "100271a"
    private const val APP_PUBLIC_TOKEN = "1_XPXQH3c5HRPtFHkSwi3sCCURmT25QfxM"
    private const val ANDROID_ID = "0000000000000000"

    private const val MAGIC = "8&%d*"
    private const val SIGN_A_KEY = "@fG2SuLA"
    private const val KEY_SALT = "@#AIjd83#@6B"

    // DES 置换表
    private val IP = intArrayOf(
        57, 49, 41, 33, 25, 17, 9, 1, 59, 51, 43, 35, 27, 19, 11, 3,
        61, 53, 45, 37, 29, 21, 13, 5, 63, 55, 47, 39, 31, 23, 15, 7,
        56, 48, 40, 32, 24, 16, 8, 0, 58, 50, 42, 34, 26, 18, 10, 2,
        60, 52, 44, 36, 28, 20, 12, 4, 62, 54, 46, 38, 30, 22, 14, 6,
    )
    private val FP = intArrayOf(
        39, 7, 47, 15, 55, 23, 63, 31, 38, 6, 46, 14, 54, 22, 62, 30,
        37, 5, 45, 13, 53, 21, 61, 29, 36, 4, 44, 12, 52, 20, 60, 28,
        35, 3, 43, 11, 51, 19, 59, 27, 34, 2, 42, 10, 50, 18, 58, 26,
        33, 1, 41, 9, 49, 17, 57, 25, 32, 0, 40, 8, 48, 16, 56, 24,
    )
    private val E = intArrayOf(
        31, 0, 1, 2, 3, 4, 3, 4, 5, 6, 7, 8, 7, 8, 9, 10, 11, 12, 11, 12, 13, 14, 15, 16,
        15, 16, 17, 18, 19, 20, 19, 20, 21, 22, 23, 24, 23, 24, 25, 26, 27, 28, 27, 28, 29, 30, 31, 0,
    )
    private val P = intArrayOf(
        15, 6, 19, 20, 28, 11, 27, 16, 0, 14, 22, 25, 4, 17, 30, 9,
        1, 7, 23, 13, 31, 26, 2, 8, 18, 12, 29, 5, 21, 10, 3, 24,
    )
    private val PC1 = intArrayOf(
        56, 48, 40, 32, 24, 16, 8, 0, 57, 49, 41, 33, 25, 17, 9, 1,
        58, 50, 42, 34, 26, 18, 10, 2, 59, 51, 43, 35, 62, 54, 46, 38,
        30, 22, 14, 6, 61, 53, 45, 37, 29, 21, 13, 5, 60, 52, 44, 36, 28, 20, 12, 4, 27, 19, 11, 3,
    )
    private val PC2 = intArrayOf(
        13, 16, 10, 23, 0, 4, 2, 27, 14, 5, 20, 9, 22, 18, 11, 3, 25, 7, 15, 6, 26, 19, 12, 1,
        40, 51, 30, 36, 46, 54, 29, 39, 50, 44, 32, 47, 43, 48, 38, 55, 33, 52, 45, 41, 49, 35, 28, 31,
    )
    private val SHIFTS = intArrayOf(1, 1, 2, 2, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 1)

    // 标准 DES S 盒（8 个盒，每盒 4 行 × 16 列）
    private val SBOX: Array<Array<IntArray>> = arrayOf(
        arrayOf(
            intArrayOf(14, 4, 13, 1, 2, 15, 11, 8, 3, 10, 6, 12, 5, 9, 0, 7),
            intArrayOf(0, 15, 7, 4, 14, 2, 13, 1, 10, 6, 12, 11, 9, 5, 3, 8),
            intArrayOf(4, 1, 14, 8, 13, 6, 2, 11, 15, 12, 9, 7, 3, 10, 5, 0),
            intArrayOf(15, 12, 8, 2, 4, 9, 1, 7, 5, 11, 3, 14, 10, 0, 6, 13),
        ),
        arrayOf(
            intArrayOf(15, 1, 8, 14, 6, 11, 3, 4, 9, 7, 2, 13, 12, 0, 5, 10),
            intArrayOf(3, 13, 4, 7, 15, 2, 8, 14, 12, 0, 1, 10, 6, 9, 11, 5),
            intArrayOf(0, 14, 7, 11, 10, 4, 13, 1, 5, 8, 12, 6, 9, 3, 2, 15),
            intArrayOf(13, 8, 10, 1, 3, 15, 4, 2, 11, 6, 7, 12, 0, 5, 14, 9),
        ),
        arrayOf(
            intArrayOf(10, 0, 9, 14, 6, 3, 15, 5, 1, 13, 12, 7, 11, 4, 2, 8),
            intArrayOf(13, 7, 0, 9, 3, 4, 6, 10, 2, 8, 5, 14, 12, 11, 15, 1),
            intArrayOf(13, 6, 4, 9, 8, 15, 3, 0, 11, 1, 2, 12, 5, 10, 14, 7),
            intArrayOf(1, 10, 13, 0, 6, 9, 8, 7, 4, 15, 14, 3, 11, 5, 2, 12),
        ),
        arrayOf(
            intArrayOf(7, 13, 14, 3, 0, 6, 9, 10, 1, 2, 8, 5, 11, 12, 4, 15),
            intArrayOf(13, 8, 11, 5, 6, 15, 0, 3, 4, 7, 2, 12, 1, 10, 14, 9),
            intArrayOf(10, 6, 9, 0, 12, 11, 7, 13, 15, 1, 3, 14, 5, 2, 8, 4),
            intArrayOf(3, 15, 0, 6, 10, 1, 13, 8, 9, 4, 5, 11, 12, 7, 2, 14),
        ),
        arrayOf(
            intArrayOf(2, 12, 4, 1, 7, 10, 11, 6, 8, 5, 3, 15, 13, 0, 14, 9),
            intArrayOf(14, 11, 2, 12, 4, 7, 13, 1, 5, 0, 15, 10, 3, 9, 8, 6),
            intArrayOf(4, 2, 1, 11, 10, 13, 7, 8, 15, 9, 12, 5, 6, 3, 0, 14),
            intArrayOf(11, 8, 12, 7, 1, 14, 2, 13, 6, 15, 0, 9, 10, 4, 5, 3),
        ),
        arrayOf(
            intArrayOf(12, 1, 10, 15, 9, 2, 6, 8, 0, 13, 3, 4, 14, 7, 5, 11),
            intArrayOf(10, 15, 4, 2, 7, 12, 9, 5, 6, 1, 13, 14, 0, 11, 3, 8),
            intArrayOf(9, 14, 15, 5, 2, 8, 12, 3, 7, 0, 4, 10, 1, 13, 11, 6),
            intArrayOf(4, 3, 2, 12, 9, 5, 15, 10, 11, 14, 1, 7, 6, 0, 8, 13),
        ),
        arrayOf(
            intArrayOf(4, 11, 2, 14, 15, 0, 8, 13, 3, 12, 9, 7, 5, 10, 6, 1),
            intArrayOf(13, 0, 11, 7, 4, 9, 1, 10, 14, 3, 5, 12, 2, 15, 8, 6),
            intArrayOf(1, 4, 11, 13, 12, 3, 7, 14, 10, 15, 6, 8, 0, 5, 9, 2),
            intArrayOf(6, 11, 13, 8, 1, 4, 10, 7, 9, 5, 0, 15, 14, 2, 3, 12),
        ),
        arrayOf(
            intArrayOf(13, 2, 8, 4, 6, 15, 11, 1, 10, 9, 3, 14, 5, 0, 12, 7),
            intArrayOf(1, 15, 13, 8, 10, 3, 7, 4, 12, 5, 6, 11, 0, 14, 9, 2),
            intArrayOf(7, 11, 4, 1, 9, 12, 14, 2, 0, 6, 10, 13, 15, 3, 5, 8),
            intArrayOf(2, 1, 14, 7, 4, 10, 8, 13, 15, 12, 9, 0, 3, 5, 6, 11),
        ),
    )

    class WakeUpException(message: String) : Exception(message)

    // ---------- 位运算辅助 ----------
    private fun bitsFromBytes(value: ByteArray): List<Int> =
        value.flatMap { b -> (0..7).map { bit -> (b.toInt() shr bit) and 1 } }

    private fun bytesFromBits(bits: List<Int>): ByteArray {
        val out = ByteArray(bits.size / 8)
        for (i in out.indices) {
            var b = 0
            for (bit in 0..7) b = b or ((bits[i * 8 + bit] and 1) shl bit)
            out[i] = b.toByte()
        }
        return out
    }

    private fun permute(bits: List<Int>, table: IntArray): List<Int> = table.map { bits[it] }

    private fun desSubkeys(key: String): List<List<Int>> {
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        // DES 密钥固定 8 字节，不足补零、超出截断
        val normalized = ByteArray(8) { i -> if (i < keyBytes.size) keyBytes[i] else 0 }
        val bits = permute(bitsFromBytes(normalized), PC1)
        if (bits.size < 56) throw WakeUpException("密钥置换失败")
        var left = bits.subList(0, 28).toMutableList()
        var right = bits.subList(28, 56).toMutableList()
        val subkeys = mutableListOf<List<Int>>()
        for (shift in SHIFTS) {
            left = (left.drop(shift) + left.take(shift)).toMutableList()
            right = (right.drop(shift) + right.take(shift)).toMutableList()
            subkeys.add(permute(left + right, PC2))
        }
        return subkeys
    }

    private fun desRound(right: List<Int>, subkey: List<Int>): List<Int> {
        val expanded = permute(right, E)
        // E 置换应得到 48 位，与子密钥长度一致
        if (expanded.size != subkey.size) throw WakeUpException("DES 轮函数参数不匹配")
        val mixed = expanded.zip(subkey) { a, b -> a xor b }
        val result = mutableListOf<Int>()
        for (boxIndex in 0..7) {
            val start = boxIndex * 6
            if (start + 6 > mixed.size) throw WakeUpException("S 盒输入越界")
            val block = mixed.subList(start, start + 6)
            val row = block[0] * 2 + block[5]
            val col = block[1] * 8 + block[2] * 4 + block[3] * 2 + block[4]
            if (row !in 0..3 || col !in 0..15) throw WakeUpException("S 盒索引越界")
            val value = SBOX[boxIndex][row][col]
            result.addAll(listOf((value shr 3) and 1, (value shr 2) and 1, (value shr 1) and 1, value and 1))
        }
        return permute(result, P)
    }

    private fun desBlock(block: ByteArray, subkeys: List<List<Int>>): ByteArray {
        if (block.size != 8) throw WakeUpException("DES 分组长度必须为 8 字节")
        val bits = permute(bitsFromBytes(block), IP)
        if (bits.size < 64) throw WakeUpException("初始置换失败")
        var left = bits.subList(0, 32).toMutableList()
        var right = bits.subList(32, 64).toMutableList()
        for (subkey in subkeys) {
            val round = desRound(right, subkey)
            val newRight = left.zip(round) { a, b -> a xor b }.toMutableList()
            left = right
            right = newRight
        }
        return bytesFromBits(permute(right + left, FP))
    }

    private fun desEncrypt(value: String, key: String): ByteArray {
        val plain = value.toByteArray(Charsets.UTF_8)
        val padding = 8 - plain.size % 8
        val padded = plain + ByteArray(padding - 1) + byteArrayOf(padding.toByte())
        val subkeys = desSubkeys(key)
        val chunks = padded.toList().chunked(8).map { it.toByteArray() }
        if (chunks.isEmpty()) throw WakeUpException("待加密数据为空")
        return chunks.map { desBlock(it, subkeys) }.reduce { a, b -> a + b }
    }

    private fun desDecrypt(value: ByteArray, key: String): ByteArray {
        if (value.isEmpty() || value.size % 8 != 0) throw WakeUpException("WakeUp 响应数据格式异常")
        val subkeys = desSubkeys(key).reversed()
        val plain = value.toList().chunked(8) { it.toByteArray() }
            .map { desBlock(it, subkeys) }
            .reduce { a, b -> a + b }
        if (plain.isEmpty()) throw WakeUpException("WakeUp 响应解密为空")
        val padding = plain.last().toInt()
        if (padding < 1 || padding > 8) throw WakeUpException("WakeUp 响应解密失败")
        return plain.copyOfRange(0, plain.size - padding)
    }

    private fun reverseNibble(value: Int): Int =
        ((value and 1) shl 3) or ((value and 2) shl 1) or ((value and 4) shr 1) or ((value and 8) shr 3)

    private fun nativeHexEncode(value: ByteArray): String = value.joinToString("") { b ->
        val low = reverseNibble(b.toInt() and 0x0F)
        val high = reverseNibble((b.toInt() shr 4) and 0x0F)
        "%02x%02x".format(low, high)
    }

    private fun nativeHexDecode(value: String): ByteArray {
        // 长度必须是 4 的倍数，且只含十六进制字符，否则直接判定格式错误
        val hex = value.trim()
        if (hex.isEmpty() || hex.length % 4 != 0) {
            throw WakeUpException("响应数据长度异常")
        }
        if (!hex.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
            throw WakeUpException("响应数据包含非法字符")
        }
        val out = ByteArray(hex.length / 4)
        for (i in out.indices) {
            val idx = i * 4
            val low = reverseNibble(hex[idx + 1].digitToInt(16))
            val high = reverseNibble(hex[idx + 3].digitToInt(16))
            out[i] = (low or (high shl 4)).toByte()
        }
        return out
    }

    private fun md5Hex(value: String): String =
        MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun nativeKey(token: String): String {
        val first = md5Hex(KEY_SALT)
        val second = md5Hex(APP_VERSION_CODE.toString())
        val thirdRaw = md5Hex("[$token]@")
        // MD5 输出固定 32 位；不足时直接判定失败，避免 substring 越界
        if (thirdRaw.length < 32) throw WakeUpException("内部密钥计算异常")
        val third = thirdRaw.substring(17, 32).reversed() +
            thirdRaw.substring(15, 17) +
            thirdRaw.substring(0, 15).reversed()

        val chars = (first + second + third).toMutableList()
        if (chars.size < 3) throw WakeUpException("内部密钥长度不足")
        for (i in 0..2) {
            val tmp = chars[i]; chars[i] = chars[chars.size - 1 - i]; chars[chars.size - 1 - i] = tmp
        }
        val mixed = chars.joinToString("")
        val chars2 = (mixed + md5Hex(mixed)).toMutableList()
        // 循环上界取实际长度，避免固定 60 导致越界
        val swapCount = minOf(60, chars2.size / 2)
        for (i in 0 until swapCount) {
            val tmp = chars2[i]; chars2[i] = chars2[chars2.size - 1 - i]; chars2[chars2.size - 1 - i] = tmp
        }
        return chars2.joinToString("")
    }

    private fun nativeSign(base64Params: String, token: String): String =
        md5Hex("$MAGIC[${md5Hex(token)}]@$base64Params")

    private fun formEncode(items: List<Pair<String, String>>): String =
        items.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }

    private fun buildShareRequest(token: String, code: String, common: List<Pair<String, String>>): Pair<String, String> {
        val rc4Key = nativeKey(token)
        val plain = "key=" + URLEncoder.encode(code, "UTF-8")
        val encrypted = nativeHexEncodeRc4(plain.toByteArray(Charsets.UTF_8), rc4Key)
        val timestamp = System.currentTimeMillis()
        val monotonic = System.nanoTime() / 1_000_000
        val signItems = mutableListOf("data=$encrypted")
        common.forEach { (k, v) -> signItems.add("$k=$v") }
        signItems.add("nt=wifi")
        signItems.add("_t_=$timestamp")
        signItems.add("kakorrhaphiophobia=$monotonic")
        val encoded = b64(signItems.sorted().joinToString("").toByteArray(Charsets.UTF_8))
        val sign = nativeSign(encoded, token)
        var body = "&" + formEncode(listOf("data" to encrypted) + common + listOf("nt" to "wifi"))
        body += "&sign=$sign&_t_=$timestamp&kakorrhaphiophobia=$monotonic"
        return body to rc4Key
    }

    private fun b64(value: ByteArray): String = Base64.getEncoder().encodeToString(value)

    /** 注意：这里是 RC4 + 特殊编码，与 Python 版一致 */
    private fun nativeHexEncodeRc4(value: ByteArray, key: String): String {
        val rc4ed = rc4(value, key)
        return nativeHexEncode(rc4ed)
    }

    private fun rc4(value: ByteArray, key: String): ByteArray {
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        // 空密钥会导致取模除零，必须拦截
        if (keyBytes.isEmpty()) throw WakeUpException("密钥为空")
        val state = IntArray(256) { it }
        var cursor = 0
        for (i in 0..255) {
            cursor = (cursor + state[i] + (keyBytes[i % keyBytes.size].toInt() and 0xFF)) and 0xFF
            val tmp = state[i]; state[i] = state[cursor]; state[cursor] = tmp
        }
        val out = ByteArray(value.size)
        var left = 0
        cursor = 0
        for (i in value.indices) {
            left = (left + 1) and 0xFF
            cursor = (cursor + state[left]) and 0xFF
            val tmp = state[left]; state[left] = state[cursor]; state[cursor] = tmp
            out[i] = (value[i].toInt() xor state[(state[left] + state[cursor]) and 0xFF]).toByte()
        }
        return out
    }

    /** 兜底：AES 版本（若 DES 版本失效时可用） */
    private fun aesKey(token: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(nativeKey(token).take(32).toByteArray(Charsets.UTF_8))

    /** 解析口令，返回课程列表 */
    fun import(code: String): ImportResult {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return ImportResult(warnings = listOf("口令为空"))

        // 捕获 Throwable：包括 ExceptionInInitializerError 等 Error，避免整个 App 闪退
        return try {
            // 显式触发类初始化，把初始化异常也纳入捕获范围
            ensureInitialized()
            doImport(trimmed)
        } catch (e: Throwable) {
            ImportResult(warnings = listOf("导入失败：${friendlyMessage(e)}"))
        }
    }

    /** 把底层异常转成对用户友好的提示 */
    private fun friendlyMessage(e: Throwable): String {
        val raw = e.message.orEmpty()
        return when {
            raw.contains("radix") || raw.contains("For input string") ->
                "口令格式不正确，请确认复制完整（WakeUp 口令较长，注意不要漏字符）"
            raw.contains("Unable to resolve host") || raw.contains("Failed to connect") ||
                raw.contains("timeout", true) || raw.contains("Unable to resolve") ->
                "网络连接失败，请检查网络后重试"
            raw.contains("反作弊") -> "WakeUp 服务器校验失败，该导入方式可能已失效"
            raw.isBlank() -> e.javaClass.simpleName
            else -> raw
        }
    }

    @Volatile
    private var initChecked = false

    private fun ensureInitialized() {
        if (initChecked) return
        val bad = SBOX.size != 8 || SBOX.any { it.size != 4 || it.any { row -> row.size != 16 } }
        if (bad) throw WakeUpException("内部 S 盒数据异常")
        initChecked = true
    }

    private fun doImport(trimmed: String): ImportResult {
        val common = commonParams()
        val headers = mapOf(
            "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
            "User-Agent" to "okhttp/4.12.0",
            "na__zyb_source__" to "wakeup",
        )

        // 第一步：获取 token
        val (signAValue, randomValue) = makeSignA()
        val antispamBody = formEncode(listOf("data" to signAValue) + common) + "&"
        val antispamResp = post(ANTISPAM_PATH, antispamBody, headers)
        val signB = extractSignB(antispamResp)
        val token = tokenFromSignB(signB, randomValue)

        // 第二步：请求分享数据
        val (shareBody, rc4Key) = buildShareRequest(token, trimmed, common)
        val shareResp = post(SHARE_PATH, shareBody, headers)

        val shareData = shareResp.optJSONObject("data")?.optString("data")
            ?: shareResp.optString("data")
        if (shareData.isNullOrBlank()) {
            return ImportResult(warnings = listOf("口令无效或已失效"))
        }

        // Base64 解码可能抛异常（非法字符）
        val decoded = try {
            Base64.getDecoder().decode(shareData)
        } catch (e: Throwable) {
            return ImportResult(warnings = listOf("口令格式不正确，请确认复制完整"))
        }

        val decrypted = try {
            rc4(decoded, rc4Key).toString(Charsets.UTF_8)
        } catch (e: Throwable) {
            return ImportResult(warnings = listOf("口令解密失败，可能已失效"))
        }

        return parseSchedule(decrypted)
    }

    private fun commonParams(): List<Pair<String, String>> {
        val cuid = md5Hex("com.baidu$ANDROID_ID").uppercase() + "|0"
        val prefix = md5Hex("alpha.beta$ANDROID_ID")
        // MD5 固定 32 位十六进制，但仍做长度与进制校验，避免任何越界/格式异常
        val safePrefix = if (prefix.length >= 32 && prefix.take(32).all { it.isDigit() || it in 'a'..'f' }) {
            prefix.take(32)
        } else {
            "0123456789abcdef0123456789abcdef"
        }
        val words = (0 until 32 step 8).map { safePrefix.substring(it, it + 8).toInt(16) }
        val checksum = words[0] xor words[1] xor words[2] xor words[3]
        val adid = safePrefix + "%08x".format(checksum)
        return listOf(
            "area" to "", "screensize" to "1080x2400", "cuid" to cuid,
            "os" to "android", "city" to "", "abis" to "arm64-v8a",
            "channel" to APP_CHANNEL, "appBit" to "64", "vc" to APP_VERSION_CODE.toString(),
            "deviceId" to "", "token" to APP_PUBLIC_TOKEN, "adid" to adid,
            "province" to "", "pkgName" to APP_PACKAGE, "appId" to "wakeup",
            "download_type" to "1", "vcname" to APP_VERSION_NAME, "sdk" to "35",
            "device" to "Pixel 7", "brand" to "google", "operatorid" to "",
        )
    }

    private fun makeSignA(): Pair<String, String> {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val randomValue = (1..10).map { alphabet.random() }.joinToString("")
        val cuid = md5Hex("com.baidu$ANDROID_ID").uppercase() + "|0"
        val plain = "$MAGIC#$randomValue##${md5Hex("318c6d4f74655d4f032fb0466bcffdbc")}##$cuid"
        return nativeHexEncode(desEncrypt(plain, SIGN_A_KEY)) to randomValue
    }

    /** 从反作弊响应中取回 sign_b（数据可能是字符串，也可能嵌在 data.data 里） */
    private fun extractSignB(payload: JSONObject): String {
        var data: Any? = payload.opt("data")
        if (data is JSONObject) data = data.opt("data")
        if (data !is String || data.isBlank()) {
            val msg = payload.optString("errstr").ifBlank { payload.optString("errMsg") }
            throw WakeUpException("WakeUp 反作弊校验失败：${msg.ifBlank { "未知错误" }}")
        }
        return data
    }

    private fun tokenFromSignB(signB: String, randomValue: String): String {
        val plain = desDecrypt(nativeHexDecode(signB), randomValue.take(5) + "#G4")
        val text = String(plain, Charsets.ISO_8859_1)
        if (text.length < 22 || !text.startsWith(randomValue)) {
            throw WakeUpException("WakeUp 反作弊校验失败")
        }
        return text.substring(12, 22)
    }

    private fun post(path: String, body: String, headers: Map<String, String>): JSONObject {
        val conn = java.net.URL(API_HOST + path).openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        return JSONObject(text)
    }

    /** 解析 WakeUp 返回的课表 JSON（每行一个 JSON） */
    private fun parseSchedule(data: String): ImportResult {
        val warnings = mutableListOf<String>()
        val courses = mutableListOf<Course>()
        var colorIndex = 0

        val lines = data.split("\n").filter { it.isNotBlank() }
        for (line in lines) {
            try {
                val obj = JSONObject(line)
                val name = obj.optString("name").ifBlank { obj.optString("courseName") }
                if (name.isBlank()) continue
                val teacher = obj.optString("teacher")
                val room = obj.optString("room").ifBlank { obj.optString("location") }
                val day = obj.optInt("day", -1).takeIf { it in 0..6 }
                    ?: (obj.optInt("dayOfWeek", 1) - 1).coerceIn(0, 6)
                // startNode 从 1 开始计（1 表示第一节）
                var start = obj.optInt("startNode", 1)
                if (start < 1) start = 1
                val step = obj.optInt("step", 1).coerceAtLeast(1)
                val weeks = obj.optInt("weeks", 0)
                val weekType = when (weeks) {
                    1 -> WeekType.ODD
                    2 -> WeekType.EVEN
                    else -> WeekType.ALL
                }

                courses.add(
                    Course(
                        name = name,
                        teacher = teacher,
                        location = room,
                        dayOfWeek = day.coerceIn(0, 6),
                        startSection = start.coerceIn(1, 20),
                        endSection = (start + step - 1).coerceIn(1, 20),
                        weekType = weekType,
                        colorArgb = courseColorArgb[colorIndex % courseColorArgb.size],
                    ),
                )
                colorIndex++
            } catch (_: Exception) {
                // 跳过无法解析的行
            }
        }
        if (courses.isEmpty()) warnings.add("口令解析后没有课程，可能是格式不兼容或口令已失效")
        return ImportResult(courses, warnings)
    }
}
