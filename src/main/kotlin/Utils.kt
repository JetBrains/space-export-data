package org.jetbrains

import okhttp3.OkHttpClient
import okhttp3.Request
import space.jetbrains.api.runtime.Batch
import space.jetbrains.api.runtime.BatchInfo
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

suspend fun <T> loadBatch(provider: suspend (BatchInfo) -> Batch<T>) = buildList {
    var offset = ""
    var hasNext = true

    while (hasNext) {
        val response = provider(BatchInfo(offset, 50))
        addAll(response.data)
        offset = response.next
        hasNext = (response.totalCount ?: 0) > size
    }
}

fun downloadFile(url: String, file: File, builder: Request.Builder.() -> Unit = {}) {
    val client = OkHttpClient()
    val request = Request.Builder().url(url).apply(builder).build()
    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IOException("Failed to download file: $response")

        response.body?.byteStream()?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
    }
}