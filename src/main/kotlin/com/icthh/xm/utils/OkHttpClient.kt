import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

fun String.httpGet(headers: Map<String, String> = mapOf()): ByteArray? {
    val client = OkHttpClient()

    var builder = Request.Builder()
    headers.map {
        builder = builder.addHeader(it.key, it.value)
    }
    val request: Request = builder
        .url(this)
        .build()

    return client.newCall(request).execute().use {
        it.body?.bytes()
    }
}

fun String.httpGetString(headers: Map<String, String> = mapOf()): String? {
    val client = OkHttpClient()

    var builder = Request.Builder()
    headers.map {
        builder = builder.addHeader(it.key, it.value)
    }
    val request: Request = builder
        .url(this)
        .build()

    return client.newCall(request).execute().use {
        it.body?.byteString()?.utf8()
    }
}

fun String.httpGetResponse(headers: Map<String, String>): Response {
    val client = OkHttpClient()

    var builder = Request.Builder()
    headers.map {
        builder = builder.addHeader(it.key, it.value)
    }
    val request: Request = builder
        .url(this)
        .build()

    return client.newCall(request).execute()
}
