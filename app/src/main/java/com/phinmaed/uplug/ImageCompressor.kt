package com.phinmaed.uplug

import android.content.Context
import android.net.Uri
import java.io.File

object ImageCompressor {
    fun from(context: Context, uri: Uri): File {
        val input = context.contentResolver.openInputStream(uri)!!
        val file = File(context.cacheDir, "temp_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { output -> input.copyTo(output) }
        return file
    }
}