package com.phinmaed.uplug

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

object CloudinaryUploader {


    private const val CLOUD_NAME = "ddly5asw1"
    private const val UPLOAD_PRESET = "android_unsigned_upload"

    fun uploadImage(
        imageBytes: ByteArray,
        uid: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                "image.jpg",
                imageBytes.toRequestBody("image/*".toMediaTypeOrNull())
            )
            .addFormDataPart("upload_preset", UPLOAD_PRESET)
            .addFormDataPart("folder", "profile_pictures/$uid")
            .build()

        val request = Request.Builder()
            .url("https://api.cloudinary.com/v1_1/$CLOUD_NAME/image/upload")
            .post(requestBody)
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError(e.message ?: "Upload failed")
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    onError("Upload failed")
                    return
                }

                val json = JSONObject(response.body!!.string())
                onSuccess(json.getString("secure_url"))
            }
        })
    }

}

