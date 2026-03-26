package com.phinmaed.uplug

data class MessagePreview(
    val uid: String,
    val lastMessage: String,
    val name: String = "",
    val photoUrl: String = ""
)