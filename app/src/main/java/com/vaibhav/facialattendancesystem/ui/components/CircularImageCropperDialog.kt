package com.vaibhav.facialattendancesystem.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Rect as AndroidRect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vaibhav.facialattendancesystem.ui.theme.PrimaryCyan
import kotlin.math.roundToInt

/**
 * Enterprise-grade circular image cropper with pinch-to-zoom, two-finger pan,
 * and rule-of-thirds grid reticle (Telegram / WhatsApp / Apple style).
 *
 * Bug fix: canvas size is now tracked via onSizeChanged so the offset-to-output
 * coordinate mapping uses the real pixel dimensions instead of a hardcoded 300px guess.
 */
@Composable
fun CircularImageCropperDialog(
    sourceBitmap: Bitmap,
    onDismiss: () -> Unit,
    onCropConfirmed: (Bitmap) -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Track the actual canvas pixel size so crop math is accurate
    var canvasWidthPx by remember { mutableFloatStateOf(0f) }
    var canvasHeightPx by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Crop Profile Picture",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Drag to reposition · Pinch to zoom",
                        color = Color.Gray,
                        fontSize = 12.5.sp,
                        textAlign = TextAlign.Center
                    )
                }

                // Interactive Crop Viewport
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onSizeChanged { size ->
                            canvasWidthPx = size.width.toFloat()
                            canvasHeightPx = size.height.toFloat()
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(0.5f, 5.0f)
                                offset += pan
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val imageBitmap = remember(sourceBitmap) { sourceBitmap.asImageBitmap() }

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasW = size.width
                        val canvasH = size.height
                        // Update tracked values from drawscope (more accurate than onSizeChanged px)
                        canvasWidthPx = canvasW
                        canvasHeightPx = canvasH

                        val circleDiameter = minOf(canvasW * 0.78f, canvasH * 0.58f, 320.dp.toPx())
                        val circleRadius = circleDiameter / 2f
                        val center = Offset(canvasW / 2f, canvasH / 2f)

                        // 1. Draw transformed image
                        val baseScale = maxOf(circleDiameter / sourceBitmap.width, circleDiameter / sourceBitmap.height)
                        val effectiveScale = baseScale * scale
                        val drawnWidth = sourceBitmap.width * effectiveScale
                        val drawnHeight = sourceBitmap.height * effectiveScale

                        val imgTopLeft = Offset(
                            center.x - drawnWidth / 2f + offset.x,
                            center.y - drawnHeight / 2f + offset.y
                        )

                        drawImage(
                            image = imageBitmap,
                            dstOffset = IntOffset(imgTopLeft.x.roundToInt(), imgTopLeft.y.roundToInt()),
                            dstSize = IntSize(drawnWidth.roundToInt(), drawnHeight.roundToInt())
                        )

                        // 2. Draw dark vignette shroud outside circular aperture
                        val shroud = Path().apply {
                            addRect(Rect(0f, 0f, canvasW, canvasH))
                            val circleHole = Path().apply {
                                addOval(
                                    Rect(
                                        center.x - circleRadius,
                                        center.y - circleRadius,
                                        center.x + circleRadius,
                                        center.y + circleRadius
                                    )
                                )
                            }
                            op(this, circleHole, PathOperation.Difference)
                        }
                        drawPath(shroud, color = Color.Black.copy(alpha = 0.75f))

                        // 3. Draw circle framing ring
                        drawCircle(
                            color = PrimaryCyan,
                            radius = circleRadius,
                            center = center,
                            style = Stroke(width = 2.dp.toPx())
                        )

                        // 4. Subtle grid lines inside circle (rule of thirds)
                        val third1 = circleRadius * (2f / 3f)
                        val third2 = circleRadius * (4f / 3f)
                        val leftX = center.x - circleRadius
                        val topY = center.y - circleRadius

                        // Horizontal grid lines
                        drawLine(
                            color = Color.White.copy(alpha = 0.25f),
                            start = Offset(center.x - circleRadius * 0.75f, topY + third1),
                            end = Offset(center.x + circleRadius * 0.75f, topY + third1),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color = Color.White.copy(alpha = 0.25f),
                            start = Offset(center.x - circleRadius * 0.75f, topY + third2),
                            end = Offset(center.x + circleRadius * 0.75f, topY + third2),
                            strokeWidth = 1.dp.toPx()
                        )
                        // Vertical grid lines
                        drawLine(
                            color = Color.White.copy(alpha = 0.25f),
                            start = Offset(leftX + third1, center.y - circleRadius * 0.75f),
                            end = Offset(leftX + third1, center.y + circleRadius * 0.75f),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color = Color.White.copy(alpha = 0.25f),
                            start = Offset(leftX + third2, center.y - circleRadius * 0.75f),
                            end = Offset(leftX + third2, center.y + circleRadius * 0.75f),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }

                // Controls Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Text("Cancel", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            // Use the tracked canvas dimensions for accurate coordinate mapping
                            val cW = canvasWidthPx.takeIf { it > 0f } ?: 1080f
                            val cH = canvasHeightPx.takeIf { it > 0f } ?: 1920f

                            val targetOutputSize = 400
                            val outputBitmap = Bitmap.createBitmap(targetOutputSize, targetOutputSize, Bitmap.Config.ARGB_8888)
                            val canvas = AndroidCanvas(outputBitmap)

                            // Mirror the preview draw math at output resolution:
                            // circleDiameter (in view px) → circle covers targetOutputSize px in output
                            val circleDiameterView = minOf(cW * 0.78f, cH * 0.58f, 320f * (cW / 1080f).coerceAtLeast(1f))
                            val baseScale = maxOf(circleDiameterView / sourceBitmap.width, circleDiameterView / sourceBitmap.height)
                            val effectiveScale = baseScale * scale

                            // Scale factor from view space → output space (output = targetOutputSize, view = circleDiameterView)
                            val viewToOutput = targetOutputSize.toFloat() / circleDiameterView

                            val drawnW = sourceBitmap.width * effectiveScale * viewToOutput
                            val drawnH = sourceBitmap.height * effectiveScale * viewToOutput

                            // offset in view px → output px
                            val outOffsetX = offset.x * viewToOutput
                            val outOffsetY = offset.y * viewToOutput

                            val dstLeft = (targetOutputSize / 2f - drawnW / 2f + outOffsetX).roundToInt()
                            val dstTop  = (targetOutputSize / 2f - drawnH / 2f + outOffsetY).roundToInt()
                            val dstRight  = (dstLeft + drawnW).roundToInt()
                            val dstBottom = (dstTop + drawnH).roundToInt()

                            val srcRect = AndroidRect(0, 0, sourceBitmap.width, sourceBitmap.height)
                            val dstRect = AndroidRect(dstLeft, dstTop, dstRight, dstBottom)

                            val paint = AndroidPaint(AndroidPaint.FILTER_BITMAP_FLAG or AndroidPaint.ANTI_ALIAS_FLAG)
                            canvas.drawBitmap(sourceBitmap, srcRect, dstRect, paint)

                            onCropConfirmed(outputBitmap)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Text("Save & Apply", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
