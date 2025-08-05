package com.example.wlist.util

import java.text.Normalizer
import java.util.Locale

private val REGEX_UNACCENT = "\\p{InCombiningDiacriticalMarks}+".toRegex()

fun normalize(input: String): String {
    val temp = Normalizer.normalize(input, Normalizer.Form.NFD)
    return REGEX_UNACCENT.replace(temp, "").lowercase(Locale.getDefault())
}