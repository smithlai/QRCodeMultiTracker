package com.smith.lai.qrcodemultitracker

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.camera.core.CameraSelector
import com.google.mlkit.vision.barcode.common.Barcode
import com.king.app.dialog.AppDialog
import com.king.app.dialog.AppDialogConfig
import com.king.camera.scan.AnalyzeResult
import com.king.camera.scan.CameraScan
import com.king.camera.scan.analyze.Analyzer
import com.king.camera.scan.config.CameraConfigFactory
import com.king.mlkit.vision.barcode.BarcodeCameraScanActivity
import com.king.mlkit.vision.barcode.analyze.BarcodeScanningAnalyzer
import com.smith.lai.qrcodemultitracker.ext.BarcodeUtils
import com.smith.lai.qrcodemultitracker.overlay.OverlayManager

/**
 * 多格式條碼掃描器
 * 支持同時掃描條形碼和QR碼
 */
class MainActivity : BarcodeCameraScanActivity() {

    companion object {
        // 掃描模式控制
        private var USE_REALTIME_MODE = true
        // 覆蓋層模式控制
        private var USE_CAMERA_BITMAP = true
        // 日誌標籤
        private const val TAG = "MultiFormatScanner"
    }

    // UI更新控制
    private var lastUpdateTime = 0L
    private val updateIntervalMs = 10 // 每10毫秒更新一次UI

    // 條碼狀態追蹤
    private var currentBarcodes: MutableList<Barcode>? = null

    // UI元素
    private var overlayImageView: ImageView? = null
    private var resultTextView: TextView? = null

    // 覆蓋層管理器
    private lateinit var overlayManager: OverlayManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "Current scan mode: ${if (USE_REALTIME_MODE) "Real-time" else "Original"}")
        Log.d(TAG, "Overlay mode: ${if (USE_CAMERA_BITMAP) "Using camera image" else "Transparent overlay"}")
    }

    /**
     * 創建多格式條碼分析器
     */
    override fun createAnalyzer(): Analyzer<MutableList<Barcode>>? {
        return BarcodeScanningAnalyzer(Barcode.FORMAT_ALL_FORMATS)
    }

    override fun initCameraScan(cameraScan: CameraScan<MutableList<Barcode>>) {
        super.initCameraScan(cameraScan)

        // 創建自定義的相機配置，指定使用後置相機
        val cameraConfig = CameraConfigFactory.createDefaultCameraConfig(this, CameraSelector.LENS_FACING_BACK)
        cameraScan.setCameraConfig(cameraConfig)

        if (USE_REALTIME_MODE) {
            cameraScan.setPlayBeep(false).setVibrate(false)
            viewfinderView?.visibility = View.GONE
        } else {
            cameraScan.setPlayBeep(true).setVibrate(true)
            viewfinderView?.visibility = View.VISIBLE
        }
    }

    override fun getLayoutId(): Int {
        return if (USE_REALTIME_MODE) {
            R.layout.continuous_qrcode_scan_activity
        } else {
            R.layout.multiple_qrcode_scan_activity
        }
    }

    override fun initUI() {
        super.initUI()

        if (USE_REALTIME_MODE) {
            overlayImageView = findViewById(R.id.overlayImageView)
            resultTextView = findViewById(R.id.resultTextView)

            // 初始化覆蓋層管理器
            overlayManager = OverlayManager(this, overlayImageView!!, resultTextView!!)
        }
    }

    /**
     * 處理螢幕旋轉事件 - 重新初始化相機
     */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(TAG, "Configuration changed: ${if (newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) "Landscape" else "Portrait"}")

        // 釋放相機資源
        getCameraScan()?.release()

        // 短暫延遲以確保UI尺寸已更新
        Handler(Looper.getMainLooper()).postDelayed({
            // 重新初始化UI
            initUI()
            clearUI()
        }, 100)
    }

    /**
     * 掃描失敗時的回調
     * 只在需要時清除UI，避免重複更新
     */
    override fun onScanResultFailure() {
        // 只在實時模式且未清除過UI時執行清除操作
        if (USE_REALTIME_MODE && currentBarcodes.isNullOrEmpty().not()) {
            Log.d(TAG, "No barcode detected: clearUI()")
            clearUI()
        }

        // 繼續掃描
        cameraScan.setAnalyzeImage(true)
    }

    /**
     * 清除UI上的條碼框和文本
     */
    private fun clearUI() {
        currentBarcodes = mutableListOf()
        overlayManager.takeIf { ::overlayManager.isInitialized }?.clearOverlay()
        resultTextView?.text = ""
    }

    /**
     * 掃描結果回調
     */
    override fun onScanResultCallback(result: AnalyzeResult<MutableList<Barcode>>) {
        if (USE_REALTIME_MODE) {
            handleRealtimeMode(result)
        } else {
            handleOriginalMode(result)
    }
    }

    /**
     * 處理實時模式的掃描結果
     */
    private fun handleRealtimeMode(result: AnalyzeResult<MutableList<Barcode>>) {
        // 節流UI更新，避免過於頻繁
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime >= updateIntervalMs) {
            lastUpdateTime = currentTime
            updateUI(result)
        }

        // 繼續掃描
        cameraScan.setAnalyzeImage(true)
    }

    /**
     * 處理原始模式的掃描結果
     */
    private fun handleOriginalMode(result: AnalyzeResult<MutableList<Barcode>>) {
        // 停止分析
        cameraScan.setAnalyzeImage(false)

        val buffer = StringBuilder()
        val bitmap = result.bitmap?.let {
            BarcodeUtils.drawBarcodeRect(it, result.result)
        }

        for ((index, data) in result.result.withIndex()) {
            val barcodeType = BarcodeUtils.getBarcodeTypeName(data.format)
            buffer.append("[$index] $barcodeType: ").append(data.displayValue).append("\n")
        }

        showResultDialog(buffer.toString(), bitmap)
    }

    /**
     * 顯示結果對話框
     */
    private fun showResultDialog(content: String, bitmap: Bitmap?) {
        val config = AppDialogConfig(this, R.layout.barcode_result_dialog)
        config.setContent(content).setOnClickConfirm {
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

    /**
     * 更新UI
     */
    private fun updateUI(result: AnalyzeResult<MutableList<Barcode>>) {
        if (!USE_REALTIME_MODE) return

        // 儲存當前條碼
        currentBarcodes = result.result

        // 更新覆蓋層
        if (::overlayManager.isInitialized) {
            overlayManager.updateOverlay(result, USE_CAMERA_BITMAP)
        }

        // 更新文本顯示
        overlayManager.updateTextResult(result.result)
    }


    //
//    /**
//     * 設置是否使用相機預覽圖像作為覆蓋層基礎
//     */
//    fun setUseCameraBitmap(useCameraBitmap: Boolean) {
//        USE_CAMERA_BITMAP = useCameraBitmap
//    }
//
//    /**
//     * 切換掃描模式
//     */
//    fun switchScanMode(useRealtimeMode: Boolean): Boolean {
//        if (USE_REALTIME_MODE != useRealtimeMode) {
//            USE_REALTIME_MODE = useRealtimeMode
//            return true  // 需要重啟活動以應用新設置
//        }
//        return false  // 模式沒有變化，不需要重啟
//    }
}