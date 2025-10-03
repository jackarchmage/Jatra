package com.jks.jatrav3.api

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

// FileUtil.kt
fun uriToFile(context: Context, uri: Uri, fileName: String = "upload_image_tmp.jpg"): File {
    val inputStream = context.contentResolver.openInputStream(uri)
    val tempFile = File(context.cacheDir, fileName)
    inputStream.use { input ->
        FileOutputStream(tempFile).use { output ->
            input?.copyTo(output)
            output.flush()
        }
    }
    return tempFile
}
