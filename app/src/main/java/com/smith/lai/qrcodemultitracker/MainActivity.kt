package com.smith.lai.qrcodemultitracker

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.google.mlkit.vision.barcode.common.Barcode
import com.king.app.dialog.AppDialog
import com.king.app.dialog.AppDialogConfig
import com.king.camera.scan.AnalyzeResult
import com.king.camera.scan.CameraScan
import com.smith.lai.qrcodemultitracker.ext.drawRect
import com.king.mlkit.vision.barcode.QRCodeCameraScanActivity

/**
 * 連續偵測多個二維碼示例
 * @author <a href="mailto:jenly1314@gmail.com">Jenly</a>
 * Continuous detection modification
 */
class MainActivity : QRCodeCameraScanActivity() {

    companion object {
        // 全局變數來控制掃描模式
        private var USE_REALTIME_MODE = true
    }

    // Handler for updating UI
    private val handler = Handler(Looper.getMainLooper())
    private var lastUpdateTime = 0L
    private val updateIntervalMs = 500 // Update UI every 500ms

    // Keep track of current QR codes
    private var currentBarcodes: MutableList<Barcode>? = null
    private var overlayImageView: ImageView? = null
    private var resultTextView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "Current scan mode: ${if(USE_REALTIME_MODE) "Real-time" else "Original"}")
    }

    override fun initCameraScan(cameraScan: CameraScan<MutableList<Barcode>>) {
        super.initCameraScan(cameraScan)

        // 根據模式設置不同的行為
        if (USE_REALTIME_MODE) {
            cameraScan.setPlayBeep(false) // 實時模式關閉提示音
                .setVibrate(false)        // 實時模式關閉震動

            // 隱藏掃描框
            viewfinderView?.visibility = View.GONE
        } else {
            cameraScan.setPlayBeep(true)  // 原始模式開啟提示音
                .setVibrate(true)         // 原始模式開啟震動

            // 顯示掃描框
            viewfinderView?.visibility = View.VISIBLE
        }
    }

    override fun getLayoutId(): Int {
        // 根據模式選擇不同的布局
        return if (USE_REALTIME_MODE) {
            R.layout.continuous_qrcode_scan_activity
        } else {
            R.layout.multiple_qrcode_scan_activity
        }
    }

    override fun initUI() {
        super.initUI()

        // 只在實時模式下初始化這些視圖
        if (USE_REALTIME_MODE) {
            overlayImageView = findViewById(R.id.overlayImageView)
            resultTextView = findViewById(R.id.resultTextView)
        }
    }

    override fun onScanResultCallback(result: AnalyzeResult<MutableList<Barcode>>) {
        if (USE_REALTIME_MODE) {
            // 實時模式: 持續掃描
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastUpdateTime >= updateIntervalMs) {
                lastUpdateTime = currentTime
                updateUI(result)
            }

            // 繼續掃描
            cameraScan.setAnalyzeImage(true)
        } else {
            // 原始模式: 停止掃描並顯示結果對話框
            // 停止分析
            cameraScan.setAnalyzeImage(false)

            val buffer = StringBuilder()
            val bitmap = result.bitmap?.drawRect { canvas, paint ->
                for ((index, data) in result.result.withIndex()) {
                    buffer.append("[$index] ").append(data.displayValue).append("\n")
                    data.boundingBox?.let { box ->
                        canvas.drawRect(box, paint)
                    }
                }
            }

            val config = AppDialogConfig(this, R.layout.barcode_result_dialog)
            config.setContent(buffer).setOnClickConfirm {
                AppDialog.INSTANCE.dismissDialog()
                cameraScan.setAnalyzeImage(true)
            }.setOnClickCancel {
                AppDialog.INSTANCE.dismissDialog()
                finish()
            }

            val imageView = config.getView<ImageView>(R.id.ivDialogContent)
            imageView.setImageBitmap(bitmap)
            AppDialog.INSTANCE.showDialog(config, false)
        }
    }

    private fun updateUI(result: AnalyzeResult<MutableList<Barcode>>) {
        // 只在實時模式下更新UI
        if (!USE_REALTIME_MODE) return

        // Store current QR codes
        currentBarcodes = result.result

        // Create overlay with bounding boxes and black border
        val buffer = StringBuilder()
        val bitmap = result.bitmap?.drawRect { canvas, paint ->
            // First draw black border around the entire image
            val borderPaint = Paint()
            borderPaint.style = Paint.Style.STROKE
            borderPaint.color = Color.BLACK
            borderPaint.strokeWidth = 12f // Thicker border
            canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), borderPaint)

            // Then draw the QR code bounding boxes
            for ((index, data) in result.result.withIndex()) {
                buffer.append("[$index] ").append(data.displayValue).append("\n")
                data.boundingBox?.let { box ->
                    canvas.drawRect(box, paint)
                }
            }
        }

        // Update UI with overlay and text
        overlayImageView?.setImageBitmap(bitmap)
        resultTextView?.text = buffer.toString()
    }
//
//    /**
//     * 切換掃描模式
//     * @param useRealtimeMode 是否使用實時模式
//     * @return 是否需要重啟活動以應用新模式
//     */
//    fun switchScanMode(useRealtimeMode: Boolean): Boolean {
//        if (USE_REALTIME_MODE != useRealtimeMode) {
//            USE_REALTIME_MODE = useRealtimeMode
//            return true  // 需要重啟活動以應用新設置
//        }
//        return false  // 模式沒有變化，不需要重啟
//    }
}