package com.phinmaed.uplug

data class User(
    var uid: String = "",
    var firstName: String = "",
    var lastName: String = "",
    var photo: String = "",

    var username: String? = null,
    var email: String? = null,
    var department: String? = null,
    var bio: String? = null,
    var onboardingComplete: Boolean? = null
) {

    val name: String
        get() = "$firstName $lastName"

    val profilePic: String
        get() = photo
}