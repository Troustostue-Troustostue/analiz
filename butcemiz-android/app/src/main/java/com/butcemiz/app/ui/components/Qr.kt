package com.butcemiz.app.ui.components

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

fun qrBitmap(text: String, sizePx: Int = 720): Bitmap {
    val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 1)
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val pixels = IntArray(matrix.width * matrix.height)
    for (y in 0 until matrix.height) {
        val off = y * matrix.width
        for (x in 0 until matrix.width) pixels[off + x] = if (matrix.get(x, y)) 0xFF0B1F1A.toInt() else 0xFFFFFFFF.toInt()
    }
    return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
}

@Composable
fun QrImage(text: String, modifier: Modifier = Modifier, size: Dp = 240.dp) {
    val bmp = remember(text) { qrBitmap(text).asImageBitmap() }
    Image(
        bitmap = bmp,
        contentDescription = "Davet QR kodu",
        filterQuality = FilterQuality.None,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(10.dp),
    )
}

/** Google Play hizmetlerinin QR tarayıcısı (kamera izni gerektirmez). */
fun scanQr(context: Context, onResult: (String) -> Unit, onError: (String) -> Unit) {
    val activity = context as? Activity
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .enableAutoZoom()
        .build()
    val scanner = if (activity != null) GmsBarcodeScanning.getClient(activity, options) else GmsBarcodeScanning.getClient(context, options)
    scanner.startScan()
        .addOnSuccessListener { code -> code.rawValue?.let(onResult) ?: onError("QR okunamadı") }
        .addOnCanceledListener { }
        .addOnFailureListener { e -> onError(e.localizedMessage ?: "Tarayıcı açılamadı. Kodu yapıştırarak devam edebilirsiniz.") }
}
