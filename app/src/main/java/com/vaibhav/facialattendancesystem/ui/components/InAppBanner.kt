package com.vaibhav.facialattendancesystem.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaibhav.facialattendancesystem.ui.theme.ErrorRose
import com.vaibhav.facialattendancesystem.ui.theme.PrimaryCyan
import com.vaibhav.facialattendancesystem.ui.theme.SuccessGreen
import kotlinx.coroutines.delay

/**
 * Encapsulates an in-app notification message.
 */
data class BannerData(
    val message: String,
    val isError: Boolean = false,
    val id: Long = System.currentTimeMillis()
)

/**
 * CompositionLocal providing a helper function to show in-app banners from any composable.
 */
val LocalBannerManager = staticCompositionLocalOf<(String, Boolean) -> Unit> {
    { _, _ -> }
}

/**
 * Modern floating top in-app banner with slide-down/fade animation.
 * Positions notifications gracefully at the top of the screen below status bars,
 * preventing any overlap with bottom FABs, action bars, or navigation controls.
 * Adapts high-contrast visual styling seamlessly across both light and dark themes.
 */
@Composable
fun InAppBanner(
    bannerData: BannerData?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(bannerData?.id) {
        if (bannerData != null) {
            delay(3400)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = bannerData != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        bannerData?.let { data ->
            val isDark = isAppDarkTheme()
            val accentColor = if (data.isError) ErrorRose else SuccessGreen
            val bgColor = if (isDark) {
                if (data.isError) Color(0xFF221115) else Color(0xFF0D2218)
            } else {
                if (data.isError) Color(0xFFFFF1F2) else Color(0xFFF0FDF4)
            }
            val textColor = if (isDark) {
                Color.White
            } else {
                if (data.isError) Color(0xFF9F1239) else Color(0xFF166534)
            }
            val borderColor = accentColor.copy(alpha = if (isDark) 0.65f else 0.45f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = bgColor,
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, borderColor, RoundedCornerShape(18.dp))
                        .clickable { onDismiss() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = if (isDark) 0.25f else 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (data.isError) "✕" else "✓",
                                color = accentColor,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Text(
                            text = data.message,
                            color = textColor,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
