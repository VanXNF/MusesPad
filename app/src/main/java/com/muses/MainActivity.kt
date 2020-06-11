package com.muses

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.camera.core.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.lxj.androidktx.core.clear
import com.lxj.androidktx.core.edit
import com.lxj.androidktx.core.sp
import com.sunfusheng.marqueeview.MarqueeView
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val REQUEST_CODE_PERMISSIONS = 10

private val REQUIRED_PERMISSIONS = arrayOf(
    Manifest.permission.CAMERA,
    Manifest.permission.INTERNET,
    Manifest.permission.READ_EXTERNAL_STORAGE,
    Manifest.permission.WRITE_EXTERNAL_STORAGE
)

class MainActivity : AppCompatActivity() {

    private lateinit var mTtvPreview: TextureView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mTtvPreview = findViewById(R.id.ttv_preview_camera)

        if (allPermissionsGranted()) {
            mTtvPreview.post {
                startCamera()
                initView()
            }

        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        findViewById<MarqueeView<String>>(R.id.mv_notice_camera).startWithText(getString(R.string.default_notice))
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                mTtvPreview.post {
                    startCamera()
                    initView()
                }
            } else {
                Toast.makeText(
                    this,
                    R.string.permission_denied,
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun initView() {
        findViewById<AppCompatImageView>(R.id.iv_setting_camera).setOnClickListener {
            val dialog = initCodeDialog()
            dialog.show()
        }
    }

    private fun initCodeDialog(): AlertDialog {
        val layoutInflater = LayoutInflater.from(this@MainActivity)
        val dialogView: View = layoutInflater.inflate(R.layout.dialog_address, null)
        val builder =
            AlertDialog.Builder(this@MainActivity)
        builder.setView(dialogView)
        val webAddress: AppCompatEditText = dialogView.findViewById(R.id.et_web_address_dialog)
        val filterAddress: AppCompatEditText =
            dialogView.findViewById(R.id.et_filter_address_dialog)
        val btnRestore: AppCompatButton = dialogView.findViewById(R.id.btn_restore_dialog)
        val btnConfirm: AppCompatButton = dialogView.findViewById(R.id.btn_confirm_dialog)
        val btnCancel: AppCompatButton = dialogView.findViewById(R.id.btn_cancel_dialog)
        builder.setCancelable(true)
        val codeDialog = builder.create()
        val window: Window = codeDialog.window!!
        window.setBackgroundDrawable(resources.getDrawable(R.drawable.rectangle_all_bg, null))
        window.setGravity(Gravity.CENTER)
        webAddress.setText(getAddress("web"))
        filterAddress.setText(getAddress("filter"))
        btnRestore.setOnClickListener {
            webAddress.setText(R.string.default_web_address)
            filterAddress.setText(R.string.default_filter_address)
            sp(name = "address").clear()
            codeDialog.dismiss()
        }
        btnConfirm.setOnClickListener {
            var web = webAddress.text.toString()
            if (web.isEmpty()) {
                web = resources.getString(R.string.default_web_address)
            }
            var filter = filterAddress.text.toString()
            if (filter.isEmpty()) {
                filter = resources.getString(R.string.default_filter_address)
            }
            sp(name = "address").edit {
                putString("web", web)
                putString("filter", filter)
            }
            codeDialog.dismiss()
        }
        btnCancel.setOnClickListener { codeDialog.dismiss() }
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

    private fun updateTransform() {
        val matrix = Matrix()

        val centerX = mTtvPreview.width / 2f
        val centerY = mTtvPreview.height / 2f

        val rotationDegrees = when (mTtvPreview.display.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> return
        }
        matrix.postRotate(-rotationDegrees.toFloat(), centerX, centerY)

        val mWidth = mTtvPreview.width.toFloat()
        val mHeight = mTtvPreview.height.toFloat()

        val mPreviewWidth = 1280
        val mPreviewHeight = 720
        val previewRect = RectF(0f, 0f, mWidth, mHeight)
        var aspect: Float = (1f * mPreviewWidth) / mPreviewHeight

        if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            aspect = 1f / aspect
        }
        val mDisplayWidth: Float
        val mDisplayHeight: Float
        if (mWidth < (mHeight * aspect)) {
            mDisplayWidth = mWidth
            mDisplayHeight = (mHeight * aspect + .5).toFloat()
        } else {
            mDisplayWidth = (mWidth / aspect + .5).toFloat()
            mDisplayHeight = mHeight
        }
        val surfaceDimensions =
            RectF(0f, 0f, mDisplayWidth, mDisplayHeight)
        matrix.setRectToRect(previewRect, surfaceDimensions, Matrix.ScaleToFit.FILL)
        mTtvPreview.setTransform(matrix)
    }

    private val executor = Executors.newSingleThreadExecutor()

    private fun startCamera() {
        val mWindowManager = this.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        mWindowManager.defaultDisplay.getMetrics(metrics)
        val wWidth = metrics.widthPixels
        val wHeight = metrics.heightPixels
//        val min = if (wHeight >= wWidth) wWidth else wHeight
        Log.d("muses_size", "window width: $wWidth window height: $wHeight")

        // Create configuration object for the viewfinder use case
        val previewConfig = PreviewConfig.Builder().apply {
            //            setTargetResolution(Size(mTtvPreview.width, (1f * wWidth / 16 * 9).toInt()))
//            setTargetAspectRatio(AspectRatio.RATIO_4_3)
        }.build()


        // Build the viewfinder use case
        val preview = Preview(previewConfig)

        // Every time the viewfinder is updated, recompute layout
        preview.setOnPreviewOutputUpdateListener {

            // To update the SurfaceTexture, we have to remove it and re-add it
            val parent = mTtvPreview.parent as ViewGroup
            parent.removeView(mTtvPreview)
//            mTtvPreview.layoutParams =
//                LinearLayout.LayoutParams(wWidth, (1f * wWidth / 16 * 9).toInt())
            parent.addView(mTtvPreview, 0)

            mTtvPreview.surfaceTexture = it.surfaceTexture
            updateTransform()
        }

        // Add this before CameraX.bindToLifecycle

        // Create configuration object for the image capture use case
        val imageCaptureConfig = ImageCaptureConfig.Builder()
            .apply {
                // We don't set a resolution for image capture; instead, we
                // select a capture mode which will infer the appropriate
                // resolution based on aspect ration and requested mode
                setCaptureMode(ImageCapture.CaptureMode.MIN_LATENCY)
//                setTargetAspectRatio(AspectRatio.RATIO_16_9)
//                setTargetRotation(Surface.ROTATION_90)
            }.build()


        // Build the image capture use case and attach button click listener
        val imageCapture = ImageCapture(imageCaptureConfig)

        findViewById<AppCompatImageButton>(R.id.btn_take_photo_camera).setOnClickListener {
            val file = File(
                externalMediaDirs.first(),
                "muses.jpg"
            )

            imageCapture.takePicture(file, executor,
                object : ImageCapture.OnImageSavedListener {
                    override fun onError(
                        imageCaptureError: ImageCapture.ImageCaptureError,
                        message: String,
                        exc: Throwable?
                    ) {
                        val msg = "Photo capture failed: $message"
                        Log.e("CameraXApp", msg, exc)
                        mTtvPreview.post {
                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onImageSaved(file: File) {
                        val msg = "Photo capture succeeded: ${file.absolutePath}"
                        Log.d("CameraXApp", msg)
                        val intent = Intent()
                        intent.setClass(this@MainActivity, EditActivity::class.java)
                        val bundle = Bundle()
                        bundle.putString("Path", file.absolutePath)
                        intent.putExtras(bundle)
                        startActivity(intent)
                    }
                })
        }

        // Setup image analysis pipeline that computes average pixel luminance
        val analyzerConfig = ImageAnalysisConfig.Builder().apply {
            // In our analysis, we care more about the latest image than
            // analyzing *every* image
            setImageReaderMode(
                ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE
            )
        }.build()

        // Build the image analysis use case and instantiate our analyzer
        val analyzerUseCase = ImageAnalysis(analyzerConfig).apply {
            setAnalyzer(executor, LuminosityAnalyzer())
        }

        CameraX.bindToLifecycle(this, preview, imageCapture)
    }

    private class LuminosityAnalyzer : ImageAnalysis.Analyzer {
        private var lastAnalyzedTimestamp = 0L

        /**
         * Helper extension function used to extract a byte array from an
         * image plane buffer
         */
        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }

        override fun analyze(image: ImageProxy, rotationDegrees: Int) {
            val currentTimestamp = System.currentTimeMillis()
            // Calculate the average luma no more often than every second
            if (currentTimestamp - lastAnalyzedTimestamp >=
                TimeUnit.SECONDS.toMillis(1)
            ) {
                // Since format in ImageAnalysis is YUV, image.planes[0]
                // contains the Y (luminance) plane
                val buffer = image.planes[0].buffer
                // Extract image data from callback object
                val data = buffer.toByteArray()
                // Convert the data into an array of pixel values
                val pixels = data.map { it.toInt() and 0xFF }
                // Compute average luminance for the image
                val luma = pixels.average()
                // Log the new luma value
                Log.d("CameraXApp", "Average luminosity: $luma")
                // Update timestamp of last analyzed frame
                lastAnalyzedTimestamp = currentTimestamp
            }
        }
    }


}
