package com.jdcr.jdcrble.util

object JdcrBleHexUtils {

    private val HEX_DIGITS = "0123456789ABCDEF".toCharArray()

    /**
     * 将 hex 字符串转为二进制 byte[]。
     * 支持: "FF FB 05"、"0xFFFB05"、"FF:FB:05"、"fffb05"
     */
    fun String.hexToBytes(): ByteArray {
        val hex = normalizeHex(this)
        require(hex.isNotEmpty()) { "Hex string must not be empty" }
        require(hex.length % 2 == 0) { "Hex string must have even length after normalization: '$this'" }
        return ByteArray(hex.length / 2) { i ->
            val index = i * 2
            ((hex.hexNibbleAt(index) shl 4) or hex.hexNibbleAt(index + 1)).toByte()
        }
    }

    /** byte[] → hex，默认大写、无分隔符，如 "FFFB05" */
    fun ByteArray.toHexString(
        separator: String = "",
        lowerCase: Boolean = false,
    ): String {
        if (isEmpty()) return ""
        val table = if (lowerCase) {
            "0123456789abcdef".toCharArray()
        } else {
            HEX_DIGITS
        }
        val sepLen = separator.length
        val out = CharArray(size * 2 + (size - 1).coerceAtLeast(0) * sepLen)
        var pos = 0
        for (i in indices) {
            if (i > 0 && sepLen > 0) {
                separator.toCharArray().copyInto(out, pos)
                pos += sepLen
            }
            val value = this[i].toInt() and 0xFF
            out[pos++] = table[value ushr 4]
            out[pos++] = table[value and 0x0F]
        }
        return String(out)
    }

    /** Java/Kotlin 有符号 byte → 0~255，读 notify 时用 */
    fun Byte.toUnsignedInt(): Int = toInt() and 0xFF
    fun ByteArray.toUnsignedIntList(): List<Int> = map { it.toUnsignedInt() }

    /** JS 传来的 [-1, -5, 5] → 发蓝牙用的 byte[] */
    fun Iterable<Int>.signedIntsToBytes(): ByteArray =
        map { it.toByte() }.toByteArray()

    private fun normalizeHex(source: String): String {
        val builder = StringBuilder(source.length)
        var index = 0
        while (index < source.length) {
            when (val ch = source[index]) {
                ' ', '\t', '\n', '\r', '-', ':' -> Unit
                '0' -> {
                    if (index + 1 < source.length &&
                        (source[index + 1] == 'x' || source[index + 1] == 'X')
                    ) {
                        index += 2
                        continue
                    }
                    builder.append(ch)
                }

                else -> builder.append(ch)
            }
            index++
        }
        return builder.toString()
    }

    private fun CharSequence.hexNibbleAt(index: Int): Int =
        when (val digit = Character.digit(this[index], 16)) {
            -1 -> throw IllegalArgumentException(
                "Invalid hex character '${this[index]}' at index $index in '$this'"
            )

            else -> digit
        }

}