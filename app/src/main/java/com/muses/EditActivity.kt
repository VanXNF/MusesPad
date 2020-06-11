package com.muses

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.Gson
import com.lxj.androidktx.core.*
import com.lxj.androidktx.okhttp.HttpCallback
import com.mmga.metroloading.MetroLoadingView
import com.muses.entity.FilterClassData
import com.muses.entity.FilterClassEntity
import com.muses.entity.FilterData
import com.muses.entity.FilterPageEntity
import com.muses.util.bindData
import com.muses.util.get
import com.muses.util.horizontal
import com.muses.util.http
import com.muses.util.itemClick
import com.muses.util.updateData
import com.sunfusheng.marqueeview.MarqueeView
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class EditActivity : AppCompatActivity() {

    private val image by lazy<AppCompatImageView> {
        findViewById(R.id.iv_display_edit)
    }
    private val recyclerData by lazy<RecyclerView> {
        findViewById(R.id.rlv_filter_data_edit)
    }
    private val recyclerClass by lazy<RecyclerView> {
        findViewById(R.id.rlv_filter_class_edit)
    }
    private val flabBack by lazy<FloatingActionButton> {
        findViewById(R.id.flab_back_edit)
    }
    private val filterSeekBar by lazy<SeekBar> {
        findViewById(R.id.sb_filter_edit)
    }
    private val llAdjustContainer by lazy<LinearLayout> {
        findViewById(R.id.ll_adjust_container_edit)
    }
    private lateinit var bitmapO: Bitmap
    private lateinit var bitmapF: Bitmap
    private lateinit var bitmapE: Bitmap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)
        val bundle = this.intent.extras

        val path: String
        if (bundle != null) {
            path = bundle.get("Path").toString()
            if (path != "") {
                initView(path)
            }
        }

    }

    @SuppressLint("RestrictedApi")
    private fun initView(path: String) {

        findViewById<MarqueeView<String>>(R.id.mv_notice_edit).startWithText(getString(R.string.default_notice))
//        val mWindowManager = this.getSystemService(Context.WINDOW_SERVICE) as WindowManager
//        val metrics = DisplayMetrics()
//        mWindowManager.defaultDisplay.getMetrics(metrics)
//        val wWidth = metrics.widthPixels
//        val wHeight = metrics.heightPixels

//        val imageSize = if (wWidth >= wHeight) wHeight else wWidth
//        val params = LinearLayout.LayoutParams(
//            imageSize,
//            imageSize
//        )

//        val parent = image.parent as ViewGroup
//        parent.removeView(image)
//        image.layoutParams = params
//        parent.addView(image, 0)

        val loadingDialog = initLoadingDialog()

        image.post {
            bitmapO = BitmapFactory.decodeFile(path)
            val height = bitmapO.height
            val width = bitmapO.width
            logd("muses_size", "image_width: $width image_height: $height")
            bitmapE = bitmapO
            image.setImageBitmap(bitmapO)
        }

        findViewById<AppCompatTextView>(R.id.tv_toolbar_back_edit).setOnClickListener {
            finish()
        }

        findViewById<AppCompatTextView>(R.id.tv_toolbar_finish_edit).setOnClickListener {
            val file = File(
                externalMediaDirs.first(),
                "muses.jpg"
            )
            try {
                val out = FileOutputStream(file)
                bitmapE.compress(Bitmap.CompressFormat.JPEG, 100, out)
                out.flush()
                out.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            val intent = Intent()
            intent.setClass(this, ExportActivity::class.java)
            val bundle = Bundle()
            bundle.putString("Path", file.absolutePath)
            intent.putExtras(bundle)
            startActivity(intent)
        }

        flabBack.setOnClickListener {
            post {
                recyclerClass.visibility = View.VISIBLE
                recyclerData.visibility = View.GONE
                flabBack.visibility = View.GONE
                llAdjustContainer.visibility = View.GONE
            }
        }



        recyclerClass
            .horizontal(0)
            .bindData(
                emptyList<FilterClassData>(),
                R.layout.item_filter
            ) { holder, item, position ->
                holder.setText(R.id.tv_filter_name, item.categoryName)
                val imageView = holder.getView<AppCompatImageView>(R.id.iv_filter_image)
                Glide.with(this@EditActivity)
                    .load(item.imageUrl)
                    .into(imageView)
            }
            .itemClick<FilterClassData> { data, holder, position ->
                ((getAddress("web") + WEB_SERVER_FILTER_DATA) + "/"+ (data[position]).id+ "/1").http()
                    .get(object : HttpCallback<String> {
                        @SuppressLint("RestrictedApi")
                        override fun onSuccess(t: String) {
                            val result = Gson()
                                .fromJson<FilterPageEntity>(t, FilterPageEntity::class.java)
                                .dataList
                            post {
                                recyclerClass.visibility = View.GONE
                                recyclerData.visibility = View.VISIBLE
                                flabBack.visibility = View.VISIBLE
                                recyclerData.updateData(result)
                            }
                        }
                    })
            }
        //具体滤镜列表
        recyclerData
            .horizontal(0)
            .bindData(emptyList<FilterData>(), R.layout.item_filter) { holder, item, position ->
                holder.setText(R.id.tv_filter_name, item.filterName)
                val imageView = holder.getView<AppCompatImageView>(R.id.iv_filter_image)
                Glide.with(this@EditActivity)
                    .load(item.coverImage)
                    .into(imageView)
            }.itemClick<FilterData> { data, holder, position ->
                post { loadingDialog.show() }
                val file = File(
                    externalMediaDirs.first(),
                    "muses.jpg"
                )
                val request = (getAddress("filter") + FILTER_TRANSFER_SERVER).http()
                    .headers(Pair("Content-Type", "multipart/form-data"))
                    .params(
                        "upload_id" to data[position].uploadId,
                        "file" to file
                    )
                    .buildPostRequest()
                val client =
                    OkHttpClient().newBuilder().connectTimeout(10000, TimeUnit.MILLISECONDS)
                        .readTimeout(10000, TimeUnit.MILLISECONDS)
                        .build()
                val call: Call = client.newCall(request)
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        loge("fail to get filter image")
                        post {
                            toast("网络异常，请稍后再试")
                            loadingDialog.dismiss()
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val result = response.body()!!.string()
                        loge(result)
                        val jsonObject = JSONObject(result)
                        val url = jsonObject.getString("image")
                        Glide.with(this@EditActivity)
                            .asBitmap()
                            .load(url)
                            .listener(object : RequestListener<Bitmap> {
                                override fun onResourceReady(
                                    resource: Bitmap?,
                                    model: Any?,
                                    target: Target<Bitmap>?,
                                    dataSource: DataSource?,
                                    isFirstResource: Boolean
                                ): Boolean {
                                    bitmapF = resource!!
                                    post {
                                        llAdjustContainer.visibility = View.VISIBLE
                                        bitmapE = tweakStyleIntensity(filterSeekBar.progress)
                                        loadingDialog.dismiss()
                                        image.setImageBitmap(bitmapE)
                                    }
                                    return true
                                }

                                override fun onLoadFailed(
                                    e: GlideException?,
                                    model: Any?,
                                    target: Target<Bitmap>?,
                                    isFirstResource: Boolean
                                ): Boolean {
                                    post {
                                        toast("获取图片失败")
                                        loadingDialog.dismiss()
                                    }
                                    return true
                                }
                            })
                            .submit(bitmapO.width, bitmapO.height)
                    }
                })
            }

        //请求滤镜类别列表
        (getAddress("web") + WEB_SERVER_FILTER_LIST).http().get(object : HttpCallback<String> {
            override fun onSuccess(t: String) {
                logd("get filter class list success")
                val result =
                    Gson().fromJson<FilterClassEntity>(t, FilterClassEntity::class.java).data
                post {
                    recyclerClass.visibility = View.VISIBLE
                    recyclerData.visibility = View.GONE
                    llAdjustContainer.visibility = View.GONE
                    recyclerClass.updateData(result)
                }
            }
        })

        filterSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                post {
                    findViewById<AppCompatTextView>(R.id.tv_filter_intensity_edit).text =
                        progress.toString()
                    bitmapE = tweakStyleIntensity(progress)
                    image.setImageBitmap(bitmapE)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        })

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

    private fun tweakStyleIntensity(number: Int): Bitmap {
        val width = bitmapO.width
        val height = bitmapO.height
        val newBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(newBitmap)
        canvas.drawBitmap(bitmapO, 0f, 0f, null)
        val paint = Paint()
        paint.style = Paint.Style.FILL_AND_STROKE
        paint.alpha = number
        canvas.drawBitmap(bitmapF, 0f, 0f, paint)
        canvas.save()
        canvas.restore()
        return newBitmap
    }

    private fun initLoadingDialog(): AlertDialog {
        val layoutInflater = LayoutInflater.from(this@EditActivity)
        val dialogView: View = layoutInflater.inflate(R.layout.dialog_loading, null)
        val builder =
            AlertDialog.Builder(this@EditActivity)
        builder.setView(dialogView)
        val txtTitle: AppCompatTextView = dialogView.findViewById(R.id.dialog_title)
        val loadingView: MetroLoadingView = dialogView.findViewById(R.id.dialog_loading)
        builder.setCancelable(false)
        txtTitle.visibility = View.VISIBLE
        txtTitle.setText(R.string.please_wait_we_are_create_image_now)
        val loadingDialog = builder.create()
        val window: Window = loadingDialog.window!!
        window.setBackgroundDrawable(resources.getDrawable(R.drawable.rectangle_all_bg, null))
        window.setGravity(Gravity.CENTER)
        loadingDialog.setOnShowListener { loadingView.start() }
        loadingDialog.setOnDismissListener { loadingView.stop() }
        return loadingDialog
    }

}

