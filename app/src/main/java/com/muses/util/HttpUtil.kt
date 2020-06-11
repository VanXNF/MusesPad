package com.muses.util

import android.graphics.BitmapFactory
import com.lxj.androidktx.core.toBean
import com.lxj.androidktx.okhttp.HttpCallback
import com.lxj.androidktx.okhttp.OkWrapper
import com.lxj.androidktx.okhttp.RequestWrapper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import okhttp3.*
import java.io.File
import java.io.IOException
import java.net.URLConnection.getFileNameMap


/**
 * http扩展，使用起来像这样：
 * 协程中使用：    "http://www.baidu.com".http().get<Bean>().await()
 * 非协程中使用：  "http://www.baidu.com".http().get<Bean>(callback)
 * Create by lxj, at 2018/12/19
 * Edit by VictorXu, at 2020/1/6
 * @param tag 请求的tag，tag和baseUrl一一对应，可以实现多个baseUrl
 */
fun String.http(tag: Any = this, baseUrlTag: String = OkWrapper.DefaultUrlTag): RequestWrapper {
    val baseUrls = OkWrapper.baseUrlMap[baseUrlTag]
    return RequestWrapper(
        tag,
        url = if (baseUrls != null) "${OkWrapper.baseUrlMap[baseUrlTag]}${this}" else this
    )
}

/**
 * get请求，需在协程中使用。结果为空即为http请求失败，并会将失败信息打印日志。
 */
inline fun <reified T> RequestWrapper.get(): Deferred<T?> {
    return defferedRequest(buildGetRequest(), this)
}

/**
 * callback style，不在协程中使用
 */
inline fun <reified T> RequestWrapper.get(cb: HttpCallback<T>) {
    callbackRequest(buildGetRequest(), cb, this)
}

/**
 * post请求，需在协程中使用。结果为空即为http请求失败，并会将失败信息打印日志。
 */
inline fun <reified T> RequestWrapper.post(): Deferred<T?> {
    return defferedRequest(buildPostRequest(), this)
}

inline fun <reified T> RequestWrapper.postJson(json: String): Deferred<T?> {
    return defferedRequest(
        buildPostRequest(buildJsonBody(json)),
        this
    )
}

/**
 * callback style，不在协程中使用
 */
inline fun <reified T> RequestWrapper.post(cb: HttpCallback<T>) {
    callbackRequest(buildPostRequest(), cb, this)
}

inline fun <reified T> RequestWrapper.postJson(json: String, cb: HttpCallback<T>) {
    callbackRequest(
        buildPostRequest(buildJsonBody(json)),
        cb,
        this
    )
}

/**
 * put请求，需在协程中使用。结果为空即为http请求失败，并会将失败信息打印日志。
 */
inline fun <reified T> RequestWrapper.put(): Deferred<T?> {
    return defferedRequest(buildPutRequest(), this)
}

inline fun <reified T> RequestWrapper.putJson(json: String): Deferred<T?> {
    return defferedRequest(
        buildPutRequest(buildJsonBody(json)),
        this
    )
}

/**
 * callback style，不在协程中使用
 */
inline fun <reified T> RequestWrapper.put(cb: HttpCallback<T>) {
    callbackRequest(buildPutRequest(), cb, this)
}

inline fun <reified T> RequestWrapper.putJson(json: String, cb: HttpCallback<T>) {
    callbackRequest(
        buildPutRequest(buildJsonBody(json)),
        cb,
        this
    )
}

/**
 * delete请求，需在协程中使用。结果为空即为http请求失败，并会将失败信息打印日志。
 */
inline fun <reified T> RequestWrapper.delete(): Deferred<T?> {
    return defferedRequest(buildDeleteRequest(), this)
}

/**
 * callback style，不在协程中使用
 */
inline fun <reified T> RequestWrapper.delete(cb: HttpCallback<T>) {
    callbackRequest(buildDeleteRequest(), cb, this)
}


inline fun <reified T> defferedRequest(request: Request, reqWrapper: RequestWrapper): Deferred<T?> {
    val req = request.newBuilder().tag(reqWrapper.tag())
        .build()
    val call = OkWrapper.okHttpClient.newCall(req)
        .apply { OkWrapper.requestCache[reqWrapper.tag()] = this } //cache req
    val deferred = CompletableDeferred<T?>()
    deferred.invokeOnCompletion {
        if (deferred.isCancelled) {
            OkWrapper.requestCache.remove(reqWrapper.tag())
            call.cancel()
        }
    }
    try {
        val response = call.execute()
        if (response.isSuccessful && response.body() != null) {
            when {
                T::class.java == String::class.java -> deferred.complete(response.body()!!.string() as T)
                T::class.java == File::class.java -> {
                    val file = File(reqWrapper.savePath)
                    if (!file.exists()) file.createNewFile()
                    response.body()!!.byteStream().copyTo(file.outputStream())
                    deferred.complete(file as T)
                }
                else -> deferred.complete(response.body()!!.string().toBean<T>())
            }
        } else {
            deferred.complete(null) //not throw, pass null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        deferred.complete(null) //pass null
    } finally {
        OkWrapper.requestCache.remove(reqWrapper.tag())
    }
    return deferred
}

inline fun <reified T> callbackRequest(
    request: Request,
    cb: HttpCallback<T>,
    reqWrapper: RequestWrapper
) {
    val req = request.newBuilder().tag(reqWrapper.tag()).build()
    OkWrapper.okHttpClient.newCall(req).apply {
        OkWrapper.requestCache[reqWrapper.tag()] = this //cache req
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                OkWrapper.requestCache.remove(reqWrapper.tag())
                cb.onFail(e)
            }

            override fun onResponse(call: Call, response: Response) {
                OkWrapper.requestCache.remove(reqWrapper.tag())
                if (response.isSuccessful && response.body() != null) {
                    when {
                        T::class.java == String::class.java -> cb.onSuccess(response.body()!!.string() as T)
                        T::class.java == File::class.java -> {
                            val file = File(reqWrapper.savePath)
                            if (!file.exists()) file.createNewFile()
                            response.body()!!.byteStream().copyTo(file.outputStream())
                            cb.onSuccess(file as T)
                        }
                        else -> cb.onSuccess(response.body()!!.string().toBean<T>())
                    }
                } else {
                    cb.onFail(IOException("request to ${request.url()} is fail; http code: ${response.code()}!"))
                }
            }
        })
    }
}

// parse some new media type.
fun File.mediaType(): String {
    return getFileNameMap().getContentTypeFor(name) ?: when (extension.toLowerCase()) {
        "json" -> "application/json"
        "js" -> "application/javascript"
        "apk" -> "application/vnd.android.package-archive"
        "md" -> "text/x-markdown"
        "webp" -> "image/webp"
        "jpg" -> "image/jpg"
        "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        else -> "application/octet-stream"
    }
}
