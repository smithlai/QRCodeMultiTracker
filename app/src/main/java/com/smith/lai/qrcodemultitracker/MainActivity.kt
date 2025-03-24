package com.smith.lai.qrcodemultitracker

import android.graphics.Bitmap
import android.graphics.Canvas
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
import com.king.camera.scan.analyze.Analyzer
import com.king.mlkit.vision.barcode.BarcodeCameraScanActivity
import com.king.mlkit.vision.barcode.analyze.BarcodeScanningAnalyzer
import com.smith.lai.qrcodemultitracker.ext.drawRect

/**
 * 連續偵測多個二維碼示例
 * 支持同時掃描條形碼和QR碼
 */
class MainActivity : BarcodeCameraScanActivity() {

    companion object {
        // 全局變數來控制掃描模式
        // true: 持續掃描
        // false: 跳出確認視窗
        private var USE_REALTIME_MODE = true

        // 是否在覆蓋層上顯示相機預覽圖像
        // true: 截圖+紅框
        // false: 只紅框
        private var USE_CAMERA_BITMAP = false

        // 日誌標籤
        private const val TAG = "MultiFormatScanner"
    }

    // Handler for updating UI
    private val handler = Handler(Looper.getMainLooper())
    private var lastUpdateTime = 0L
    private val updateIntervalMs = 30 // Update UI every 30ms

    // Keep track of current barcodes
    private var currentBarcodes: MutableList<Barcode>? = null
    private var overlayImageView: ImageView? = null
    private var resultTextView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "Current scan mode: ${if(USE_REALTIME_MODE) "Real-time" else "Original"}")
        Log.d(TAG, "Overlay mode: ${if(USE_CAMERA_BITMAP) "Using camera image" else "Transparent overlay"}")
    }

    /**
     * 創建多格式條碼分析器
     * 支持所有條碼格式，包括QR碼和其他一維碼
     */
    override fun createAnalyzer(): Analyzer<MutableList<Barcode>>? {
        // 設置掃描所有格式的條碼，包括QR碼和一維條碼
        return BarcodeScanningAnalyzer(Barcode.FORMAT_ALL_FORMATS)
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
                    // 顯示條碼類型和內容
                    val barcodeType = getBarcodeTypeName(data.format)
                    buffer.append("[$index] $barcodeType: ").append(data.displayValue).append("\n")
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

        // Store current barcodes
        currentBarcodes = result.result

        // 獲取螢幕尺寸
        val screenWidth = overlayImageView?.width ?: resources.displayMetrics.widthPixels
        val screenHeight = overlayImageView?.height ?: resources.displayMetrics.heightPixels

        // 根據設置決定使用哪種覆蓋層模式
        val bitmap: Bitmap
        if (USE_CAMERA_BITMAP && result.bitmap != null) {
            // 使用相機預覽圖像作為覆蓋層基礎，但調整尺寸以適應螢幕
            val sourceBitmap = result.bitmap

            // 創建一個與螢幕大小相同的bitmap
            bitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // 計算縮放和位置，確保相機預覽居中
            val sourceWidth = sourceBitmap!!.width
            val sourceHeight = sourceBitmap!!.height

            // 計算縮放比例（保持寬高比）
            val scaleX = screenWidth.toFloat() / sourceWidth
            val scaleY = screenHeight.toFloat() / sourceHeight
            val scale = maxOf(scaleX, scaleY) // 使用較大的縮放比例確保覆蓋整個螢幕

            // 計算縮放後的尺寸
            val scaledWidth = sourceWidth * scale
            val scaledHeight = sourceHeight * scale

            // 計算居中的位置
            val left = (screenWidth - scaledWidth) / 2
            val top = (screenHeight - scaledHeight) / 2

            // 繪製相機預覽（調整大小並居中）
            val destRect = android.graphics.Rect(
                left.toInt(),
                top.toInt(),
                (left + scaledWidth).toInt(),
                (top + scaledHeight).toInt()
            )
            canvas.drawBitmap(sourceBitmap, null, destRect, null)

            // 繪製黑色邊框（覆蓋整個螢幕）
            drawBlackBorder(canvas)

            // 調整條碼位置以匹配縮放和位置
            val paint = Paint().apply {
                strokeWidth = 6f
                style = Paint.Style.STROKE
                color = Color.RED
            }

            // 繪製條碼框（需要調整位置）
            for (data in result.result) {
                data.boundingBox?.let { originalBox ->
                    // 調整條碼框位置以匹配縮放和位移
                    val scaledBox = android.graphics.RectF(
                        left + originalBox.left * scale,
                        top + originalBox.top * scale,
                        left + originalBox.right * scale,
                        top + originalBox.bottom * scale
                    )

                    // QR碼使用紅色，其他條碼使用綠色
                    paint.color = if (data.format == Barcode.FORMAT_QR_CODE) Color.RED else Color.GREEN
                    canvas.drawRect(scaledBox, paint)
                }
            }
        } else {
            // 創建透明覆蓋層，只繪製框架（直接使用螢幕尺寸）
            bitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // 整個畫布透明
            canvas.drawColor(Color.TRANSPARENT)

            // 繪製黑色邊框（覆蓋整個螢幕）
            drawBlackBorder(canvas)

            // 如果有相機預覽圖像，計算條碼框的縮放和位置
            if (result.bitmap != null) {
                val sourceBitmap = result.bitmap
                val sourceWidth = sourceBitmap!!.width
                val sourceHeight = sourceBitmap!!.height

                // 計算縮放比例
                val scaleX = screenWidth.toFloat() / sourceWidth
                val scaleY = screenHeight.toFloat() / sourceHeight
                val scale = maxOf(scaleX, scaleY)

                // 計算縮放後的尺寸
                val scaledWidth = sourceWidth * scale
                val scaledHeight = sourceHeight * scale

                // 計算居中的位置
                val left = (screenWidth - scaledWidth) / 2
                val top = (screenHeight - scaledHeight) / 2

                // 繪製條碼框
                val paint = Paint().apply {
                    strokeWidth = 6f
                    style = Paint.Style.STROKE
                    color = Color.RED
                }

                // 繪製調整後的條碼框
                for (data in result.result) {
                    data.boundingBox?.let { originalBox ->
                        // 調整條碼框位置
                        val scaledBox = android.graphics.RectF(
                            left + originalBox.left * scale,
                            top + originalBox.top * scale,
                            left + originalBox.right * scale,
                            top + originalBox.bottom * scale
                        )

                        // QR碼使用紅色，其他條碼使用綠色
                        paint.color = if (data.format == Barcode.FORMAT_QR_CODE) Color.RED else Color.GREEN
                        canvas.drawRect(scaledBox, paint)
                    }
                }
            } else {
                // 沒有相機預覽圖像，無法確定條碼位置
                // 這種情況應該不會發生，因為掃描結果中總是包含bitmap
            }
        }

        // 更新UI
        overlayImageView?.setImageBitmap(bitmap)

        // 更新文本顯示
        val buffer = StringBuilder()
        for ((index, data) in result.result.withIndex()) {
            val barcodeType = getBarcodeTypeName(data.format)
            buffer.append("[$index] $barcodeType: ").append(data.displayValue).append("\n")
        }
        resultTextView?.text = buffer.toString()
    }
    /**
     * 繪製黑色邊框
     */
    private fun drawBlackBorder(canvas: Canvas) {
        val borderPaint = Paint().apply {
            style = Paint.Style.STROKE
            color = Color.BLACK
            strokeWidth = 12f // Thicker border
        }
        canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), borderPaint)
    }

    /**
     * 繪製條碼框
     */
    private fun drawBarcodeBoxes(canvas: Canvas, paint: Paint, barcodes: MutableList<Barcode>) {
        for (data in barcodes) {
            data.boundingBox?.let { box ->
                // QR碼使用紅色，其他條碼使用綠色
                paint.color = if (data.format == Barcode.FORMAT_QR_CODE) Color.RED else Color.GREEN
                canvas.drawRect(box, paint)
            }
        }
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

    /**
     * 設置是否使用相機預覽圖像作為覆蓋層基礎
     * @param useCameraBitmap true表示使用相機預覽圖像，false表示使用透明覆蓋層
     */
    fun setUseCameraBitmap(useCameraBitmap: Boolean) {
        USE_CAMERA_BITMAP = useCameraBitmap
    }

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