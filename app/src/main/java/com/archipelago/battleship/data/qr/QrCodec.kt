package com.archipelago.battleship.data.qr

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter

/** 纯 ZXing：生成用 QRCodeWriter，扫码用 CameraX 的 Y 平面 + MultiFormatReader。 */
object QrCodec {

    fun generate(text: String, size: Int = 512): ImageBitmap {
        val hints = HashMap<EncodeHintType, Any>()
        hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
        hints[EncodeHintType.MARGIN] = 1
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val pixels = IntArray(size * size)
        val dark = 0xFF0B192C.toInt()
        val light = 0xFFF4EBD9.toInt()
        for (y in 0 until size) {
            for (x in 0 until size) {
                pixels[y * size + x] = if (matrix[x, y]) dark else light
            }
        }
        val bmp = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
        return bmp.asImageBitmap()
    }

    fun yPlane(image: ImageProxy): ByteArray? {
        val plane = image.planes.firstOrNull() ?: return null
        val w = image.width
        val h = image.height
        if (w <= 0 || h <= 0) return null
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val out = ByteArray(w * h)
        return try {
            buffer.rewind()
            val limit = buffer.limit()
            for (r in 0 until h) {
                var idx = r * rowStride
                for (c in 0 until w) {
                    out[r * w + c] = if (idx < limit) buffer.get(idx) else 0
                    idx += pixelStride
                }
            }
            out
        } catch (_: Exception) {
            null
        }
    }

    fun decodeY(y: ByteArray, width: Int, height: Int, rotation: Int): String? {
        var data = y
        var w = width
        var h = height
        val turns = (((rotation % 360) + 360) % 360) / 90
        repeat(turns) {
            val rotated = rotate90(data, w, h)
            data = rotated
            val oldW = w
            w = h
            h = oldW
        }
        val reader = MultiFormatReader()
        return try {
            val source = PlanarYUVLuminanceSource(data, w, h, 0, 0, w, h, false)
            val hints = HashMap<DecodeHintType, Any>()
            hints[DecodeHintType.POSSIBLE_FORMATS] = listOf(BarcodeFormat.QR_CODE)
            hints[DecodeHintType.TRY_HARDER] = java.lang.Boolean.TRUE
            reader.setHints(hints)
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        } catch (_: Exception) {
            null
        } finally {
            reader.reset()
        }
    }

    private fun rotate90(src: ByteArray, w: Int, h: Int): ByteArray {
        val dst = ByteArray(src.size)
        for (r in 0 until h) {
            for (c in 0 until w) {
                val from = r * w + c
                val to = c * h + (h - 1 - r)
                if (from < src.size && to < dst.size) dst[to] = src[from]
            }
        }
        return dst
    }
}
