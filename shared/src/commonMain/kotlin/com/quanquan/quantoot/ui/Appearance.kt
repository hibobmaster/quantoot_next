package com.quanquan.quantoot.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.quanquan.quantoot.data.safeWebUrl
import com.quanquan.quantoot.model.Account
import com.quanquan.quantoot.resources.Res
import com.quanquan.quantoot.resources.quantoot_logo
import org.jetbrains.compose.resources.painterResource

private val Day = lightColorScheme(
    primary = Color(0xFFB74029), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE4DA), onPrimaryContainer = Color(0xFF772713),
    secondary = Color(0xFF545651), onSecondary = Color.White,
    secondaryContainer = Color(0xFFECEBE6), onSecondaryContainer = Color(0xFF262723),
    background = Color(0xFFFDFCFA), onBackground = Color(0xFF1A1B19),
    surface = Color(0xFFFDFCFA), onSurface = Color(0xFF1A1B19),
    onSurfaceVariant = Color(0xFF62645F), surfaceVariant = Color(0xFFF0EFEB),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF5F4F0),
    surfaceContainer = Color(0xFFEFEEE9), surfaceContainerHigh = Color(0xFFE9E8E3),
    surfaceContainerHighest = Color(0xFFE3E2DC), surfaceTint = Color.Transparent,
    outline = Color(0xFF858680), outlineVariant = Color(0xFFDEDED7),
    inverseSurface = Color(0xFF292A26), inverseOnSurface = Color(0xFFF7F6F2),
    inversePrimary = Color(0xFFFFAA91),
)

private val Night = darkColorScheme(
    primary = Color(0xFFFF9C82), onPrimary = Color(0xFF48170B),
    primaryContainer = Color(0xFF512B23), onPrimaryContainer = Color(0xFFFFD5C8),
    secondary = Color(0xFFBFC1B9), onSecondary = Color(0xFF292B26),
    secondaryContainer = Color(0xFF30322D), onSecondaryContainer = Color(0xFFE5E6E0),
    background = Color(0xFF121311), onBackground = Color(0xFFF2F1ED),
    surface = Color(0xFF121311), onSurface = Color(0xFFF2F1ED),
    onSurfaceVariant = Color(0xFFA5A7A0), surfaceVariant = Color(0xFF272824),
    surfaceContainerLowest = Color(0xFF111210), surfaceContainerLow = Color(0xFF1B1C19),
    surfaceContainer = Color(0xFF242521), surfaceContainerHigh = Color(0xFF2B2C28),
    surfaceContainerHighest = Color(0xFF343530), surfaceTint = Color.Transparent,
    outline = Color(0xFF777A72), outlineVariant = Color(0xFF363732),
    inverseSurface = Color(0xFFE5E5DD), inverseOnSurface = Color(0xFF242520),
    inversePrimary = Color(0xFF9F341F),
)

private val type = Typography().let { base ->
    Typography(
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1.1).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.9).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.65).sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.55).sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 23.sp, letterSpacing = 0.sp),
        bodyMedium = base.bodyMedium.copy(lineHeight = 20.sp, letterSpacing = 0.sp),
        bodySmall = base.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        labelMedium = base.labelMedium.copy(letterSpacing = 0.sp),
        labelSmall = base.labelSmall.copy(letterSpacing = 0.sp),
    )
}

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(5.dp), small = RoundedCornerShape(9.dp),
    medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

@Composable
fun QuanTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Night else Day,
        typography = type, shapes = shapes, content = content)
}

@Composable
internal fun BrandMark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(Res.drawable.quantoot_logo),
        contentDescription = null,
        modifier = modifier.clip(CircleShape),
        contentScale = ContentScale.Fit,
    )
}

@Composable
internal fun AccountAvatar(account: Account?, size: Dp, modifier: Modifier = Modifier) {
    val url = account?.let { safeWebUrl(it.avatarStatic.ifBlank { it.avatar }) }
    var loaded by remember(url) { mutableStateOf(false) }
    Box(
        modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            val initial = account?.name?.firstOrNull { it.isLetterOrDigit() }?.uppercase()
            if (initial != null) {
                Text(initial, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            } else {
                Icon(Icons.Outlined.Person, null, Modifier.size(size * 0.64f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (url != null) {
            AsyncImage(
                url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                onSuccess = { loaded = true }, onError = { loaded = false },
            )
        }
    }
}
