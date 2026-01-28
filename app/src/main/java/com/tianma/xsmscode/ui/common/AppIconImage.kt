package com.tianma.xsmscode.ui.common

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import android.content.pm.PackageManager
import com.tianma.xsmscode.common.utils.XLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val labelToPackageCache = java.util.concurrent.ConcurrentHashMap<String, String>()

/**
 * A reusable component to load and display application icons from package names.
 */
@Composable
fun AppIconImage(
    packageName: String?,
    label: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    contentDescription: String? = null
) {
    val context = LocalContext.current
    var iconBitmap by remember(packageName, label) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    
    LaunchedEffect(packageName, label) {
        withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                var finalPackageName = packageName
                
                // Fallback to label search if package name is missing
                if (finalPackageName.isNullOrBlank() && !label.isNullOrBlank()) {
                    finalPackageName = labelToPackageCache[label]
                    if (finalPackageName == null) {
                        val installedApps = pm.getInstalledApplications(PackageManager.MATCH_ALL)
                        for (app in installedApps) {
                            if (pm.getApplicationLabel(app).toString() == label) {
                                finalPackageName = app.packageName
                                labelToPackageCache[label] = finalPackageName
                                break
                            }
                        }
                    }
                }

                if (!finalPackageName.isNullOrBlank()) {
                    val appInfo = pm.getApplicationInfo(finalPackageName, 0)
                    val drawable = appInfo.loadIcon(pm)
                    iconBitmap = drawable.toBitmap().asImageBitmap()
                } else {
                    iconBitmap = null
                }
            } catch (e: Exception) {
                // Silently fail for individual icons to avoid log flooding, but log at debug
                if (com.github.tianma8023.xposed.smscode.BuildConfig.DEBUG) {
                    XLog.e("Failed to load icon for pkg=$packageName, label=$label", e)
                }
                iconBitmap = null
            }
        }
    }

    Box(modifier = modifier.size(size)) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap!!,
                contentDescription = contentDescription,
                modifier = Modifier
                    .size(size)
                    .clip(MaterialTheme.shapes.small)
            )
        } else {
            Icon(
                imageVector = Icons.Default.Android,
                contentDescription = contentDescription,
                modifier = Modifier.size(size),
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}
