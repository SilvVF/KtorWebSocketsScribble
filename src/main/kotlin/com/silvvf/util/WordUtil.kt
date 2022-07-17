package com.silvvf.util

import java.io.File

val words = readWordList("src/main/resources/word_list.txt")

fun readWordList(fileName: String): List<String> {
    val inputStream = File(fileName).inputStream()
    val words = mutableListOf<String>()
    inputStream.bufferedReader().forEachLine {words.add(it) }
    return words
}

fun getRandomWords(amount: Int): List<String> {
    val res = mutableListOf<String>()
    repeat(amount) {
        var word = words.random()
        while (word in res) { word = words.random() }
        res.add(word)
    }
    return res
}

//need to pass a space to space single letters and double-spacing for spaces
fun String.transformToUnderscores() = this.map { if (it != ' ') '_' else it }.joinToString( " ")