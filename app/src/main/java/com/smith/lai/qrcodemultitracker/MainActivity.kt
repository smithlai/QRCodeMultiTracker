package com.smith.lai.qrcodemultitracker

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
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
import com.king.camera.scan.BaseCameraScanActivity
import com.king.camera.scan.CameraScan
import com.king.camera.scan.analyze.Analyzer
import com.king.camera.scan.config.CameraConfigFactory
import com.king.mlkit.vision.barcode.analyze.BarcodeScanningAnalyzer
import com.king.view.viewfinderview.ViewfinderView
import com.smith.lai.qrcodemultitracker.ext.drawRect

/**
 * 多格式條碼掃描器
 * 支持同時掃描條形碼和QR碼
 */
class MainActivity : BaseCameraScanActivity<List<Barcode>>() {

    companion object {
        // 掃描模式控制
        // true: 持續掃描 | false: 跳出確認視窗
        private var USE_REALTIME_MODE = true

        // 覆蓋層模式控制
        // true: 截圖+紅框 | false: 只紅框
        private var USE_CAMERA_BITMAP = false

        // 日誌標籤
        private const val TAG = "MultiFormatScanner"
    }

    // UI更新控制
    private var lastUpdateTime = 0L
    private val updateIntervalMs = 10 // 每10毫秒更新一次UI

    // 條碼狀態追蹤
    private var currentBarcodes: List<Barcode>? = null
    private var hasClearedUI = false // 追蹤UI是否已經被清空

    // UI元素
    private var overlayImageView: ImageView? = null
    private var resultTextView: TextView? = null
    protected var viewfinderView: ViewfinderView? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "Current scan mode: ${if(USE_REALTIME_MODE) "Real-time" else "Original"}")
        Log.d(TAG, "Overlay mode: ${if(USE_CAMERA_BITMAP) "Using camera image" else "Transparent overlay"}")
    }

    /**
     * 創建多格式條碼分析器
     */
    override fun createAnalyzer(): Analyzer<List<Barcode>> {
        return BarcodeScanningAnalyzer(Barcode.FORMAT_ALL_FORMATS)
    }

    override fun initCameraScan(cameraScan: CameraScan<List<Barcode>>) {
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
    fun getViewfinderViewId(): Int {
        return R.id.viewfinderView
    }

    override fun initUI() {
        val viewfinderViewId = getViewfinderViewId()
        if (viewfinderViewId != View.NO_ID && viewfinderViewId != 0) {
            viewfinderView = findViewById(viewfinderViewId)
        }
        super.initUI()

        if (USE_REALTIME_MODE) {
            overlayImageView = findViewById(R.id.overlayImageView)
            resultTextView = findViewById(R.id.resultTextView)
        }
    }

    /**
     * 處理螢幕旋轉事件 - 重新初始化相機
     */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(TAG, "Configuration changed: ${if(newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) "Landscape" else "Portrait"}")

        // 釋放相機資源
        getCameraScan()?.release()

        // 短暫延遲以確保UI尺寸已更新
        Handler(Looper.getMainLooper()).postDelayed({
            // 重新初始化UI
            initUI()

            // 清除UI
            hasClearedUI = false
            clearUI()
        }, 100)
    }

    /**
     * 掃描失敗時的回調
     * 只在需要時清除UI，避免重複更新
     */
    override fun onScanResultFailure() {
        // 只在實時模式且未清除過UI時執行清除操作
        if (USE_REALTIME_MODE && !hasClearedUI && currentBarcodes?.isNotEmpty() == true) {
            Log.d(TAG, "No barcode detected: clearUI()")
            clearUI()
            hasClearedUI = true // 標記UI已經被清空
        }

        // 繼續掃描
        cameraScan.setAnalyzeImage(true)
    }

    /**
     * 清除UI上的條碼框和文本
     */
    private fun clearUI() {
        // 清空當前條碼列表
        currentBarcodes = mutableListOf()

        // 獲取螢幕尺寸
        val screenWidth = overlayImageView?.width ?: resources.displayMetrics.widthPixels
        val screenHeight = overlayImageView?.height ?: resources.displayMetrics.heightPixels

        // 創建透明覆蓋層，只繪製黑框
        val bitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)
        drawBlackBorder(canvas)

        // 更新UI
        overlayImageView?.setImageBitmap(bitmap)
        resultTextView?.text = ""
    }

    /**
     * 掃描結果回調
     */
    override fun onScanResultCallback(result: AnalyzeResult<List<Barcode>>) {
        if (USE_REALTIME_MODE) {
            handleRealtimeMode(result)
        } else {
            handleOriginalMode(result)
        }
    }

    /**
     * 處理實時模式的掃描結果
     */
    private fun handleRealtimeMode(result: AnalyzeResult<List<Barcode>>) {
        // 重設UI清除狀態，因為檢測到了新的條碼
        hasClearedUI = false

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
    private fun handleOriginalMode(result: AnalyzeResult<List<Barcode>>) {
        // 停止分析
        cameraScan.setAnalyzeImage(false)

        val buffer = StringBuilder()
        val bitmap = result.bitmap?.drawRect { canvas, paint ->
            for ((index, data) in result.result.withIndex()) {
                val barcodeType = getBarcodeTypeName(data.format)
                buffer.append("[$index] $barcodeType: ").append(data.displayValue).append("\n")
                data.boundingBox?.let { box ->
                    paint.color = if (data.format == Barcode.FORMAT_QR_CODE) Color.RED else Color.GREEN
                    canvas.drawRect(box, paint)
                }
            }
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
    private fun updateUI(result: AnalyzeResult<List<Barcode>>) {
        if (!USE_REALTIME_MODE) return

        // 儲存當前條碼
        currentBarcodes = result.result

        // 獲取螢幕尺寸
        val screenWidth = overlayImageView?.width ?: resources.displayMetrics.widthPixels
        val screenHeight = overlayImageView?.height ?: resources.displayMetrics.heightPixels

        // 根據設置決定使用哪種覆蓋層模式
        val bitmap = if (USE_CAMERA_BITMAP && result.bitmap != null) {
            createBitmapWithCameraPreview(result, screenWidth, screenHeight)
        } else {
            createTransparentOverlay(result, screenWidth, screenHeight)
        }

        // 更新UI
        overlayImageView?.setImageBitmap(bitmap)
        updateTextResult(result.result)
    }

    /**
     * 使用相機預覽圖像創建覆蓋層
     * 預覽圖像保持原始大小但半透明
     */
    private fun createBitmapWithCameraPreview(result: AnalyzeResult<List<Barcode>>, screenWidth: Int, screenHeight: Int): Bitmap {
        val sourceBitmap = result.bitmap!!
        val bitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 背景設為透明
        canvas.drawColor(Color.TRANSPARENT)

        // 使用已有的縮放計算方法
        val sourceWidth = sourceBitmap.width
        val sourceHeight = sourceBitmap.height
        val scale = calculateScale(sourceWidth, sourceHeight, screenWidth, screenHeight)

        // 計算縮放後的尺寸和位置
        val scaledWidth = sourceWidth * scale
        val scaledHeight = sourceHeight * scale
        val left = (screenWidth - scaledWidth) / 2
        val top = (screenHeight - scaledHeight) / 2

        // 設置半透明效果的畫筆 (保持80%透明度)
        val alphaPaint = Paint().apply {
            alpha = 51  // 設置透明度 (0-255，51約為20%不透明，即80%透明)
        }

        // 繪製相機預覽 (原始大小但半透明)
        val destRect = Rect(
            left.toInt(),
            top.toInt(),
            (left + scaledWidth).toInt(),
            (top + scaledHeight).toInt()
        )
        canvas.drawBitmap(sourceBitmap, null, destRect, alphaPaint)

        // 繪製黑色邊框
        drawBlackBorder(canvas)

        // 繪製條碼框
        if (result.result.isNotEmpty()) {
            val paint = Paint().apply {
                strokeWidth = 6f
                style = Paint.Style.STROKE
            }

            for (data in result.result) {
                data.boundingBox?.let { originalBox ->
                    // 計算條碼框的位置
                    val scaledBox = RectF(
                        left + originalBox.left * scale,
                        top + originalBox.top * scale,
                        left + originalBox.right * scale,
                        top + originalBox.bottom * scale
                    )

                    paint.color = if (data.format == Barcode.FORMAT_QR_CODE) Color.RED else Color.GREEN
                    canvas.drawRect(scaledBox, paint)
                }
            }
        }

        return bitmap
    }
    /**
     * 創建透明覆蓋層
     */
    private fun createTransparentOverlay(result: AnalyzeResult<List<Barcode>>, screenWidth: Int, screenHeight: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)

        // 繪製黑色邊框
        drawBlackBorder(canvas)

        // 只有在有條碼時繪製條碼框
        if (result.result.isNotEmpty() && result.bitmap != null) {
            val sourceBitmap = result.bitmap!!
            val sourceWidth = sourceBitmap.width
            val sourceHeight = sourceBitmap.height

            val scale = calculateScale(sourceWidth, sourceHeight, screenWidth, screenHeight)
            val scaledWidth = sourceWidth * scale
            val scaledHeight = sourceHeight * scale
            val left = (screenWidth - scaledWidth) / 2
            val top = (screenHeight - scaledHeight) / 2

            val paint = Paint().apply {
                strokeWidth = 6f
                style = Paint.Style.STROKE
            }

            for (data in result.result) {
                data.boundingBox?.let { originalBox ->
                    val scaledBox = RectF(
                        left + originalBox.left * scale,
                        top + originalBox.top * scale,
                        left + originalBox.right * scale,
                        top + originalBox.bottom * scale
                    )

                    paint.color = if (data.format == Barcode.FORMAT_QR_CODE) Color.RED else Color.GREEN
                    canvas.drawRect(scaledBox, paint)
                }
            }
        }

        return bitmap
    }

    /**
     * 更新文本結果
     */
    private fun updateTextResult(barcodes: List<Barcode>) {
        val buffer = StringBuilder()
//        buffer.append("=== (${barcodes.size}) ===").append("\n")
        for ((index, data) in barcodes.withIndex()) {
            val barcodeType = getBarcodeTypeName(data.format)
            buffer.append("[${index+1}] $barcodeType: ").append(data.displayValue).append("\n")
        }

        resultTextView?.text = buffer.toString()
    }

    /**
     * 計算縮放比例
     */
    private fun calculateScale(sourceWidth: Int, sourceHeight: Int, targetWidth: Int, targetHeight: Int): Float {
        val scaleX = targetWidth.toFloat() / sourceWidth
        val scaleY = targetHeight.toFloat() / sourceHeight
        return maxOf(scaleX, scaleY) // 使用較大的縮放比例確保覆蓋整個螢幕
    }

    /**
     * 繪製黑色邊框
     */
    private fun drawBlackBorder(canvas: Canvas) {
        val borderPaint = Paint().apply {
            style = Paint.Style.STROKE
            color = Color.BLACK
            strokeWidth = 12f
        }
        canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), borderPaint)
    }

    /**
     * 獲取條碼類型的友好名稱
     */
    private fun getBarcodeTypeName(format: Int): String {
        return when (format) {
            Barcode.FORMAT_QR_CODE -> "QR Code"
            Barcode.FORMAT_AZTEC -> "AZTEC"
            Barcode.FORMAT_CODABAR -> "CODABAR"
            Barcode.FORMAT_CODE_39 -> "CODE 39"
            Barcode.FORMAT_CODE_93 -> "CODE 93"
            Barcode.FORMAT_CODE_128 -> "CODE 128"
            Barcode.FORMAT_DATA_MATRIX -> "DATA MATRIX"
            Barcode.FORMAT_EAN_8 -> "EAN 8"
            Barcode.FORMAT_EAN_13 -> "EAN 13"
            Barcode.FORMAT_ITF -> "ITF"
            Barcode.FORMAT_PDF417 -> "PDF417"
            Barcode.FORMAT_UPC_A -> "UPC A"
            Barcode.FORMAT_UPC_E -> "UPC E"
            else -> "Unknown"
        }
    }

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