package com.muses

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.os.EnvironmentCompat
import com.lxj.androidktx.core.clear
import com.lxj.androidktx.core.edit
import com.lxj.androidktx.core.sp
import com.yalantis.ucrop.UCrop
import java.io.File
import java.io.IOException


private const val REQUEST_CODE_PERMISSIONS = 10
private const val REQUEST_CODE_CAMERA = 11
private const val CAMERA_IMAGE_NAME = "MusesImage.jpg"
private const val CROPPED_IMAGE_NAME = "MusesCropImage.jpg"

private val REQUIRED_PERMISSIONS = arrayOf(
    Manifest.permission.CAMERA,
    Manifest.permission.INTERNET,
    Manifest.permission.READ_EXTERNAL_STORAGE,
    Manifest.permission.WRITE_EXTERNAL_STORAGE
)

class MainActivity : AppCompatActivity() {

    private val codeDialog by lazy { initCodeDialog() }

    private val isAndroidN =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

    private lateinit var cameraImageUri: Uri
    private lateinit var cropImagePath: String


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<AppCompatImageView>(R.id.iv_setting_main).setOnClickListener {
            codeDialog.show()
        }

        findViewById<AppCompatImageView>(R.id.iv_camera_main).setOnClickListener {
            if (checkPermissions()) {
                startCamera()
            } else {
                ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
                )
            }
        }

    }


    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (checkPermissions()) {
                startCamera()
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

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_CODE_CAMERA) {
                startCrop()
            } else if (requestCode == UCrop.REQUEST_CROP) {
                val intent = Intent()
                intent.setClass(this, EditActivity::class.java)
                val bundle = Bundle()
                bundle.putString("Path", cropImagePath)
                intent.putExtras(bundle)
                startActivity(intent)
            }
        }

    }

    @Throws(IOException::class)
    private fun createImageFile(): File? {
        val imageName: String = CAMERA_IMAGE_NAME
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES+"/MusesPad")
//        Log.d("MainActivity", "createImageFile: $storageDir")
        if (!storageDir!!.exists()) {
            storageDir.mkdir()
        }
        val tempFile = File(storageDir, imageName)
        return if (!Environment.MEDIA_MOUNTED.equals(EnvironmentCompat.getStorageState(tempFile))) {
            null
        } else tempFile
    }

    private fun startCamera() {
        val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (captureIntent.resolveActivity(packageManager) != null) {
            var photoFile: File? = null
            var photoUri: Uri? = null
            try {
                photoFile = createImageFile()
            } catch (e: IOException) {
                e.printStackTrace()
            }

            if (photoFile != null) {
                photoUri = if (isAndroidN) {
                    FileProvider.getUriForFile(
                        this,
                        "$packageName.FileProvider",
                        photoFile
                    )
                } else {
                    Uri.fromFile(photoFile)
                }
            }
            if (photoUri != null) {
                cameraImageUri = photoUri
                captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                captureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                startActivityForResult(captureIntent, REQUEST_CODE_CAMERA)
            }
        }
    }

    private fun startCrop() {
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES+"/MusesPad")
        if (!storageDir!!.exists()) {
            storageDir.mkdir()
        }
        val file = File(storageDir, CROPPED_IMAGE_NAME)
        cropImagePath = file.absolutePath
        val newUri = Uri.fromFile(file)
        UCrop.of(cameraImageUri, newUri)
            .withAspectRatio(1F, 1F)
            .withMaxResultSize(1000, 1000)
            .start(this@MainActivity)
    }

    private fun checkPermissions() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
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
        window.setBackgroundDrawable(resources.getDrawable(R.drawable.white_rectangle_bg, null))
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
}
