package com.phinmaed.uplug

data class ChatItem(
    val type: Int,
    val message: Message? = null,
    val dateText: String? = null
)