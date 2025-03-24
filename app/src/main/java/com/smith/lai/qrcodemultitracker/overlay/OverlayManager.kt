package com.smith.lai.qrcodemultitracker.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.widget.ImageView
import android.widget.TextView
import com.google.mlkit.vision.barcode.common.Barcode
import com.king.camera.scan.AnalyzeResult
import com.smith.lai.qrcodemultitracker.ext.BarcodeUtils

/**
 * 覆蓋層管理器
 * 負責創建和更新覆蓋層，顯示條碼標記框等
 */
class OverlayManager(
    private val context: Context,
    private val overlayImageView: ImageView,
    private val resultTextView: TextView
) {

    /**
     * 清除覆蓋層
     */
    fun clearOverlay() {
        val screenWidth = overlayImageView.width
        val screenHeight = overlayImageView.height

        // 創建透明覆蓋層，只繪製黑框
        val bitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)
        drawBlackBorder(canvas)

        // 更新UI
        overlayImageView.setImageBitmap(bitmap)
    }

    /**
     * 更新覆蓋層
     */
    fun updateOverlay(result: AnalyzeResult<MutableList<Barcode>>, useCameraBitmap: Boolean) {
        val screenWidth = overlayImageView.width
        val screenHeight = overlayImageView.height

        // 根據設置決定使用哪種覆蓋層模式
        val bitmap = if (useCameraBitmap && result.bitmap != null) {
            createBitmapWithCameraPreview(result, screenWidth, screenHeight)
        } else {
            createTransparentOverlay(result, screenWidth, screenHeight)
        }

        // 更新UI
        overlayImageView.setImageBitmap(bitmap)
    }

    /**
     * 使用相機預覽圖像創建覆蓋層
     * 預覽圖像保持原始大小但半透明
     */
    private fun createBitmapWithCameraPreview(result: AnalyzeResult<MutableList<Barcode>>, screenWidth: Int, screenHeight: Int): Bitmap {
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
    private fun createTransparentOverlay(result: AnalyzeResult<MutableList<Barcode>>, screenWidth: Int, screenHeight: Int): Bitmap {
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
     * 更新文本結果
     */
    fun updateTextResult(barcodes: List<Barcode>) {
        val buffer = StringBuilder()
        buffer.append("=== (${barcodes.size}) ===").append("\n")
        for ((index, data) in barcodes.withIndex()) {
            val barcodeType = BarcodeUtils.getBarcodeTypeName(data.format)
            buffer.append("[$index] $barcodeType: ").append(data.displayValue).append("\n")
        }

        resultTextView?.text = buffer.toString()
    }
}