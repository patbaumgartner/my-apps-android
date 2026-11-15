package com.patbaumgartner.android.myapps.ui.components

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.patbaumgartner.android.myapps.ui.theme.BrandCyan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Branded letter tile shown when the app is not installed (no icon available yet). */
@Composable
fun LetterIcon(letter: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(BrandCyan),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/**
 * Loads the installed app's launcher icon off the main thread and renders it; falls back
 * to a letter tile while loading or when the package is absent.
 */
@Composable
fun InstalledAppIcon(
    packageId: String,
    fallbackLetter: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var icon by remember(packageId) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(packageId) {
        icon = withContext(Dispatchers.IO) {
            runCatching {
                val drawable: Drawable = context.packageManager.getApplicationIcon(packageId)
                drawable.toBitmap(96, 96).asImageBitmap()
            }.getOrNull()
        }
    }
    if (icon != null) {
        Image(
            bitmap = icon!!,
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Fit,
        )
    } else {
        LetterIcon(fallbackLetter, modifier.size(40.dp))
    }
}

