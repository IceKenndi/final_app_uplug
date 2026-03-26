package com.phinmaed.uplug

import android.app.Application
import com.cloudinary.android.MediaManager

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        val config = mapOf(
            "cloud_name" to "ddly5asw1",
            "api_key" to "847734539465316",
            "api_secret" to "4YJRocxue3aAK3i0r24-SQxFgyw"
        )

        MediaManager.init(this, config)
    }
}