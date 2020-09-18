package com.handtruth.mc.mcsman.server.util

class ParseException(message: String, symbol: Int) : RuntimeException("$message at $symbol")

fun wordEnd(value: String, start: Int): Int {
    for (i in start until value.length) {
        when (value[i]) {
            in '0'..'9', in 'a'..'z', in 'A'..'Z', '_' -> {}
            else -> return i
        }
    }
    return value.length
}

enum class Brackets(val begin: Char, val end: Char) {
    Round('(', ')'), Curly('{', '}'), Square('[', ']')
}

fun bracketEnd(value: String, start: Int, brackets: Brackets): Int {
    if (value[start] != brackets.begin)
        return start
    var nesting = 0
    var i = start
    val end = value.length
    do {
        if (i == end)
            throw ParseException("close bracket not closed", start)
        when (value[i]) {
            brackets.begin -> nesting++
            brackets.end -> nesting--
        }
        ++i
    } while (nesting != 0)
    return i
}

fun translate(value: String, dictionary: Map<String, Any>): String {
    var i = value.indexOf('$')
    if (i == -1)
        return value
    else {
        val builder = StringBuilder(value.substring(0, i))
        while (true) {
            val j: Int
            val word: String
            if (value[i + 1] == '{') {
                j = bracketEnd(value, i + 1, Brackets.Curly)
                word = value.substring(i + 2, j - 1).trim()
            } else {
                j = wordEnd(value, i + 1)
                word = value.substring(i + 1, j)
            }
            if (word.isEmpty())
                throw ParseException("word for translation is empty", i)
            builder.append(dictionary[word].toString())
            i = value.indexOf('$', j)
            if (i == -1) {
                builder.append(value.substring(j))
                break
            }
            if (j != i)
                builder.append(value.substring(j, i))
        }
        return builder.toString()
    }
}

fun toDbWildcard(wildcard: String): String {
    wildcard.any { it == '*' || it == '?' } || return wildcard
    val chars = wildcard.toCharArray()
    for (i in chars.indices) {
        chars[i] = when (val char = chars[i]) {
            '*' -> '%'
            '?' -> '_'
            else -> char
        }
    }
    return String(chars)
}

private val hex = "0123456789abcdef"

fun checkImageId(id: String) {
    require(id.startsWith("sha256:") && id.length == 64 + 7 && (7..id.lastIndex).all { id[it] in hex }) {
        "not a docker image id: $id"
    }
}
