import com.google.gson.GsonBuilder
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class HttpClient(private val url: String = "https://localhost:5000") {
    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient()
    private val gson = GsonBuilder().setPrettyPrinting().create()

    @Throws(IOException::class)
    fun post(path: String, requestObject: Any): Response {
        val body = gson.toJson(requestObject).toRequestBody(JSON)
        val request = Request.Builder()
                .url(url + path)
                .post(body)
                .build()
        return client.newCall(request).execute()
    }

    @Throws(IOException::class)
    fun put(path: String, requestObject: Any): Response {
        val body = gson.toJson(requestObject).toRequestBody(JSON)
        val request = Request.Builder()
                .url(url + path)
                .post(body)
                .build()
        return client.newCall(request).execute()
    }

    @Throws(IOException::class)
    fun get(path: String): Response {
        val request = Request.Builder()
                .url(url + path)
                .get()
                .build()
        return client.newCall(request).execute()
    }
}