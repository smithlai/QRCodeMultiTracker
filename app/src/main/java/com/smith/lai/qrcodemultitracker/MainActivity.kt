package com.smith.lai.qrcodemultitracker

import android.util.Log
import android.widget.ImageView
import com.google.mlkit.vision.barcode.common.Barcode
import com.king.app.dialog.AppDialog
import com.king.app.dialog.AppDialogConfig
import com.king.camera.scan.AnalyzeResult
import com.king.camera.scan.CameraScan
import com.smith.lai.qrcodemultitracker.ext.drawRect
import com.king.mlkit.vision.barcode.QRCodeCameraScanActivity

/**
 * 扫描多个二维码示例
 * @author <a href="mailto:jenly1314@gmail.com">Jenly</a>
 */
class MainActivity : QRCodeCameraScanActivity() {
    override fun initCameraScan(cameraScan: CameraScan<MutableList<Barcode>>) {
        super.initCameraScan(cameraScan)
        cameraScan.setPlayBeep(true)
            .setVibrate(true)
    }

    override fun getLayoutId(): Int {
        return R.layout.multiple_qrcode_scan_activity
    }

    override fun onScanResultCallback(result: AnalyzeResult<MutableList<Barcode>>) {
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