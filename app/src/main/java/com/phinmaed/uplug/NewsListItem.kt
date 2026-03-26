package com.phinmaed.uplug

sealed class NewsListItem {

    data class SectionHeader(val title: String) : NewsListItem()

    data class MonthHeader(val title: String) : NewsListItem()

    data class PostRow(val post: Post) : NewsListItem()
}