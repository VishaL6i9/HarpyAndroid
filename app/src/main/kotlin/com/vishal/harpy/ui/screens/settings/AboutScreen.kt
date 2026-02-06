package com.vishal.harpy.ui.screens.settings

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.vishal.harpy.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val packageManager: PackageManager = context.packageManager
    val packageInfo = packageManager.getPackageInfo(context.packageName, 0)
    val versionName = packageInfo.versionName ?: BuildConfig.VERSION_NAME
    val buildType = BuildConfig.BUILD_TYPE
    var showLibrariesDialog by remember { mutableStateOf(false) }

    if (showLibrariesDialog) {
        LibrariesDialog(onDismiss = { showLibrariesDialog = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "About",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        val cs = MaterialTheme.colorScheme
        val colorPrimary = cs.primaryContainer
        val colorTertiary = cs.tertiaryContainer
        val transition = rememberInfiniteTransition(label = "gradient")
        val fraction by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 5000),
                repeatMode = RepeatMode.Reverse
            ),
            label = "gradient_fraction"
        )
        val cornerRadius = 28.dp

        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Main App Info Card with Animated Gradient
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(cornerRadius),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Box(
                    modifier = Modifier
                        .drawWithCache {
                            val cx = size.width - size.width * fraction
                            val cy = size.height * fraction

                            val gradient = Brush.radialGradient(
                                colors = listOf(colorPrimary, colorTertiary),
                                center = Offset(cx, cy),
                                radius = 800f
                            )

                            onDrawBehind {
                                drawRoundRect(
                                    brush = gradient,
                                    cornerRadius = CornerRadius(
                                        cornerRadius.toPx(),
                                        cornerRadius.toPx()
                                    )
                                )
                            }
                        }
                        .padding(16.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Filled.Security,
                                    contentDescription = "App Icon",
                                    modifier = Modifier.size(48.dp),
                                    tint = cs.onPrimaryContainer
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Harpy",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = cs.onPrimaryContainer
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "v$versionName $buildType",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = cs.onPrimaryContainer.copy(alpha = 0.85f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            val btnContainer = cs.primary
                            val btnContent = cs.onPrimary

                            Button(
                                onClick = { showLibrariesDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = btnContainer,
                                    contentColor = btnContent
                                )
                            ) {
                                Text(
                                    text = "Libraries",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Button(
                                onClick = {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            "https://github.com/VishaL6i9/HarpyAndroid".toUri()
                                        )
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = btnContainer,
                                    contentColor = btnContent
                                )
                            ) {
                                Text(
                                    text = "GitHub",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    clipboardManager.setText(AnnotatedString(collectDeviceInfo(context)))
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = "Device Info",
                                    modifier = Modifier.size(20.dp),
                                    tint = cs.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Device Info",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = cs.onPrimaryContainer
                                )
                            }
                            Text(
                                text = collectDeviceInfo(context),
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Donate Section
            Text(
                text = "Donate",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                // UPI
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            clipboardManager.setText(
                                AnnotatedString("VishaL6i9@slc")
                            )
                            android.widget.Toast
                                .makeText(context, "UPI ID copied to clipboard", android.widget.Toast.LENGTH_SHORT)
                                .show()
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Filled.CurrencyRupee,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "UPI",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "VishaL6i9@slc",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun collectDeviceInfo(context: android.content.Context): String {
    return buildString {
        appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        appendLine("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        appendLine("ABI: ${android.os.Build.SUPPORTED_ABIS.joinToString(", ")}")
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            appendLine("App Version: ${packageInfo.versionName}")
        } catch (e: Exception) {
            appendLine("App Version: Unknown")
        }
    }
}

@Composable
private fun LibrariesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Open Source Libraries",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LibraryItem(
                    name = "Jetpack Compose",
                    description = "Modern toolkit for building native Android UI",
                    license = "Apache 2.0",
                    url = "https://developer.android.com/jetpack/compose"
                )
                
                LibraryItem(
                    name = "Material 3",
                    description = "Material Design 3 components for Compose",
                    license = "Apache 2.0",
                    url = "https://m3.material.io/"
                )
                
                LibraryItem(
                    name = "Hilt",
                    description = "Dependency injection library for Android",
                    license = "Apache 2.0",
                    url = "https://dagger.dev/hilt/"
                )
                
                LibraryItem(
                    name = "Kotlin Coroutines",
                    description = "Library support for Kotlin coroutines",
                    license = "Apache 2.0",
                    url = "https://github.com/Kotlin/kotlinx.coroutines"
                )
                
                LibraryItem(
                    name = "AndroidX Libraries",
                    description = "Core Android libraries and components",
                    license = "Apache 2.0",
                    url = "https://developer.android.com/jetpack/androidx"
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun LibraryItem(
    name: String,
    description: String,
    license: String,
    url: String
) {
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, url.toUri())
                )
            }
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "License: $license",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}
