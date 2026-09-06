package com.example.personalvault.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Rational
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
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
import java.io.FileOutputStream

/**
 * A custom in-app camera screen (rather than launching the system camera app) so a
 * card-shaped guide frame can be drawn directly over the live preview — the system camera
 * app is a black box we can't draw over, which is exactly why wallet card photos need this
 * dedicated screen instead of the TakePicture() system-camera approach used elsewhere in the
 * app (e.g. document scanning).
 *
 * The preview is constrained to the SAME 3:4 aspect ratio as the captured photo (via the
 * outer Modifier.aspectRatio Box below) — this is what lets the guide overlay's proportions
 * be applied identically, after capture, directly onto the saved image's own pixel
 * dimensions to crop it to just the guided region. Without this matching aspect ratio, the
 * on-screen guide position wouldn't correspond to the same physical region in the saved
 * (differently-cropped/letterboxed) photo.
 */
private const val CARD_ASPECT_WIDTH_OVER_HEIGHT = 1f / 1.586f // portrait card: taller than wide
private const val GUIDE_WIDTH_FRACTION = 0.85f

@Composable
fun CardCameraScreen(onCaptured: (File) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isCapturing by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Constraining preview + capture to the same 3:4 portrait ratio means "what's shown"
        // and "what's saved" always match exactly — no separate letterboxing math needed.
        Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        // Default PERFORMANCE mode renders via a SurfaceView, which uses its
                        // own hardware-compositor layer and ignores normal View/Compose
                        // z-ordering — that's why the card-guide Canvas overlay below
                        // wouldn't show up on top of the live preview otherwise. COMPATIBLE
                        // mode uses a TextureView instead, which correctly blends with other
                        // content drawn on top of it.
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder()
                            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                            .setTargetRotation(previewView.display.rotation)
                            .build()
                            .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                        val capture = ImageCapture.Builder()
                            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                            .setTargetRotation(previewView.display.rotation)
                            .build()
                        imageCapture = capture
                        try {
                            cameraProvider.unbindAll()
                            // ViewPort guarantees ImageCapture's saved output is cropped to
                            // exactly the same camera-sensor region the person actually saw
                            // in the PreviewView — without this, devices whose sensor's real
                            // aspect ratio isn't quite 4:3 (very common — CameraX just picks
                            // the closest supported resolution) would save a differently
                            // framed/zoomed photo than what was shown on screen, which is
                            // exactly what caused the wrong-crop/zoomed-in result before this.
                            val viewPort = ViewPort.Builder(
                                Rational(3, 4),
                                previewView.display.rotation
                            ).build()
                            val useCaseGroup = UseCaseGroup.Builder()
                                .addUseCase(preview)
                                .addUseCase(capture)
                                .setViewPort(viewPort)
                                .build()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                useCaseGroup
                            )
                        } catch (e: Exception) {
                            Log.e("CardCameraScreen", "Camera bind failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                }
            )

            // Card-shaped guide overlay — a standard bank-card aspect ratio, centered. The
            // exact same proportions (GUIDE_WIDTH_FRACTION, CARD_ASPECT_WIDTH_OVER_HEIGHT) are
            // re-applied to the saved bitmap's own pixel size after capture, below.
            Canvas(Modifier.fillMaxSize()) {
                val guideWidth = size.width * GUIDE_WIDTH_FRACTION
                val guideHeight = guideWidth / CARD_ASPECT_WIDTH_OVER_HEIGHT
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
                            cropToGuideFrame(file)
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

/**
 * Crops the just-captured photo down to the same relative region the on-screen guide frame
 * covered, then overwrites the file with the cropped result. Safe to fail silently (leaves
 * the uncropped photo in place) — a slightly-uncropped photo is a far better outcome for the
 * person than a crash or a missing file.
 */
private fun cropToGuideFrame(file: File) {
    try {
        val raw = BitmapFactory.decodeFile(file.absolutePath) ?: return
        // Some devices bake the camera's rotation directly into the saved pixels; others
        // only record it as EXIF metadata and leave the raw pixels in the sensor's native
        // (often landscape) orientation. Normalizing to "upright" here first means the crop
        // math below can safely assume width/height always match what was actually shown
        // on screen, regardless of which behavior this particular device uses.
        val exif = androidx.exifinterface.media.ExifInterface(file.absolutePath)
        val orientation = exif.getAttributeInt(
            androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
            androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
        )
        val rotationDegrees = when (orientation) {
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val original = if (rotationDegrees != 0f) {
            val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees) }
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        } else raw

        val guideWidth = original.width * GUIDE_WIDTH_FRACTION
        val guideHeight = guideWidth / CARD_ASPECT_WIDTH_OVER_HEIGHT
        val left = ((original.width - guideWidth) / 2f).toInt().coerceIn(0, original.width - 1)
        val top = ((original.height - guideHeight) / 2f).toInt().coerceIn(0, original.height - 1)
        val width = guideWidth.toInt().coerceIn(1, original.width - left)
        val height = guideHeight.toInt().coerceIn(1, original.height - top)
        val cropped = Bitmap.createBitmap(original, left, top, width, height)
        FileOutputStream(file).use { out ->
            cropped.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
    } catch (e: Exception) {
        Log.e("CardCameraScreen", "Crop failed, keeping uncropped photo", e)
    }
}
