package com.handtruth.mc.mcsman.server.bundle

import kotlin.math.min

class Version(
    val prefix: String? = null,
    val numbers: IntArray = IntArray(0),
    val suffix: String = ""
) : Comparable<Version> {
    override fun compareTo(other: Version): Int {
        val size = min(other.numbers.size, numbers.size)
        for (i in 0 until size) {
            val cmp = numbers[i].compareTo(other.numbers[i])
            if (cmp != 0)
                return cmp
        }
        val cmp = numbers.size.compareTo(other.numbers.size)
        if (cmp != 0)
            return cmp
        return suffix.compareTo(other.suffix)
    }

    override fun equals(other: Any?): Boolean {
        return other is Version && prefix == other.prefix && numbers.contentEquals(other.numbers)
                && suffix == other.suffix
    }

    override fun hashCode(): Int {
        var result = prefix?.hashCode() ?: 0
        result = 31 * result + numbers.contentHashCode()
        result = 31 * result + suffix.hashCode()
        return result
    }

    override fun toString() = buildString {
        prefix?.let { append(it) }
        numbers.joinTo(this, separator = ".")
        append(suffix)
    }

    companion object {
        fun parse(string: String): Version {
            val prefixEnd = string.indexOfFirst { it.isDigit() }
            if (prefixEnd == -1)
                return Version(suffix = string)
            val prefix = if (prefixEnd == 0) null else string.substring(0, prefixEnd)
            val numbers = mutableListOf<Int>()
            var cursor = prefixEnd - 1
            do {
                ++cursor
                var shouldContinue = false
                loop@ for (n in cursor until string.length) {
                    shouldContinue = when (string[n]) {
                        in '0'..'9' -> continue@loop
                        '.' -> true
                        else -> false
                    }
                    numbers += string.substring(cursor, n).toInt()
                    cursor = n
                    break@loop
                }
            } while (shouldContinue)
            val suffix = string.substring(cursor)
            return Version(prefix, numbers.toIntArray(), suffix)
        }
    }
}
