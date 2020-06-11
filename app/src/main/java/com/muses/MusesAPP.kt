package com.muses

import android.app.Application
import com.lxj.androidktx.AndroidKtxConfig

const val WEB_BASE_SERVER: String = "http://muses.deepicecream.com:7010"
const val WEB_SERVER_FILTER_LIST: String = "/api/filterCategory/"
const val WEB_SERVER_FILTER_DATA: String = "/api/filter/category/"
const val FILTER_BASE_SERVER: String = "http://art.deepicecream.com:7004"
const val FILTER_TRANSFER_SERVER = "/api/transfer"
const val FILTER_IMAGE_UPLOAD_SERVER = "/api/upload"
const val FILTER_AD_SERVER = "/ad/ad_pic.jpg"

class MusesAPP : Application() {

    override fun onCreate() {
        super.onCreate()
        AndroidKtxConfig.init(
            this,
            sharedPrefName = "MUSES",
            defaultLogTag = "muses_debug",
            isDebug = true
        )
    }
}