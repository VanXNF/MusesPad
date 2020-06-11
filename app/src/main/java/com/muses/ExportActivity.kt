package com.muses

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.lxj.androidktx.core.loge
import com.lxj.androidktx.core.post
import com.lxj.androidktx.core.sp
import com.lxj.androidktx.core.toast
import com.muses.util.http
import com.sunfusheng.marqueeview.MarqueeView
import com.yzq.zxinglibrary.encode.CodeCreator
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class ExportActivity : AppCompatActivity() {

    private lateinit var image: AppCompatImageView
    private lateinit var bitmap: Bitmap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_export)
        val bundle = this.intent.extras
        image = findViewById(R.id.iv_display_export)
        val path: String
        if (bundle != null) {
            path = bundle.get("Path").toString()
            if (path != "") {
                initView(path)
            }
        }
    }

    private fun initView(path: String) {
        findViewById<MarqueeView<String>>(R.id.mv_notice_export).startWithText(getString(R.string.default_notice))
        image.post {
            bitmap = BitmapFactory.decodeFile(path)
            val height = bitmap.height
            val width = bitmap.width
            Log.d("muses_size", "image_width: $width image_height: $height")
            image.setImageBitmap(bitmap)
            findViewById<AppCompatTextView>(R.id.tv_size_info_export).text = "尺寸：$width x $height"
        }

        findViewById<AppCompatTextView>(R.id.tv_toolbar_back_export).setOnClickListener {
            val intent = Intent()
            intent.setClass(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }

        findViewById<AppCompatButton>(R.id.btn_continue_editing_export).setOnClickListener {
            finish()
        }


        findViewById<AppCompatButton>(R.id.btn_share_now_export).setOnClickListener {
            val file = File(path)
            val request = (getAddress("filter") + FILTER_IMAGE_UPLOAD_SERVER).http()
                .headers(Pair("Content-Type", "multipart/form-data"))
                .params("file" to file)
                .buildPostRequest()
            val client =
                OkHttpClient().newBuilder().connectTimeout(10000, TimeUnit.MILLISECONDS)
                    .readTimeout(10000, TimeUnit.MILLISECONDS)
                    .build()
            val call: Call = client.newCall(request)
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    e.printStackTrace()
                    loge("fail to post image")
                    post { toast(resources.getString(R.string.fail_to_get_code)) }
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = response.body()!!.string()
                    val jsonObject = JSONObject(result)
                    val url = jsonObject.getString("image")
                    post {
                        val codeDialog = initCodeDialog(url)
                        codeDialog.show()
                    }
                }
            })
        }
    }

    private fun initCodeDialog(url: String): AlertDialog {
        val layoutInflater = LayoutInflater.from(this@ExportActivity)
        val dialogView: View = layoutInflater.inflate(R.layout.dialog_display, null)
        val builder =
            AlertDialog.Builder(this@ExportActivity)
        builder.setView(dialogView)
        val codeView: AppCompatImageView = dialogView.findViewById(R.id.iv_dialog_display)
        val bitmap = CodeCreator.createQRCode(url, 400, 400, null)
        codeView.setImageBitmap(bitmap)
        builder.setCancelable(true)
        val codeDialog = builder.create()
        val window: Window = codeDialog.window!!
        window.setBackgroundDrawable(resources.getDrawable(R.drawable.rectangle_all_bg, null))
        window.setGravity(Gravity.CENTER)
        return codeDialog
    }

    private fun getAddress(key: String): String {
        var v = sp(name = "address").getString(key, "null").toString()
        if (v == "null") {
            when (key) {
                "web" -> v = WEB_BASE_SERVER
                "filter" -> v = FILTER_BASE_SERVER
            }
        }
        return v
    }
}
