package com.tianma.xsmscode.data.http.service

import com.tianma.xsmscode.common.serialization.JsonConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.ConcurrentHashMap

class ServiceGenerator private constructor() {

    private val mRetrofitMap: MutableMap<String, Retrofit> = ConcurrentHashMap()
    private val mOkHttpClient: OkHttpClient = OkHttpClient.Builder().build()

    companion object {
        private val instanceDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            ServiceGenerator()
        }

        @JvmStatic
        fun getInstance(): ServiceGenerator = instanceDelegate.value
    }

    fun <T> createService(baseUrl: String, serviceClass: Class<T>): T {
        var retrofit = mRetrofitMap[baseUrl]
        if (retrofit == null) {
            retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(mOkHttpClient)
                .addConverterFactory(ScalarsConverterFactory.create())
                .addConverterFactory(JsonConfig.json.asConverterFactory("application/json".toMediaType()))
                .build()
            mRetrofitMap[baseUrl] = retrofit
        }
        return retrofit!!.create(serviceClass)
    }
}
