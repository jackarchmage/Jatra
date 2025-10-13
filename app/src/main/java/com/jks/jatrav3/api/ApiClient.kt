package com.jks.jatrav3.api

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val BASE_URL = "https://elysianarco.com/"

    /**
     * A safe logging interceptor that logs headers and only the first `maxBodyBytes` bytes of the body.
     * It uses response.peekBody(...) which limits the amount buffered and does not consume the real stream.
     */
    class TruncatingLoggingInterceptor(private val maxBodyBytes: Long = 1024) : Interceptor {
        private val delegate = HttpLoggingInterceptor().apply {
            // We will not use Level.BODY because that reads the whole body into memory.
            // Use BASIC for minimal info or HEADERS for headers; body preview is done via peek.
            level = HttpLoggingInterceptor.Level.HEADERS
        }

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()

            // Use the delegate to log request line + headers
            val logged = delegate.intercept(chain) // this will proceed as well, so we must be careful

            // The delegate already proceeded the chain, so we should return its response.
            // But we want to safely peek a limited amount of the body and log it.
            try {
                val peek = logged.peekBody(maxBodyBytes)
                val preview = peek.string()
                android.util.Log.d("HTTP", "← body preview (first $maxBodyBytes bytes): $preview")
            } catch (e: Exception) {
                android.util.Log.d("HTTP", "← body preview: <error>")
            }

            return logged
        }
    }

    // General "safe" client used for normal API calls (small responses)
    private val safeClient: OkHttpClient by lazy {
        val trunc = TruncatingLoggingInterceptor(1024) // peek first 1KB
        OkHttpClient.Builder()
            .addInterceptor(trunc)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // Dedicated client for uploads: no logging of bodies, longer timeouts.
    // Important: do NOT add a logging interceptor that reads body here.
    private val uploadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // DO NOT add HttpLoggingInterceptor.Level.BODY or any interceptor that reads the whole body
            .connectTimeout(180, TimeUnit.SECONDS) // upload may need longer
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(180, TimeUnit.SECONDS)
            .build()
    }

    // Retrofit instance for regular API calls
    private val retrofitSafe: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(safeClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // Retrofit instance for uploads (uses uploadClient)
    private val retrofitUpload: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(uploadClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // Public API objects
    val jatraApi: JatraApi by lazy { retrofitSafe.create(JatraApi::class.java) }

    /**
     * Use this for large file upload calls (e.g. uploadArFile).
     * This client has no body-logging and longer timeouts to avoid OOM and premature timeouts.
     */
    val jatraUploadApi: JatraApi by lazy { retrofitUpload.create(JatraApi::class.java) }

    // Keep designService (wired to safe client)
    val designService: JatraApi by lazy { retrofitSafe.create(JatraApi::class.java) }
}
