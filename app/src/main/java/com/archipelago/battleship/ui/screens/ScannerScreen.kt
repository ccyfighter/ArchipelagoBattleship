package com.archipelago.battleship.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.archipelago.battleship.data.qr.QrCodec

private class QrAnalyzer(private val onQr: (String) -> Unit) : ImageAnalysis.Analyzer {

    @Volatile
    private var done = false

    override fun analyze(image: ImageProxy) {
        if (done) {
            image.close()
            return
        }
        try {
            val y = QrCodec.yPlane(image)
            if (y != null) {
                val raw = QrCodec.decodeY(y, image.width, image.height, image.imageInfo.rotationDegrees)
                if (!raw.isNullOrEmpty()) {
                    done = true
                    onQr(raw)
                }
            }
        } catch (_: Exception) {
        } finally {
            image.close()
        }
    }
}

@Composable
fun ScannerScreen(
    activity: ComponentActivity,
    onResult: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }

    val providerHolder = remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val found = remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                providerHolder.value?.unbindAll()
            } catch (_: Exception) {
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (granted) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    val future = ProcessCameraProvider.getInstance(ctx)
                    val executor = ContextCompat.getMainExecutor(ctx)
                    future.addListener({
                        try {
                            val provider = future.get()
                            providerHolder.value = provider
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                            analysis.setAnalyzer(executor, QrAnalyzer { raw ->
                                if (!found.value) {
                                    found.value = true
                                    onResult(raw)
                                }
                            })
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                activity,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis
                            )
                        } catch (_: Exception) {
                        }
                    }, executor)
                    previewView
                }
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (granted) "将房主的房间二维码放入取景框" else "需要相机权限才能扫码",
                color = MaterialTheme.colorScheme.primary
            )
            Button(onClick = onCancel, modifier = Modifier.padding(top = 8.dp)) {
                Text("取消")
            }
        }
    }
}
