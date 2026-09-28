package com.vaibhav.facialattendancesystem.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaibhav.facialattendancesystem.ui.theme.DarkBackground
import com.vaibhav.facialattendancesystem.ui.theme.ErrorRose
import com.vaibhav.facialattendancesystem.ui.theme.PrimaryCyan
import com.vaibhav.facialattendancesystem.ui.theme.SuccessGreen
import com.vaibhav.facialattendancesystem.ui.theme.WarningAmber

// ─────────────────────────────────────────────────────────────────
// Design Tokens
// ─────────────────────────────────────────────────────────────────

object AppShapes {
    val CardRadius   = 16.dp
    val ButtonRadius = 12.dp
    val PillRadius   = 999.dp
}

object AppSpacing {
    val ScreenPadding = 16.dp
    val CardGap       = 16.dp
    val SectionGap    = 20.dp
    val ItemGap       = 12.dp
}

// ─────────────────────────────────────────────────────────────────
// Adaptive Theme Helpers
// ─────────────────────────────────────────────────────────────────

@Composable
fun isAppDarkTheme(): Boolean =
    MaterialTheme.colorScheme.background == DarkBackground

@Composable
fun adaptiveBorderColor(): Color =
    if (isAppDarkTheme()) Color.White.copy(alpha = 0.10f) else Color(0x3864748B)

@Composable
fun adaptiveDividerColor(): Color =
    if (isAppDarkTheme()) Color.White.copy(alpha = 0.08f) else Color(0xFFE2E8F0)

// ─────────────────────────────────────────────────────────────────
// Glassmorphism Card Modifier
// ─────────────────────────────────────────────────────────────────

fun Modifier.glassCard(surface: Color, borderColor: Color = Color.White.copy(alpha = 0.08f)): Modifier =
    this
        .shadow(elevation = 6.dp, shape = RoundedCornerShape(AppShapes.CardRadius), clip = false)
        .clip(RoundedCornerShape(AppShapes.CardRadius))
        .background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    surface.copy(alpha = 0.95f),
                    surface.copy(alpha = 0.85f)
                )
            )
        )
        .border(1.dp, borderColor, RoundedCornerShape(AppShapes.CardRadius))

// ─────────────────────────────────────────────────────────────────
// StatusPill — Reusable color-coded pill badge
// ─────────────────────────────────────────────────────────────────

@Composable
fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(AppShapes.PillRadius),
        color = color.copy(alpha = 0.15f),
        modifier = modifier
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────
// ConfidenceBadge — 3-tier color law: ≥60% green / 50-60% amber / <50% red
// ─────────────────────────────────────────────────────────────────

@Composable
fun ConfidenceBadge(name: String, confidencePct: Int) {
    val color = when {
        confidencePct >= 60 -> SuccessGreen
        confidencePct >= 50 -> WarningAmber
        else                -> ErrorRose
    }
    StatusPill(text = "$name  $confidencePct%", color = color)
}

// ─────────────────────────────────────────────────────────────────
// AttendanceProgressBar — animated, 3-tier color coded
// ─────────────────────────────────────────────────────────────────

@Composable
fun AttendanceProgressBar(percent: Float) {
    val color = when {
        percent <= 0f    -> MaterialTheme.colorScheme.outlineVariant
        percent >= 0.75f -> SuccessGreen
        percent >= 0.50f -> WarningAmber
        else             -> ErrorRose
    }
    val animated by animateFloatAsState(
        targetValue = percent,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "progress"
    )
    LinearProgressIndicator(
        progress = { animated },
        color = color,
        trackColor = color.copy(alpha = 0.15f),
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
    )
}

// ─────────────────────────────────────────────────────────────────
// CameraPermissionFallbackCard & Settings Helper
// ─────────────────────────────────────────────────────────────────

fun openAppSettings(context: Context) {
    try {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Composable
fun CameraPermissionFallbackCard(
    modifier: Modifier = Modifier,
    title: String = "Camera Permission Required",
    description: String = "Swiff Mark requires access to your camera to detect faces and mark attendance.",
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, adaptiveBorderColor())
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(PrimaryCyan.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text("📷", fontSize = 32.sp)
            }

            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = description,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
            ) {
                Text("GRANT CAMERA ACCESS", fontWeight = FontWeight.Bold, color = Color.White)
            }

            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("OPEN APP SETTINGS", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

