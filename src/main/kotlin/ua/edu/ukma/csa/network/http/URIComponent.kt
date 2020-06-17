package ua.edu.ukma.csa.network.http

import java.io.UnsupportedEncodingException


const val ALLOWED_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.!~*'()"

fun encodeURIComponent(input: String): String {
    if (input.isEmpty()) {
        return input
    }
    val o = StringBuilder(input.length * 3)
    try {
        for (i in input.indices) {
            val e = input.substring(i, i + 1)
            if (ALLOWED_CHARS.indexOf(e) == -1) {
                val b = e.toByteArray(charset("utf-8"))
                o.append(getHex(b))
                continue
            }
            o.append(e)
        }
        return o.toString()
    } catch (e: UnsupportedEncodingException) {
        e.printStackTrace()
    }
    return input
}

private fun getHex(buf: ByteArray): String {
    val o = StringBuilder(buf.size * 3)
    for (i in buf.indices) {
        val n = buf[i].toInt() and 0xff
        o.append("%")
        if (n < 0x10) {
            o.append("0")
        }
        o.append(n.toLong().toString(16).toUpperCase())
    }
    return o.toString()
}

fun decodeURIComponent(encodedURI: String): String {
    var actualChar: Char
    val buffer = StringBuffer()
    var bytePattern: Int
    var sumb = 0
    var i = 0
    var more = -1
    while (i < encodedURI.length) {
        actualChar = encodedURI[i]
        when (actualChar) {
            '%' -> {
                actualChar = encodedURI[++i]
                val hb =
                    (if (Character.isDigit(actualChar)) actualChar - '0' else 10 + Character.toLowerCase(
                        actualChar
                    ).toInt() - 'a'.toInt()) and 0xF
                actualChar = encodedURI[++i]
                val lb =
                    (if (Character.isDigit(actualChar)) actualChar - '0' else 10 + Character.toLowerCase(
                        actualChar
                    ).toInt() - 'a'.toInt()) and 0xF
                bytePattern = hb shl 4 or lb
            }
            '+' -> {
                bytePattern = ' '.toInt()
            }
            else -> {
                bytePattern = actualChar.toInt()
            }
        }
        if (bytePattern and 0xc0 == 0x80) { // 10xxxxxx
            sumb = sumb shl 6 or (bytePattern and 0x3f)
            if (--more == 0) buffer.append(sumb.toChar())
        } else if (bytePattern and 0x80 == 0x00) { // 0xxxxxxx
            buffer.append(bytePattern.toChar())
        } else if (bytePattern and 0xe0 == 0xc0) { // 110xxxxx
            sumb = bytePattern and 0x1f
            more = 1
        } else if (bytePattern and 0xf0 == 0xe0) { // 1110xxxx
            sumb = bytePattern and 0x0f
            more = 2
        } else if (bytePattern and 0xf8 == 0xf0) { // 11110xxx
            sumb = bytePattern and 0x07
            more = 3
        } else if (bytePattern and 0xfc == 0xf8) { // 111110xx
            sumb = bytePattern and 0x03
            more = 4
        } else { // 1111110x
            sumb = bytePattern and 0x01
            more = 5
        }
        i++
    }
    return buffer.toString()
}
