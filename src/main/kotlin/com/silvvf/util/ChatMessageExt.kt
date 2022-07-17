package com.silvvf.util

import com.silvvf.data.models.ChatMessage

// used to check if the chat message matches the word being guessed
fun ChatMessage.matchesWord(word: String): Boolean {
    return message.lowercase().trim() == word.lowercase().trim()
}