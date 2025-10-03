package com.jks.jatrav3.api
// MultipartUtils.kt
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/** Get filename from Uri (content resolver) */
fun getFileNameFromUri(context: Context, uri: Uri): String {
    var name = "file"
    val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (it.moveToFirst() && nameIndex >= 0) {
            name = it.getString(nameIndex)
        }
    }
    return name
}

/** Get mime type from Uri */
fun getMimeType(context: Context, uri: Uri): String {
    return context.contentResolver.getType(uri) ?: "application/octet-stream"
}

/**
 * Copies the content from the Uri into a temp file and returns a MultipartBody.Part
 * partName: the name of the part expected by backend, e.g. "giftDeed" or "documents[giftDeed]"
 */
@Throws(Exception::class)
fun uriToMultipartPart(context: Context, partName: String, uri: Uri): MultipartBody.Part {
    val fileName = getFileNameFromUri(context, uri)
    val mime = getMimeType(context, uri)
    // create temp file (cache)
    val input = context.contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("Cannot open URI")
    val temp = File.createTempFile("upload_", fileName, context.cacheDir)
    temp.outputStream().use { out -> input.copyTo(out) }

    val mediaType = mime.toMediaTypeOrNull()
    val reqFile = temp.asRequestBody(mediaType)
    // createFormData(name, filename, body)
    val part = MultipartBody.Part.createFormData(partName, fileName, reqFile)
    return part
}

/** Create RequestBody for plain text parts */
fun String.toPlainRequestBody(): RequestBody = this.toRequestBody("text/plain".toMediaTypeOrNull())
