package com.phinmaed.uplug

import com.google.firebase.firestore.DocumentSnapshot

object HomeFeedCache {
    val posts = mutableListOf<Post>()
    var lastVisible: DocumentSnapshot? = null
    var isLastPage: Boolean = false
    var departmentWall: String = ""
    var hasLoadedOnce: Boolean = false
}