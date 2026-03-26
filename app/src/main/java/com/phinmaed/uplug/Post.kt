package com.phinmaed.uplug

import com.google.firebase.Timestamp

data class Post(
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val authorName: String = "",
    val authorEmail: String = "",
    val authorUid: String = "",
    val wall: String = "",
    val align: String = "left",
    val createdAt: Timestamp? = null,
    val pinned: Boolean = false,
    val pinnedAt: Timestamp? = null,
    var photoUrl: String = ""
)