package com.smith.lai.qrcodemultitracker.ext

import android.graphics.Bitmap
import android.graphics.Color
import com.google.mlkit.vision.barcode.common.Barcode

/**
 * 條碼工具類
 * 提供條碼相關的通用功能
 */
object BarcodeUtils {

    /**
     * 獲取條碼類型的友好名稱
     */
    fun getBarcodeTypeName(format: Int): String {
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
     * 創建包含條碼標記的覆蓋層圖像
     */
    fun drawBarcodeRect(bitmap: Bitmap, barcodes: List<Barcode>): Bitmap {
        return bitmap.drawRect { canvas, paint ->
            for (data in barcodes) {
                data.boundingBox?.let { box ->
                    paint.color = if (data.format == Barcode.FORMAT_QR_CODE) Color.RED else Color.GREEN
                    canvas.drawRect(box, paint)
                }
            }
        }
    }

    /**
     * 從條碼中提取URL
     */
    fun extractUrl(barcode: Barcode): String? {
        if (barcode.valueType == Barcode.TYPE_URL) {
            return barcode.url?.url
        }

        // 嘗試從原始內容中提取URL
        val rawValue = barcode.rawValue ?: return null
        if (rawValue.startsWith("http://") || rawValue.startsWith("https://")) {
            return rawValue
        }

        return null
    }

    /**
     * 從條碼中提取聯絡人信息
     */
    fun extractContact(barcode: Barcode): String? {
        if (barcode.valueType == Barcode.TYPE_CONTACT_INFO) {
            val contact = barcode.contactInfo ?: return null
            val name = contact.name?.formattedName ?: ""
            val phones = contact.phones.joinToString(", ") { it.number ?: "" }
            val emails = contact.emails.joinToString(", ") { it.address ?: "" }

            return "Name: $name\nPhone: $phones\nEmail: $emails"
        }
        return null
    }

    /**
     * 從條碼中提取WiFi信息
     */
    fun extractWifi(barcode: Barcode): String? {
        if (barcode.valueType == Barcode.TYPE_WIFI) {
            val wifi = barcode.wifi ?: return null
            val ssid = wifi.ssid ?: ""
            val password = wifi.password ?: ""
            val encryptionType = when (wifi.encryptionType) {
                Barcode.WiFi.TYPE_OPEN -> "Open"
                Barcode.WiFi.TYPE_WPA -> "WPA"
                Barcode.WiFi.TYPE_WEP -> "WEP"
                else -> "Unknown"
            }

            return "SSID: $ssid\nPassword: $password\nType: $encryptionType"
        }
        return null
    }
}