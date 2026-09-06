package com.example.personalvault.ui.screens

import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.personalvault.R
import com.example.personalvault.util.FileUtils
import java.io.File

/**
 * A custom in-app camera screen (rather than launching the system camera app) so a
 * card-shaped guide frame can be drawn directly over the live preview — the system camera
 * app is a black box we can't draw over, which is exactly why wallet card photos need this
 * dedicated screen instead of the TakePicture() system-camera approach used elsewhere in the
 * app (e.g. document scanning).
 */
@Composable
fun CardCameraScreen(onCaptured: (File) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isCapturing by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val capture = ImageCapture.Builder().build()
                    imageCapture = capture
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            capture
                        )
                    } catch (e: Exception) {
                        Log.e("CardCameraScreen", "Camera bind failed", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }
        )

        // Card-shaped guide overlay — a standard bank-card aspect ratio (1.586:1), centered,
        // so the person can line up the physical card precisely before capturing.
        Canvas(Modifier.fillMaxSize()) {
            val guideWidth = size.width * 0.85f
            val guideHeight = guideWidth / 1.586f
            val left = (size.width - guideWidth) / 2f
            val top = (size.height - guideHeight) / 2f
            drawRoundRect(
                color = Color.White,
                topLeft = androidx.compose.ui.geometry.Offset(left, top),
                size = Size(guideWidth, guideHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(24f, 24f),
                style = Stroke(width = 6f)
            )
        }

        IconButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel), tint = Color.White)
        }

        FloatingActionButton(
            onClick = {
                if (isCapturing) return@FloatingActionButton
                val capture = imageCapture ?: return@FloatingActionButton
                val file = FileUtils.createImageCaptureFile(context)
                val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
                isCapturing = true
                capture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onError(exception: ImageCaptureException) {
                            isCapturing = false
                            Log.e("CardCameraScreen", "Capture failed", exception)
                        }
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            isCapturing = false
                            onCaptured(file)
                        }
                    }
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            Icon(Icons.Default.PhotoCamera, contentDescription = stringResource(R.string.wallet_take_photo))
        }
    }
}
