package com.phinmaed.uplug

import com.google.firebase.Timestamp

data class Message(
    val text: String = "",
    val fromUid: String = "",
    val toUid: String = "",
    val createdAt: com.google.firebase.Timestamp? = null,
    val isGroupedWithPrevious: Boolean = false,
    val isGroupedWithNext: Boolean = false,
    val showExactTime: Boolean = false,
    val isSelected: Boolean = false
)