package com.smc.visual

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.Button
import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Environment
import android.os.Looper
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.smc.visual.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.widget.Toast
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.Preview
import androidx.camera.core.CameraSelector
import android.util.Log
import androidx.camera.core.ImageCaptureException
import android.os.Handler
import java.io.File

typealias LumaListener = (luma: Double) -> Unit

class MainActivity : AppCompatActivity() {
    private lateinit var fontSize: TextView
    private lateinit var reduceFontSize: Button
    private lateinit var increaseFontSize: Button

    private lateinit var viewBinding: ActivityMainBinding

    // Handler 以及周期性任务配置
    private val handler = Handler(Looper.getMainLooper())

    private val periodicTask = object : Runnable {
        override fun run() {
            takePhoto()

            handler.postDelayed(this, 5000)
        }
    }

    // CameraX 初始化
    private var imageCapture: ImageCapture? = null

    private lateinit var cameraExecutor: ExecutorService

    // 设置存储位置
    private val picturesDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)

    // 设置字号大小模块
    private var _textSize = 32f
    var textSize: Float
        get() = _textSize
        set(value) {
            _textSize = value

            update()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        clearImageDirectory()

        fontSize = findViewById(R.id.font_size)
        reduceFontSize = findViewById(R.id.reduceFontSize)
        increaseFontSize  = findViewById(R.id.increaseFontSize)

        update()

        startPeriodicTask()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                    R.string.apply_for_permission,
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        stopPeriodicTask()
    }

    fun reduceTextSize(view: View) {
        if (textSize > 26f) {
            textSize -= 2f
        }
    }

    fun increaseTextSize(view: View) {
        if (textSize < 38f) {
            textSize += 2f
        }
    }

    private fun update() {
        fontSize.textSize = textSize
    }

    private fun clearImageDirectory() {
        val imageDir = File(picturesDirectory, "Sounds&Words")

        if (imageDir.exists() && imageDir.isDirectory) {
            if (imageDir.deleteRecursively()) {
                Log.d(TAG, "图片目录已清空")
            } else {
                Log.e(TAG, "无法清空图片目录")
            }
        } else {
            Log.d(TAG, "图片目录不存在")
        }
    }

    private fun startPeriodicTask() {
        handler.post(periodicTask)
    }

    private fun stopPeriodicTask() {
        handler.removeCallbacks(periodicTask)
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        // 创建文件
        val name = "Capture.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Sounds&Words")
        }

        val capture = File(picturesDirectory, "Sounds&Words/Capture.jpg")

        capture.delete()

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        // 设置图片监听器
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "捕获失败：${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Log.d(TAG, "捕获成功：${output.savedUri}")
                }
            }
        )
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // 绑定生命周期
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // 预览
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            // 初始化 imageCapture
            imageCapture = ImageCapture.Builder().build()

            // 默认使用后置摄像头
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // 解除绑定
                cameraProvider.unbindAll()

                // 绑定相机
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture)

            } catch(exc: Exception) {
                Log.e(TAG, "绑定失败", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val TAG = "Sounds & Words"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA
            ).toTypedArray()
    }
}