package com.eyeguardian.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AppOpsManager
import android.app.DatePickerDialog
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview as CameraPreviewUseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.util.Calendar
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.eyeguardian.app.ui.theme.EyeGuardTheme
import com.eyeguardian.app.ui.theme.appColor
import com.eyeguardian.app.ui.theme.glassBorder
import com.eyeguardian.app.ui.theme.LocalEyeGuardDark
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LocalCrashLogger.install(applicationContext)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val themeStore = remember(context) { EyeGuardianStore(context) }
            var darkTheme by remember { mutableStateOf(themeStore.isDarkTheme()) }
            EyeGuardTheme(darkTheme = darkTheme) {
                SideEffect {
                    val barColor = if (darkTheme) Color(0xFF0A1320) else Color(0xFFE7DED2)
                    window.statusBarColor = barColor.toArgb()
                    window.navigationBarColor = barColor.toArgb()
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !darkTheme
                        isAppearanceLightNavigationBars = !darkTheme
                    }
                }
                EyeGuardianApp(
                    isDarkTheme = darkTheme,
                    onThemeChanged = { enabled ->
                        darkTheme = enabled
                        themeStore.setDarkTheme(enabled)
                    }
                )
            }
        }
    }
}

@Composable
private fun EyeGuardianApp(
    isDarkTheme: Boolean,
    onThemeChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val store = remember(context) { EyeGuardianStore(context) }
    var onboardingComplete by remember { mutableStateOf(store.isOnboardingComplete()) }
    var showDistanceLab by remember { mutableStateOf(false) }
    var showUsageScreen by remember { mutableStateOf(false) }
    var showGame by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    BackHandler(enabled = showGame || showSettings || showDistanceLab || showUsageScreen) {
        when {
            showGame -> showGame = false
            showSettings -> showSettings = false
            showDistanceLab -> showDistanceLab = false
            showUsageScreen -> showUsageScreen = false
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        if (!onboardingComplete) {
            EyeGuardianOnboarding(
                modifier = Modifier.padding(innerPadding),
                onComplete = {
                    store.setOnboardingComplete(true)
                    onboardingComplete = true
                }
            )
            return@Scaffold
        }
        if (showGame) {
            FocusTrailGameScreen(
                modifier = Modifier.padding(innerPadding),
                onBack = { showGame = false }
            )
        } else if (showSettings) {
            SettingsScreen(
                modifier = Modifier.padding(innerPadding),
                onBack = { showSettings = false },
                isDarkTheme = isDarkTheme,
                onThemeChanged = onThemeChanged
            )
        } else if (showDistanceLab) {
            DistanceLabScreen(
                modifier = Modifier.padding(innerPadding),
                onBack = { showDistanceLab = false }
            )
        } else if (showUsageScreen) {
            UsageAccessScreen(
                modifier = Modifier.padding(innerPadding),
                onBack = { showUsageScreen = false },
                onOpenGame = { showGame = true }
            )
        } else {
            EyeGuardHome(
                modifier = Modifier.padding(innerPadding),
                onStartMonitoring = { showDistanceLab = true },
                onOpenUsage = { showUsageScreen = true },
                onOpenSettings = { showSettings = true }
            )
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun EyeGuardianOnboarding(
    modifier: Modifier = Modifier,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var step by remember { mutableStateOf(0) }
    var introShown by remember { mutableStateOf(false) }
    LaunchedEffect(step) {
        introShown = false
        if (step == 0) {
            kotlinx.coroutines.delay(120L)
        }
        introShown = true
    }
    val introLift by animateDpAsState(
        targetValue = if (introShown) 0.dp else 24.dp,
        animationSpec = tween(650),
        label = "intro_lift"
    )
    val introAlpha by animateFloatAsState(
        targetValue = if (introShown) 1f else 0.05f,
        animationSpec = tween(650),
        label = "intro_alpha"
    )
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var notificationGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var usageGranted by remember { mutableStateOf(usageAccessGranted(context)) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { cameraGranted = it }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { notificationGranted = it }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                cameraGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                notificationGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                usageGranted = usageAccessGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(appColor(0xFF14263B), appColor(0xFF263B72), appColor(0xFF7B4A80))
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (slideInHorizontally(animationSpec = tween(280)) + fadeIn(tween(280))) togetherWith
                    (slideOutHorizontally(animationSpec = tween(280)) + fadeOut(tween(220)))
            },
            label = "onboarding_step"
        ) { page ->
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(com.eyeguardian.app.R.drawable.eye_guard_logo),
                        contentDescription = "Eye Guardian logo",
                        modifier = Modifier
                            .size(if (page == 0) 180.dp else 104.dp)
                            .clip(RoundedCornerShape(36.dp))
                            .offset(y = if (page == 0) introLift else 0.dp)
                            .graphicsLayer { alpha = if (page == 0) introAlpha else 1f }
                    )
                }
                when (page) {
                    0 -> {
                        Text(
                            "A calmer way to look after your eyes",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White
                        )
                        Text(
                            "Welcome to Eye Guardian",
                            style = MaterialTheme.typography.titleLarge,
                            color = appColor(0xFF9BEFC6)
                        )
                        Card(
                            border = BorderStroke(1.dp, Color.White.copy(alpha = .20f)),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White.copy(alpha = .12f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    "Gentle distance reminders, useful screen insights, and small breaks that fit your day.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = appColor(0xFFF0F5FF)
                                )
                                Text(
                                    "Private by design · processed on this device",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = appColor(0xFFD6E4FF)
                                )
                            }
                        }
                    }
                    1 -> OnboardingPermissionCard(
                        title = "Step 1 · Camera distance protection",
                        description = "The front camera checks face distance on-device. Frames are analyzed and discarded; photos and videos are not saved.",
                        status = if (cameraGranted) "Ready" else "Not granted",
                        statusColor = if (cameraGranted) appColor(0xFF7FF0B8) else appColor(0xFFFFD166),
                        actionLabel = if (cameraGranted) "Camera ready" else "Allow camera",
                        onAction = { if (!cameraGranted) cameraLauncher.launch(Manifest.permission.CAMERA) }
                    )
                    2 -> OnboardingPermissionCard(
                        title = "Step 2 · Notifications",
                        description = "Notifications let Eye Guardian alert you while you are using another app, with friendly actions such as Start break and Snooze.",
                        status = if (notificationGranted) "Ready" else "Not granted",
                        statusColor = if (notificationGranted) appColor(0xFF7FF0B8) else appColor(0xFFFFD166),
                        actionLabel = if (notificationGranted) "Notifications ready" else "Allow notifications",
                        onAction = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationGranted) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    )
                    else -> {
                        OnboardingPermissionCard(
                            title = "Step 3 · Activity insights",
                            description = "Usage access provides aggregated foreground-app time only. Eye Guardian cannot read messages, page contents, or what you do inside apps.",
                            status = if (usageGranted) "Ready" else "Not granted",
                            statusColor = if (usageGranted) appColor(0xFF7FF0B8) else appColor(0xFFFFD166),
                            actionLabel = if (usageGranted) "Usage access ready" else "Open usage access",
                            onAction = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                        )
                        if (!usageGranted) {
                            Text(
                                "Some Android versions protect this special permission. If Android blocks the toggle, open App info → ⋮ → Allow restricted settings, then return and enable usage access.",
                                style = MaterialTheme.typography.bodySmall,
                                color = appColor(0xFFD4DDF5)
                            )
                            OutlinedButton(
                                onClick = {
                                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    })
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) { Text("Open Eye Guardian app info") }
                        }
                    }
                }
                Text(
                    "You can change permissions later in Android Settings. You may continue without granting them now.",
                    style = MaterialTheme.typography.bodySmall,
                    color = appColor(0xFFD4DDF5)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (page > 0) OutlinedButton(onClick = { step-- }, modifier = Modifier.weight(1f)) { Text("Back") }
                    Button(
                        onClick = { if (page == 3) onComplete() else step++ },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = appColor(0xFF78E4B0),
                            contentColor = appColor(0xFF113A2A)
                        )
                    ) { Text(if (page == 3) "Enter Eye Guardian" else "Next") }
                }
            }
        }
    }
}

@Composable
private fun OnboardingPermissionCard(
    title: String,
    description: String,
    status: String,
    statusColor: Color,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
            border = glassBorder(),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .14f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(status, style = MaterialTheme.typography.labelMedium, color = statusColor)
            }
            Text(description, style = MaterialTheme.typography.bodyMedium, color = appColor(0xFFE4EBFF))
            OutlinedButton(
                onClick = onAction,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) { Text(actionLabel) }
        }
    }
}

@Composable
private fun EyeGuardHome(
    modifier: Modifier = Modifier,
    onStartMonitoring: () -> Unit = {},
    onOpenUsage: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val breakStore = remember(context) { EyeGuardianStore(context) }
    var isBreakStarted by remember { mutableStateOf(breakStore.isManualBreakActive()) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, breakStore) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isBreakStarted = breakStore.isManualBreakActive()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(appColor(0xFFF1F6F3), appColor(0xFFEAF1FF), appColor(0xFFFFEEF5))
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Eye Guardian",
                    style = MaterialTheme.typography.headlineMedium,
                    color = appColor(0xFF123B2A)
                )
                Text(
                    text = "A calmer screen day starts here",
                    style = MaterialTheme.typography.bodyMedium,
                    color = appColor(0xFF64736B)
                )
            }
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.semantics {
                    contentDescription = "Open Eye Guardian settings"
                    role = Role.Button
                },
                shape = RoundedCornerShape(16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 15.dp,
                    vertical = 10.dp
                )
            ) {
                Text(text = "⚙", style = MaterialTheme.typography.titleMedium)
            }
        }

        Card(
            border = glassBorder(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = appColor(0xFF1F6045))
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Your eyes deserve a reset",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                Text(
                    text = "Protect your viewing distance, understand your patterns, and build better breaks.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = appColor(0xFFD8F0E1)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HomePill(label = "On-device", color = appColor(0xFF377D5B))
                    HomePill(label = "Private", color = appColor(0xFF377D5B))
                }
            }
        }

        Text(
            text = "Your toolkit",
            style = MaterialTheme.typography.titleLarge,
            color = appColor(0xFF123B2A),
            modifier = Modifier.padding(top = 4.dp)
        )

        DistanceCard(onStartMonitoring = onStartMonitoring)

        UsageCard(onOpenUsage = onOpenUsage)

        Card(
            border = glassBorder(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = appColor(0xFFFFF9ED))
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DashboardBadge(kind = "reset")
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "20-minute reset",
                            style = MaterialTheme.typography.titleMedium,
                            color = appColor(0xFF493719)
                        )
                        Text(
                            text = "A tiny pause for your eyes",
                            style = MaterialTheme.typography.bodySmall,
                            color = appColor(0xFF8A7656)
                        )
                    }
                }
                Text(
                    text = if (isBreakStarted) {
                        "Your break has started. Look across the room and let your eyes relax."
                    } else {
                        "Look at something far away, breathe, and come back refreshed."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = appColor(0xFF6E604B)
                )
                Button(
                    onClick = {
                        if (isBreakStarted) {
                            val elapsedMillis = System.currentTimeMillis() - breakStore.getManualBreakStartedAt()
                            BreakTimerReceiver.stop(context)
                            isBreakStarted = false
                            Toast.makeText(
                                context,
                                if (elapsedMillis >= MANUAL_BREAK_BLOCK_MILLIS) {
                                    "Break stopped · reward calculated"
                                } else {
                                    "Break stopped early · no XP yet"
                                },
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            BreakTimerReceiver.start(context)
                            isBreakStarted = true
                            Toast.makeText(context, "20-minute break started", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = appColor(0xFFE39A3B),
                        contentColor = Color.White
                    )
                ) {
                    Text(if (isBreakStarted) "Stop break" else "Start test break")
                }
            }
        }

        ProgressCard()

        Text(
            text = "Your camera analysis stays on-device. Usage insights use aggregated app time only.",
            style = MaterialTheme.typography.bodySmall,
            color = appColor(0xFF6B7971),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun HomePill(label: String, color: Color) {
    Text(
        text = "•  $label",
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

@Composable
private fun DashboardBadge(kind: String) {
    val badgeBrush = when (kind) {
        "eye" -> Brush.linearGradient(listOf(appColor(0xFF40C9A2), appColor(0xFF2784D8)))
        "chart" -> Brush.linearGradient(listOf(appColor(0xFFFF9A62), appColor(0xFFE85D9E)))
        else -> Brush.linearGradient(listOf(appColor(0xFFFFC86B), appColor(0xFFE778A5)))
    }
    Canvas(modifier = Modifier.size(52.dp)) {
        val corner = 16.dp.toPx()
        drawRoundRect(
            brush = badgeBrush,
            cornerRadius = CornerRadius(corner, corner)
        )
        val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        when (kind) {
            "eye" -> {
                drawOval(
                    color = Color.White,
                    topLeft = Offset(size.width * .22f, size.height * .35f),
                    size = Size(size.width * .56f, size.height * .30f),
                    style = stroke
                )
                drawCircle(
                    color = Color.White,
                    radius = 5.dp.toPx(),
                    center = Offset(size.width / 2f, size.height / 2f)
                )
            }
            "chart" -> {
                val chartPath = Path().apply {
                    moveTo(size.width * .18f, size.height * .68f)
                    lineTo(size.width * .38f, size.height * .48f)
                    lineTo(size.width * .55f, size.height * .57f)
                    lineTo(size.width * .78f, size.height * .27f)
                }
                drawPath(chartPath, color = Color.White, style = stroke)
                listOf(
                    Offset(size.width * .18f, size.height * .68f),
                    Offset(size.width * .38f, size.height * .48f),
                    Offset(size.width * .55f, size.height * .57f),
                    Offset(size.width * .78f, size.height * .27f)
                ).forEach { point ->
                    drawCircle(Color.White, 3.dp.toPx(), point)
                }
            }
            else -> {
                drawOval(
                    color = Color.White,
                    topLeft = Offset(size.width * .32f, size.height * .20f),
                    size = Size(size.width * .34f, size.height * .56f),
                    style = stroke
                )
                drawLine(
                    color = Color.White,
                    start = Offset(size.width * .50f, size.height * .28f),
                    end = Offset(size.width * .50f, size.height * .72f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun DistanceCard(onStartMonitoring: () -> Unit) {
    Card(
            border = glassBorder(),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = appColor(0xFFE1F2E8))
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DashboardBadge(kind = "eye")
                Spacer(modifier = Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Distance protection",
                        style = MaterialTheme.typography.titleMedium,
                        color = appColor(0xFF123B2A)
                    )
                    Text(
                        text = "Ready when you are",
                        style = MaterialTheme.typography.bodyMedium,
                        color = appColor(0xFF2E7D55)
                    )
                }
            }
            Text(
                text = "Uses the front camera to detect whether your face is getting too close. Frames are analyzed on-device and not saved.",
                style = MaterialTheme.typography.bodyMedium,
                color = appColor(0xFF587066)
            )
            Button(
                onClick = onStartMonitoring,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = appColor(0xFF2E7D55),
                    contentColor = Color.White
                )
            ) {
                Text("Open distance lab")
            }
        }
    }
}

@Composable
private fun UsageCard(onOpenUsage: () -> Unit) {
    Card(
            border = glassBorder(),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = appColor(0xFFFFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DashboardBadge(kind = "chart")
                Spacer(modifier = Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Activity insights",
                        style = MaterialTheme.typography.titleMedium,
                        color = appColor(0xFF123B2A)
                    )
                    Text(
                        text = "Notice your screen patterns",
                        style = MaterialTheme.typography.bodyMedium,
                        color = appColor(0xFF64736B)
                    )
                }
            }
            Text(
                text = "See your app time, sessions, trends, and the habits that shape your day. Android requires usage access in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = appColor(0xFF64736B)
            )
            Button(
                onClick = onOpenUsage,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = appColor(0xFF2E7D55),
                    contentColor = Color.White
                )
            ) {
                Text("Open activity usage")
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    isDarkTheme: Boolean,
    onThemeChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val historyStore = remember(context) { EyeGuardianStore(context) }
    var twentyTwentyEnabled by remember { mutableStateOf(historyStore.isTwentyTwentyEnabled()) }
    var intervalMinutes by remember { mutableStateOf(historyStore.getTwentyTwentyIntervalMinutes()) }
    var breakEngineEnabled by remember { mutableStateOf(historyStore.isBreakEngineEnabled()) }
    var breakIntervalMinutes by remember { mutableStateOf(historyStore.getBreakIntervalMinutes()) }
    var hasUsageAccess by remember { mutableStateOf(usageAccessGranted(context)) }
    var profileName by remember { mutableStateOf(historyStore.getProfileName()) }
    var profileAge by remember { mutableStateOf(historyStore.getProfileAge()?.toString() ?: "") }
    var isEditingProfile by remember {
        mutableStateOf(historyStore.getProfileName().isBlank() && historyStore.getProfileAge() == null)
    }
    val installDateMillis = remember { historyStore.getInstallDateMillis() }
    val firstReportDay = startOfDayMillisFromTimestamp(installDateMillis)
    var reportStartMillis by remember {
        mutableStateOf(startOfDayMillis(7).coerceAtLeast(firstReportDay))
    }
    var reportEndMillis by remember { mutableStateOf(startOfDayMillis(0).coerceAtLeast(firstReportDay)) }
    var crashLogCount by remember { mutableStateOf(LocalCrashLogger.count(context)) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    var notificationSoundUri by remember { mutableStateOf(historyStore.getNotificationSoundUri()) }
    var openingChannelId by remember { mutableStateOf<String?>(null) }
    var batteryOptimizationIgnored by remember {
        mutableStateOf(
            context.getSystemService(PowerManager::class.java)
                ?.isIgnoringBatteryOptimizations(context.packageName) == true
        )
    }
    val intervalOptions = listOf(1L, 5L, 10L, 20L, 30L, 45L, 60L)
    val breakIntervalOptions = listOf(1L, 15L, 20L, 30L, 45L, 60L, 90L)

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsageAccess = usageAccessGranted(context)
                openingChannelId = null
                batteryOptimizationIgnored = context
                    .getSystemService(PowerManager::class.java)
                    ?.isIgnoringBatteryOptimizations(context.packageName) == true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            val report = buildReportBundle(
                context,
                historyStore,
                profileName,
                profileAge,
                reportStartMillis,
                reportEndMillis
            )
            writeReportAsPdf(context, uri, report)
            Toast.makeText(context, "PDF report saved", Toast.LENGTH_SHORT).show()
        }
    }
    val jpegLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/jpeg")
    ) { uri ->
        if (uri != null) {
            val report = buildReportBundle(
                context,
                historyStore,
                profileName,
                profileAge,
                reportStartMillis,
                reportEndMillis
            )
            writeReportAsJpeg(context, uri, report)
            Toast.makeText(context, "JPEG report saved", Toast.LENGTH_SHORT).show()
        }
    }
    val ringtoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.getParcelableExtra<Uri>(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            ?.let { uri ->
                notificationSoundUri = uri.toString()
                historyStore.setNotificationSoundUri(notificationSoundUri)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val notificationManager = context.getSystemService(android.app.NotificationManager::class.java)
                    notificationManager.deleteNotificationChannel(DistanceMonitoringService.TWENTY_TWENTY_CHANNEL_ID)
                    notificationManager.deleteNotificationChannel(DistanceMonitoringService.BREAK_CHANNEL_ID)
                    notificationManager.deleteNotificationChannel(DistanceMonitoringService.ALERT_CHANNEL_ID)
                    DistanceMonitoringService.ensureNotificationChannels(context)
                }
                if (DistanceMonitoringService.isRunning) {
                    context.startService(
                        Intent(context, DistanceMonitoringService::class.java).apply {
                            action = DistanceMonitoringService.ACTION_REFRESH_SETTINGS
                            putExtra(DistanceMonitoringService.EXTRA_SOUND_CHANGED, true)
                        }
                    )
                }
                Toast.makeText(context, "Notification sound updated", Toast.LENGTH_SHORT).show()
            }
    }

    fun refreshMonitoringSettings(soundChanged: Boolean = false) {
        if (DistanceMonitoringService.isRunning) {
            context.startService(
                Intent(context, DistanceMonitoringService::class.java).apply {
                    action = DistanceMonitoringService.ACTION_REFRESH_SETTINGS
                    putExtra(DistanceMonitoringService.EXTRA_SOUND_CHANGED, soundChanged)
                }
            )
        }
    }

    fun openNotificationChannel(channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            openingChannelId = channelId
            DistanceMonitoringService.ensureNotificationChannels(context)
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
                    }
                )
            }.onFailure {
                openingChannelId = null
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                )
            }
        }
    }

    fun pickDate(currentMillis: Long, onSelected: (Long) -> Unit) {
        val calendar = Calendar.getInstance().apply { timeInMillis = currentMillis }
        DatePickerDialog(
            android.view.ContextThemeWrapper(
                context,
                if (isDarkTheme) android.R.style.Theme_DeviceDefault_Dialog_Alert
                else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
            ),
            { _, year, month, day -> onSelected(dayStartMillis(year, month, day)) },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).also { dialog ->
            dialog.datePicker.minDate = firstReportDay
            dialog.datePicker.maxDate = startOfDayMillis(0) + 24L * 60L * MINUTE_MILLIS - 1L
            dialog.show()
        }
    }

    fun chooseNotificationSound() {
        ringtoneLauncher.launch(
            Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(
                    android.media.RingtoneManager.EXTRA_RINGTONE_TYPE,
                    android.media.RingtoneManager.TYPE_NOTIFICATION
                )
                putExtra(
                    android.media.RingtoneManager.EXTRA_RINGTONE_TITLE,
                    "Choose Eye Guardian reminder sound"
                )
                putExtra(
                    android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                    notificationSoundUri?.let(Uri::parse)
                )
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(appColor(0xFFF4F0FF), appColor(0xFFE9F5FF), appColor(0xFFFFF1F5))
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                color = appColor(0xFF163B2A)
            )
        }

        Text(
            text = "Personalize how Eye Guardian reminds you to care for your eyes. Changes apply the next time background protection is started, or immediately if it is already active.",
            style = MaterialTheme.typography.bodyMedium,
            color = appColor(0xFF5F6D65)
        )

        if (isEditingProfile) {
            Card(
                border = glassBorder(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = appColor(0xFFE9E4FF).copy(alpha = .82f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (profileName.isBlank() && profileAge.isBlank()) "Create your profile" else "Edit your profile",
                        style = MaterialTheme.typography.titleLarge,
                        color = appColor(0xFF342B67)
                    )
                    Text(
                        text = "Optional details personalize your reports. They stay on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = appColor(0xFF665E87)
                    )
                    OutlinedTextField(
                        value = profileName,
                        onValueChange = { profileName = it.take(60) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = profileAge,
                        onValueChange = { value -> profileAge = value.filter(Char::isDigit).take(3) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Age (optional)") },
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            historyStore.setProfileName(profileName)
                            historyStore.setProfileAge(profileAge.toIntOrNull())
                            isEditingProfile = false
                            Toast.makeText(context, "Profile saved on this device", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = appColor(0xFF6654C7),
                            contentColor = Color.White
                        )
                    ) {
                        Text("Save profile")
                    }
                }
            }
        } else {
            Card(
                border = glassBorder(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = appColor(0xFFE9E4FF).copy(alpha = .82f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = profileName.ifBlank { "Eye Guardian user" },
                            style = MaterialTheme.typography.titleLarge,
                            color = appColor(0xFF342B67)
                        )
                        Text(
                            text = if (profileAge.isBlank()) "Age not provided" else "Age $profileAge",
                            style = MaterialTheme.typography.bodyMedium,
                            color = appColor(0xFF665E87)
                        )
                        Text(
                            text = "Profile details stay on this device",
                            style = MaterialTheme.typography.bodySmall,
                            color = appColor(0xFF665E87)
                        )
                    }
                    OutlinedButton(onClick = { isEditingProfile = true }) {
                        Text("Edit")
                    }
                }
            }
        }

        Card(
            border = glassBorder(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = appColor(0xFFEAF4EE).copy(alpha = .88f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Appearance",
                        style = MaterialTheme.typography.titleMedium,
                        color = appColor(0xFF163B2A)
                    )
                    Text(
                        text = if (isDarkTheme) "Dark glass mode: charcoal surfaces with soft green highlights."
                        else "Light mode: the warm matcha and cream palette is active.",
                        style = MaterialTheme.typography.bodySmall,
                        color = appColor(0xFF5F6D65)
                    )
                }
                Switch(
                    checked = isDarkTheme,
                    onCheckedChange = onThemeChanged,
                    modifier = Modifier.semantics {
                        contentDescription = "Use dark glass theme"
                    }
                )
            }
        }

        Card(
            border = glassBorder(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = appColor(0xFFFFF0D8).copy(alpha = .90f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Background reliability",
                    style = MaterialTheme.typography.titleMedium,
                    color = appColor(0xFF654719)
                )
                Text(
                    text = "Battery optimization can stop camera protection while another app is open. Allowing Eye Guardian to run in the background improves reliability, but you can leave this unchanged.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = appColor(0xFF806D4B)
                )
                Text(
                    text = if (batteryOptimizationIgnored) "Battery optimization is already disabled for Eye Guardian."
                    else "Battery optimization is currently enabled for Eye Guardian.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (batteryOptimizationIgnored) appColor(0xFF2E7D55) else appColor(0xFF8A6500)
                )
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                            )
                        }
                    },
                    enabled = !batteryOptimizationIgnored
                ) {
                    Text(if (batteryOptimizationIgnored) "Background access ready" else "Improve background reliability")
                }
                Text(
                    text = "After a reboot, Android may still require you to open Eye Guardian once before camera protection can resume.",
                    style = MaterialTheme.typography.bodySmall,
                    color = appColor(0xFF806D4B)
                )
            }
        }

        Card(
            border = glassBorder(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = appColor(0xFFE0F2FF).copy(alpha = .86f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Personal activity report",
                    style = MaterialTheme.typography.titleLarge,
                    color = appColor(0xFF164A70)
                )
                Text(
                    text = "Export locally saved usage, distance warnings, breaks, resets, and game activity for a selected period.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = appColor(0xFF4A6C82)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { pickDate(reportStartMillis) { reportStartMillis = it.coerceAtMost(reportEndMillis) } }
                    ) {
                        Text("From\n${formatReportDate(reportStartMillis)}")
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { pickDate(reportEndMillis) { reportEndMillis = it.coerceAtLeast(reportStartMillis) } }
                    ) {
                        Text("To\n${formatReportDate(reportEndMillis)}")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { pdfLauncher.launch("eye_guardian_report_${dayKey(reportEndMillis)}.pdf") },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = appColor(0xFF3178C6))
                    ) { Text("Export PDF") }
                    OutlinedButton(onClick = { jpegLauncher.launch("eye_guardian_report_${dayKey(reportEndMillis)}.jpg") }) {
                        Text("Export JPEG")
                    }
                }
            }
        }

        Card(
            border = glassBorder(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = appColor(0xFFFFFFFF))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "20/20 eye reset",
                            style = MaterialTheme.typography.titleMedium,
                            color = appColor(0xFF163B2A)
                        )
                        Text(
                            text = "Get a gentle reminder to look far away while background protection is active.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = appColor(0xFF5F6D65)
                        )
                    }
                    Button(
                        onClick = {
                            twentyTwentyEnabled = !twentyTwentyEnabled
                            historyStore.setTwentyTwentyEnabled(twentyTwentyEnabled)
                            refreshMonitoringSettings()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (twentyTwentyEnabled) appColor(0xFF2E7D55) else appColor(0xFFE7ECE9),
                            contentColor = if (twentyTwentyEnabled) Color.White else appColor(0xFF5F6D65)
                        )
                    ) {
                        Text(if (twentyTwentyEnabled) "On" else "Off")
                    }
                }

                Text(
                    text = "Reminder interval",
                    style = MaterialTheme.typography.titleSmall,
                    color = appColor(0xFF163B2A)
                )
                intervalOptions.forEach { option ->
                    val label = if (option == 1L) "1 minute (test)" else "$option minutes"
                    if (option == intervalMinutes) {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {},
                            colors = ButtonDefaults.buttonColors(
                                containerColor = appColor(0xFF2E7D55),
                                contentColor = Color.White
                            )
                        ) {
                            Text("Every $label")
                        }
                    } else {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                intervalMinutes = option
                                historyStore.setTwentyTwentyIntervalMinutes(option)
                                refreshMonitoringSettings()
                            }
                        ) {
                            Text("Every $label")
                        }
                    }
                }

                Text(
                    text = "When the reminder arrives, “I'm on it” starts the next interval. “Snooze 5 min” dismisses it and brings it back after five minutes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = appColor(0xFF718078)
                )
            }
        }

        Card(
            border = glassBorder(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = appColor(0xFFE8F0FF))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Long-session break coach",
                            style = MaterialTheme.typography.titleMedium,
                            color = appColor(0xFF163B2A)
                        )
                        Text(
                            text = "Remind me after continuous use of one app.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = appColor(0xFF5F6D65)
                        )
                    }
                    Button(
                        onClick = {
                            breakEngineEnabled = !breakEngineEnabled
                            historyStore.setBreakEngineEnabled(breakEngineEnabled)
                            refreshMonitoringSettings()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (breakEngineEnabled) appColor(0xFF2E7D55) else appColor(0xFFE7ECE9),
                            contentColor = if (breakEngineEnabled) Color.White else appColor(0xFF5F6D65)
                        )
                    ) {
                        Text(if (breakEngineEnabled) "On" else "Off")
                    }
                }

                Text(
                    text = "Long-session threshold",
                    style = MaterialTheme.typography.titleSmall,
                    color = appColor(0xFF163B2A)
                )
                breakIntervalOptions.forEach { option ->
                    val label = if (option == 1L) "1 minute (test)" else "$option minutes"
                    if (option == breakIntervalMinutes) {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {},
                            colors = ButtonDefaults.buttonColors(
                                containerColor = appColor(0xFF2E7D55),
                                contentColor = Color.White
                            )
                        ) {
                            Text("After $label of one app")
                        }
                    } else {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                breakIntervalMinutes = option
                                historyStore.setBreakIntervalMinutes(option)
                                refreshMonitoringSettings()
                            }
                        ) {
                            Text("After $label of one app")
                        }
                    }
                }

                Text(
                    text = "A manual reset awards +20 XP only after a full 20-minute block. Continuing after the reminder can earn another +20 XP for each additional completed block.",
                    style = MaterialTheme.typography.bodySmall,
                    color = appColor(0xFF718078)
                )
                if (!hasUsageAccess) {
                    Text(
                        text = "Usage access is needed to know which app is active and how long the session has lasted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = appColor(0xFF8A5200)
                    )
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }) {
                        Text("Open usage access settings")
                    }
                } else {
                    Text(
                        text = "Usage access is enabled. Eye Guardian reads app timing only, not messages or content.",
                        style = MaterialTheme.typography.bodySmall,
                        color = appColor(0xFF2E7D55)
                    )
                }
            }
        }

        Card(
            border = glassBorder(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = appColor(0xFFE4EFFF).copy(alpha = .86f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Reminder sound",
                    style = MaterialTheme.typography.titleMedium,
                    color = appColor(0xFF163B2A)
                )
                Text(
                    text = "Choose one sound for Eye Guardian's distance, 20/20, and break reminders. Android will use it for the notification channels after they refresh.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = appColor(0xFF5F6D65)
                )
                Button(
                    onClick = { chooseNotificationSound() },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = appColor(0xFFE9769F))
                ) {
                    Text(if (notificationSoundUri == null) "Choose a custom sound" else "Change custom sound")
                }
                Text(
                    text = if (notificationSoundUri == null) "Using the device's default notification sound."
                    else "A custom sound is selected for Eye Guardian reminders.",
                    style = MaterialTheme.typography.bodySmall,
                    color = appColor(0xFF718078)
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    OutlinedButton(
                        enabled = openingChannelId == null,
                        onClick = {
                        openNotificationChannel(DistanceMonitoringService.ALERT_CHANNEL_ID)
                        }
                    ) {
                        Text(if (openingChannelId == DistanceMonitoringService.ALERT_CHANNEL_ID) "Opening sound settings…" else "Distance alert sound")
                    }
                    OutlinedButton(
                        enabled = openingChannelId == null,
                        onClick = {
                        openNotificationChannel(DistanceMonitoringService.TWENTY_TWENTY_CHANNEL_ID)
                        }
                    ) {
                        Text(if (openingChannelId == DistanceMonitoringService.TWENTY_TWENTY_CHANNEL_ID) "Opening sound settings…" else "20/20 reminder sound")
                    }
                    OutlinedButton(
                        enabled = openingChannelId == null,
                        onClick = {
                        openNotificationChannel(DistanceMonitoringService.BREAK_CHANNEL_ID)
                        }
                    ) {
                        Text(if (openingChannelId == DistanceMonitoringService.BREAK_CHANNEL_ID) "Opening sound settings…" else "Break coach sound")
                    }
                    if (openingChannelId != null) {
                        Text(
                            text = "Opening Android's notification sound settings…",
                            style = MaterialTheme.typography.bodySmall,
                            color = appColor(0xFF718078)
                        )
                    }
                }
            }
        }

        Card(
            border = glassBorder(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = appColor(0xFFFFE4EC).copy(alpha = .88f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Privacy & local data",
                    style = MaterialTheme.typography.titleMedium,
                    color = appColor(0xFF782B4B)
                )
                Text(
                    text = "Your profile, history, reports, XP, and activity summaries are stored locally. There is no account or cloud sync.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = appColor(0xFF80546A)
                )
                OutlinedButton(
                    onClick = { showClearConfirm = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = appColor(0xFFB33E68))
                ) {
                    Text("Clear saved history and profile")
                }
                TextButton(onClick = { showPrivacyPolicy = true }) {
                    Text("Read privacy policy", color = appColor(0xFFB33E68))
                }
            }
        }

        Card(
            border = glassBorder(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = appColor(0xFFE8F0FF).copy(alpha = .82f))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Diagnostics", style = MaterialTheme.typography.titleMedium, color = appColor(0xFF164A70))
                Text(
                    "Crash logs stay in this app's private storage and are never uploaded. They help diagnose failures during testing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = appColor(0xFF4A6C82)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Saved reports: $crashLogCount", color = appColor(0xFF164A70))
                    OutlinedButton(
                        onClick = {
                            LocalCrashLogger.clear(context)
                            crashLogCount = 0
                            Toast.makeText(context, "Crash logs cleared", Toast.LENGTH_SHORT).show()
                        },
                        enabled = crashLogCount > 0,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = appColor(0xFF164A70))
                    ) { Text("Clear logs") }
                }
            }
        }

        Text(
            text = "Completed resets: ${historyStore.getTwentyTwentyCompletedCount()}  •  Snoozed: ${historyStore.getTwentyTwentySnoozeCount()}",
            style = MaterialTheme.typography.bodySmall,
            color = appColor(0xFF718078)
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear local data?") },
            text = {
                Text("This removes your profile, saved usage, distance warnings, breaks, resets, XP, streaks, and game scores from this phone. This cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    historyStore.clearPersonalData()
                    profileName = ""
                    profileAge = ""
                    isEditingProfile = true
                    showClearConfirm = false
                    Toast.makeText(context, "Saved local data cleared", Toast.LENGTH_SHORT).show()
                }) { Text("Clear data", color = appColor(0xFFB33E68)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showPrivacyPolicy) {
        AlertDialog(
            onDismissRequest = { showPrivacyPolicy = false },
            title = { Text("Eye Guardian privacy policy") },
            text = {
                Column(
                    modifier = Modifier
                        .height(420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Eye Guardian is designed to work offline. It has no account, advertising, analytics, cloud sync, or remote crash upload.")
                    Text("Camera frames are analyzed on-device for distance protection and are not saved. Usage access is used only for aggregated foreground-app durations; the app does not read messages, page contents, or in-app activity.")
                    Text("Profile details, reminders, activity summaries, warnings, rewards, games, reports, and crash diagnostics stay in this app's private storage. Reports leave the app only when you explicitly export them to a destination you choose.")
                    Text("You can clear local history, profile, rewards, games, and crash logs from Settings. This app is a wellness aid, not medical advice or a diagnosis.")
                    Text("Full policy: PRIVACY.md in the Eye Guardian repository.", color = appColor(0xFF2E7D55))
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyPolicy = false }) { Text("Done") }
            }
        )
    }
}

@Composable
private fun DistanceLabScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var latestResult by remember { mutableStateOf(DistanceResult.unknown()) }
    var lightAssistEnabled by remember { mutableStateOf(false) }
    var showDistanceWarning by remember { mutableStateOf(false) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    val historyStore = remember(context) { EyeGuardianStore(context) }
    var backgroundMonitoring by remember { mutableStateOf(DistanceMonitoringService.isRunning) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            permissionMessage = "Camera permission was not granted. Open Android Settings if you want to enable distance protection."
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            backgroundMonitoring = true
            ContextCompat.startForegroundService(
                context,
                Intent(context, DistanceMonitoringService::class.java)
            )
        } else {
            permissionMessage = "Notifications are off, so background alerts cannot be shown. Enable notifications in Android Settings and try again."
        }
    }

    fun startBackgroundMonitoring() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            backgroundMonitoring = true
            ContextCompat.startForegroundService(
                context,
                Intent(context, DistanceMonitoringService::class.java)
            )
        }
    }

    LightAssistEffect(enabled = lightAssistEnabled)

    LaunchedEffect(latestResult.status, backgroundMonitoring) {
        if (!backgroundMonitoring && latestResult.status == DistanceStatus.TOO_CLOSE) {
            kotlinx.coroutines.delay(5_000L)
            if (latestResult.status == DistanceStatus.TOO_CLOSE) {
                historyStore.recordDistanceWarning()
                showDistanceWarning = true
            }
        } else {
            showDistanceWarning = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(appColor(0xFFEAF8F3), appColor(0xFFE8F3FF), appColor(0xFFFFF1EA))
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Distance lab",
                style = MaterialTheme.typography.headlineSmall,
                color = appColor(0xFF163B2A)
            )
        }

        Text(
            text = "Keep your face centered and move the phone slowly. This first version learns a broad near/far signal from face size; it does not claim an exact centimetre measurement.",
            style = MaterialTheme.typography.bodyMedium,
            color = appColor(0xFF5F6D65)
        )

        permissionMessage?.let { message ->
            Card(
            border = glassBorder(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = appColor(0xFFFFE4EC).copy(alpha = .92f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Permission needed", style = MaterialTheme.typography.titleSmall, color = appColor(0xFF782B4B))
                    Text(message, style = MaterialTheme.typography.bodyMedium, color = appColor(0xFF80546A))
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                            )
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = appColor(0xFFB33E68))
                    ) { Text("Open app permissions") }
                }
            }
        }

        if (hasCameraPermission) {
            if (backgroundMonitoring) {
                Card(
            border = glassBorder(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = appColor(0xFFDDF8EE).copy(alpha = .82f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Background protection is active",
                            style = MaterialTheme.typography.titleMedium,
                            color = appColor(0xFF163B2A)
                        )
                        Text(
                            text = "You can switch to Instagram, WhatsApp, or another app. Eye Guardian will keep checking distance and show a notification with sound and vibration if you stay too close.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = appColor(0xFF5F6D65)
                        )
                        Text(
                            text = "A persistent camera notification and Android's camera privacy indicator are expected while protection is active.",
                            style = MaterialTheme.typography.bodySmall,
                            color = appColor(0xFF718078)
                        )
                        OutlinedButton(onClick = {
                            backgroundMonitoring = false
                            context.stopService(Intent(context, DistanceMonitoringService::class.java))
                        }) {
                            Text("Stop background protection")
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            OutlinedButton(onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        putExtra(
                                            Settings.EXTRA_CHANNEL_ID,
                                            DistanceMonitoringService.ALERT_CHANNEL_ID
                                        )
                                    }
                                )
                            }) {
                                Text("Choose notification sound")
                            }
                        }
                    }
                }
            } else {
                CameraPreview(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(20.dp)),
                    onDistanceResult = { latestResult = it }
                )
                DistanceStatusCard(
                    result = latestResult,
                    lightAssistEnabled = lightAssistEnabled,
                    onToggleLightAssist = { lightAssistEnabled = !lightAssistEnabled }
                )
                Button(
                    onClick = { startBackgroundMonitoring() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = appColor(0xFF2E7D55),
                        contentColor = Color.White
                    )
                ) {
                    Text("Start background protection")
                }
                Text(
                    text = "Start this while Eye Guardian is visible, then switch to any other app. You can stop it from this screen or from the notification.",
                    style = MaterialTheme.typography.bodySmall,
                    color = appColor(0xFF718078)
                )
            }
        } else {
            Card(
            border = glassBorder(),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = appColor(0xFFFFFFFF)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Camera permission needed",
                        style = MaterialTheme.typography.titleMedium,
                        color = appColor(0xFF163B2A)
                    )
                    Text(
                        text = "Eye Guardian needs the front camera only while this screen is open. No photos or videos are stored.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = appColor(0xFF5F6D65)
                    )
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Allow camera")
                    }
                }
            }
        }

            Text(
                text = "Calibration note: the face-size thresholds are starter values. The warning appears after five continuous seconds in the too-close state.",
                style = MaterialTheme.typography.bodySmall,
                color = appColor(0xFF718078)
            )
        }

        if (showDistanceWarning) {
            DistanceWarningOverlay(
                onMoveBack = { showDistanceWarning = false }
            )
        }
    }
}

@Composable
private fun DistanceWarningOverlay(onMoveBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(appColor(0xFFFFE7B8), appColor(0xFFFFEEF7), appColor(0xFFE8F3FF))
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            border = glassBorder(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = appColor(0xFFFFFFFF)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                DashboardBadge(kind = "eye")
                Text(
                    text = "Take a step back",
                    style = MaterialTheme.typography.headlineSmall,
                    color = appColor(0xFF8A5200)
                )
                Text(
                    text = "You have been holding the phone very close for several seconds. Give your eyes a little more space.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = appColor(0xFF4E4030)
                )
                Button(
                    onClick = onMoveBack,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = appColor(0xFFB26A00),
                        contentColor = Color.White
                    )
                ) {
                    Text("I moved back")
                }
            }
        }
    }
}

@Composable
private fun DistanceStatusCard(
    result: DistanceResult,
    lightAssistEnabled: Boolean,
    onToggleLightAssist: () -> Unit
) {
    val statusColor = when (result.status) {
        DistanceStatus.COMFORTABLE -> appColor(0xFF2E7D55)
        DistanceStatus.GETTING_CLOSE -> appColor(0xFFB26A00)
        DistanceStatus.TOO_CLOSE -> appColor(0xFFC43D3D)
        DistanceStatus.LOW_LIGHT -> appColor(0xFF8A6500)
        DistanceStatus.UNKNOWN -> appColor(0xFF718078)
    }

    Card(
            border = glassBorder(),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = appColor(0xFFFFFFFF).copy(alpha = .76f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = result.status.label,
                style = MaterialTheme.typography.headlineSmall,
                color = statusColor
            )
            Text(
                text = result.message,
                style = MaterialTheme.typography.bodyLarge,
                color = appColor(0xFF163B2A)
            )
            Text(
                text = result.detail,
                style = MaterialTheme.typography.bodySmall,
                color = appColor(0xFF718078)
            )
            if (result.status == DistanceStatus.LOW_LIGHT) {
                Button(onClick = onToggleLightAssist) {
                    Text(if (lightAssistEnabled) "Light assist on" else "Enable light assist")
                }
                Text(
                    text = "Face a lamp or window if possible. Light assist temporarily raises this app's screen brightness; it does not use or infer gender, race, or skin tone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = appColor(0xFF718078)
                )
            }
        }
    }
}

@Composable
private fun LightAssistEffect(enabled: Boolean) {
    val context = LocalContext.current

    DisposableEffect(enabled) {
        val window = (context as? Activity)?.window
        val previousBrightness = window?.attributes?.screenBrightness

        if (enabled && window != null) {
            val attributes = window.attributes
            attributes.screenBrightness = 1f
            window.attributes = attributes
        }

        onDispose {
            if (window != null && previousBrightness != null) {
                val attributes = window.attributes
                attributes.screenBrightness = previousBrightness
                window.attributes = attributes
            }
        }
    }
}

@Composable
private fun CameraPreview(
    modifier: Modifier = Modifier,
    onDistanceResult: (DistanceResult) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraSession = remember(context, lifecycleOwner) {
        CameraSession(
            context = context,
            lifecycleOwner = lifecycleOwner,
            onDistanceResult = onDistanceResult
        )
    }

    DisposableEffect(cameraSession) {
        cameraSession.start()
        onDispose { cameraSession.close() }
    }

    AndroidView(
        modifier = modifier.semantics {
            contentDescription = "Front camera preview for viewing distance detection"
        },
        factory = { cameraSession.previewView }
    )
}

@SuppressLint("UnsafeOptInUsageError")
private class CameraSession(
    context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onDistanceResult: (DistanceResult) -> Unit
) {
    val previewView: PreviewView = PreviewView(context).apply {
        scaleType = PreviewView.ScaleType.FILL_CENTER
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val faceDetector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
    )
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    @Volatile private var closed = false

    fun start() {
        val providerFuture = ProcessCameraProvider.getInstance(previewView.context)
        providerFuture.addListener({
            if (closed) return@addListener

            val provider = providerFuture.get()
            val preview = CameraPreviewUseCase.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(
                        cameraExecutor,
                        FaceDistanceAnalyzer(faceDetector) { result ->
                            mainHandler.post { onDistanceResult(result) }
                        }
                    )
                }

            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    analysis
                )
                cameraProvider = provider
                imageAnalysis = analysis
            } catch (_: Exception) {
                mainHandler.post {
                    onDistanceResult(DistanceResult.unknown("The front camera could not be opened."))
                }
            }
        }, ContextCompat.getMainExecutor(previewView.context))
    }

    fun close() {
        closed = true
        imageAnalysis?.clearAnalyzer()
        cameraProvider?.unbindAll()
        faceDetector.close()
        cameraExecutor.shutdown()
    }
}

@SuppressLint("UnsafeOptInUsageError")
class FaceDistanceAnalyzer(
    private val faceDetector: FaceDetector,
    private val onResult: (DistanceResult) -> Unit
) : ImageAnalysis.Analyzer {
    private var lastFaceResult: DistanceResult? = null
    private var lastFaceDetectedAt: Long = 0L

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        if (averageLuma(imageProxy) < 32f) {
            onResult(DistanceResult.lowLight())
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        faceDetector.process(inputImage)
            .addOnSuccessListener { faces ->
                val result = when {
                    faces.isEmpty() -> {
                        val recentResult = lastFaceResult
                        if (
                            recentResult?.status == DistanceStatus.TOO_CLOSE &&
                            System.currentTimeMillis() - lastFaceDetectedAt < 6_000L
                        ) {
                            DistanceResult(
                                status = DistanceStatus.TOO_CLOSE,
                                message = "Your face may be very close or partly outside the frame.",
                                detail = "Move the phone slightly back so your full face is visible"
                            )
                        } else {
                            DistanceResult.unknown("Move into the camera view.")
                        }
                    }
                    faces.size > 1 -> DistanceResult.unknown("More than one face detected.")
                    else -> {
                        val faceWidthRatio = faces.first().boundingBox.width().toFloat() /
                            inputImage.width.toFloat()
                        DistanceResult.fromFaceWidthRatio(faceWidthRatio)
                    }
                }
                if (faces.size == 1) {
                    lastFaceResult = result
                    lastFaceDetectedAt = System.currentTimeMillis()
                }
                onResult(result)
            }
            .addOnFailureListener {
                onResult(DistanceResult.unknown("Face detection is temporarily unavailable."))
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun averageLuma(imageProxy: ImageProxy): Float {
        val yPlane = imageProxy.planes.firstOrNull() ?: return 255f
        val buffer = yPlane.buffer.duplicate()
        if (!buffer.hasRemaining()) return 255f

        val sampleStep = maxOf(1, buffer.remaining() / 800)
        var total = 0L
        var samples = 0
        while (buffer.hasRemaining()) {
            total += buffer.get().toInt() and 0xFF
            samples++
            repeat(sampleStep - 1) {
                if (buffer.hasRemaining()) buffer.get()
            }
        }
        return if (samples == 0) 255f else total.toFloat() / samples
    }
}

enum class DistanceStatus(val label: String) {
    COMFORTABLE("Comfortable"),
    GETTING_CLOSE("Getting close"),
    TOO_CLOSE("Too close"),
    LOW_LIGHT("Need more light"),
    UNKNOWN("Looking for you")
}

data class DistanceResult(
    val status: DistanceStatus,
    val message: String,
    val detail: String
) {
    companion object {
        fun unknown(message: String = "Waiting for a clear face view.") = DistanceResult(
            status = DistanceStatus.UNKNOWN,
            message = message,
            detail = "No distance estimate yet"
        )

        fun lowLight() = DistanceResult(
            status = DistanceStatus.LOW_LIGHT,
            message = "The scene is too dark for a reliable reading.",
            detail = "Face a light source or enable light assist"
        )

        fun fromFaceWidthRatio(ratio: Float): DistanceResult {
            val percent = (ratio * 100).toInt()
            return when {
                ratio >= 0.40f -> DistanceResult(
                    DistanceStatus.TOO_CLOSE,
                    "Move the phone a little farther away.",
                    "Face size in frame: about $percent%"
                )
                ratio >= 0.28f -> DistanceResult(
                    DistanceStatus.GETTING_CLOSE,
                    "A little more space may be comfortable.",
                    "Face size in frame: about $percent%"
                )
                else -> DistanceResult(
                    DistanceStatus.COMFORTABLE,
                    "Your current position looks comfortable.",
                    "Face size in frame: about $percent%"
                )
            }
        }
    }
}

private data class UsageAppRow(
    val packageName: String,
    val label: String,
    val minutes: Long
)

internal data class UsageBlockRecord(
    val packageName: String,
    val startMillis: Long,
    val endMillis: Long
) {
    val durationMillis: Long
        get() = (endMillis - startMillis).coerceAtLeast(0L)
}

internal data class DailyUsageRecord(
    val dateKey: String,
    val totalMinutes: Long,
    val appMinutes: Map<String, Long>,
    val hourlyMinutes: List<Long>,
    val activityBlockCount: Int,
    val longestBlockMinutes: Long,
    val blocks: List<UsageBlockRecord>,
    val capturedAtMillis: Long
)

private data class UsageSnapshot(
    val totalMinutes: Long,
    val apps: List<UsageAppRow>,
    val appMinutes: Map<String, Long>,
    val hourlyMinutes: List<Long>,
    val dailyMinutes: List<Long>,
    val dailyLabels: List<String>,
    val sessionCount: Int,
    val longestSessionMinutes: Long,
    val foregroundBlocks: List<UsageBlockRecord>
)

private data class HabitAnalysis(
    val warningCountToday: Int,
    val longBlockCountToday: Int,
    val warningsAssociatedWithLongBlocks: Int,
    val strongestAppLabel: String?,
    val completedBreaksToday: Int,
    val skippedBreaksToday: Int,
    val completedResetsToday: Int,
    val warningsLastSevenDays: Int,
    val warningsPreviousSevenDays: Int,
    val recommendations: List<String>
)

private data class CareHistoryItem(
    val timestampMillis: Long,
    val title: String,
    val detail: String,
    val completed: Boolean
)

private data class GamificationState(
    val xp: Int,
    val level: Int,
    val currentStreakDays: Int,
    val checkInDays: Int,
    val completedBreaks: Int,
    val completedTwentyTwentyResets: Int,
    val warningCount: Int,
    val gamesPlayed: Int,
    val bestGameScore: Int,
    val achievements: List<String>,
    val todayChallengeComplete: Boolean
)

internal data class DistanceWarningRecord(
    val dateKey: String,
    val timestampMillis: Long
)

internal data class BreakRecord(
    val dateKey: String,
    val timestampMillis: Long,
    val packageName: String,
    val completed: Boolean
)

internal data class TwentyTwentyRecord(
    val dateKey: String,
    val timestampMillis: Long,
    val completed: Boolean
)

internal enum class GameDifficulty(
    val label: String,
    val durationSeconds: Int,
    val movementMillis: Long,
    val baseTargetSize: Int
) {
    EASY("Easy", 30, 2_000L, 68),
    MEDIUM("Medium", 20, 1_250L, 58),
    HARD("Hard", 15, 750L, 48)
}

internal data class FocusBall(
    val id: String,
    val name: String,
    val startColor: Long,
    val endColor: Long,
    val requiredPlays: Int,
    val requiredBestScore: Int
)

private val FOCUS_BALLS = listOf(
    FocusBall("sunrise", "Sunrise", 0xFFFFE08A, 0xFFFF8FAD, 0, 0),
    FocusBall("ocean", "Ocean", 0xFF8BE9FD, 0xFF6C63FF, 3, 30),
    FocusBall("aurora", "Aurora", 0xFF9BEFC6, 0xFF7B61FF, 7, 80),
    FocusBall("ember", "Ember", 0xFFFFC857, 0xFFE85D75, 12, 150)
)

private fun unlockedFocusBalls(results: List<GameScoreRecord>): List<FocusBall> {
    val plays = results.size
    val bestScore = results.maxOfOrNull { it.score } ?: 0
    return FOCUS_BALLS.filter { plays >= it.requiredPlays || bestScore >= it.requiredBestScore }
}

internal data class GameScoreRecord(
    val score: Int,
    val difficulty: String,
    val timestampMillis: Long
)

internal class EyeGuardianStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun isOnboardingComplete(): Boolean {
        return preferences.getBoolean(KEY_ONBOARDING_COMPLETE, false)
    }

    fun setOnboardingComplete(complete: Boolean) {
        preferences.edit().putBoolean(KEY_ONBOARDING_COMPLETE, complete).apply()
    }

    fun isDarkTheme(): Boolean {
        return preferences.getBoolean(KEY_DARK_THEME, false)
    }

    fun setDarkTheme(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_DARK_THEME, enabled).apply()
    }

    fun getProfileName(): String {
        return preferences.getString(KEY_PROFILE_NAME, "") ?: ""
    }

    fun setProfileName(name: String) {
        preferences.edit().putString(KEY_PROFILE_NAME, name.trim().take(60)).apply()
    }

    fun getProfileAge(): Int? {
        val age = preferences.getInt(KEY_PROFILE_AGE, 0)
        return age.takeIf { it in 1..120 }
    }

    fun setProfileAge(age: Int?) {
        preferences.edit().putInt(KEY_PROFILE_AGE, age?.coerceIn(1, 120) ?: 0).apply()
    }

    fun getInstallDateMillis(): Long {
        val saved = preferences.getLong(KEY_INSTALL_DATE, 0L)
        if (saved > 0L) return saved
        val installTime = runCatching {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).firstInstallTime
        }.getOrDefault(System.currentTimeMillis())
        preferences.edit().putLong(KEY_INSTALL_DATE, installTime).apply()
        return installTime
    }

    fun getNotificationSoundUri(): String? {
        return preferences.getString(KEY_NOTIFICATION_SOUND_URI, null)
    }

    fun setNotificationSoundUri(uri: String?) {
        preferences.edit().apply {
            if (uri.isNullOrBlank()) remove(KEY_NOTIFICATION_SOUND_URI)
            else putString(KEY_NOTIFICATION_SOUND_URI, uri)
        }.apply()
    }

    fun getSelectedFocusBallId(): String {
        return preferences.getString(KEY_SELECTED_FOCUS_BALL, FOCUS_BALLS.first().id)
            ?: FOCUS_BALLS.first().id
    }

    fun setSelectedFocusBallId(id: String) {
        preferences.edit().putString(KEY_SELECTED_FOCUS_BALL, id).apply()
    }

    fun clearPersonalData() {
        preferences.edit()
            .remove(KEY_USAGE)
            .remove(KEY_WARNINGS)
            .remove(KEY_GAME_SCORES)
            .remove(KEY_TWENTY_TWENTY_COMPLETED_COUNT)
            .remove(KEY_TWENTY_TWENTY_SNOOZE_COUNT)
            .remove(KEY_LAST_TWENTY_TWENTY_COMPLETED_AT)
            .remove(KEY_TWENTY_TWENTY_RECORDS)
            .remove(KEY_BREAK_RECORDS)
            .remove(KEY_MANUAL_BREAK_ACTIVE)
            .remove(KEY_MANUAL_BREAK_STARTED_AT)
            .remove(KEY_MANUAL_BREAK_AWARDED_BLOCKS)
            .remove(KEY_PROFILE_NAME)
            .remove(KEY_PROFILE_AGE)
            .remove(KEY_ONBOARDING_COMPLETE)
            .apply()
        LocalCrashLogger.clear(appContext)
    }

    fun saveUsage(record: DailyUsageRecord) {
        val records = readUsageRecords()
            .filterNot { it.dateKey == record.dateKey }
            .plus(record)
            .sortedBy { it.dateKey }
            .takeLast(MAX_HISTORY_DAYS)
        preferences.edit()
            .putString(KEY_USAGE, encodeUsageRecords(records))
            .apply()
    }

    fun setMonitoringActive(active: Boolean) {
        preferences.edit().putBoolean(KEY_MONITORING_ACTIVE, active).apply()
    }

    fun isMonitoringActive(): Boolean {
        return preferences.getBoolean(KEY_MONITORING_ACTIVE, false)
    }

    fun isTwentyTwentyEnabled(): Boolean {
        return preferences.getBoolean(KEY_TWENTY_TWENTY_ENABLED, true)
    }

    fun isManualBreakActive(): Boolean {
        return preferences.getBoolean(KEY_MANUAL_BREAK_ACTIVE, false)
    }

    fun setManualBreakActive(active: Boolean) {
        preferences.edit().putBoolean(KEY_MANUAL_BREAK_ACTIVE, active).apply()
    }

    fun getManualBreakStartedAt(): Long {
        return preferences.getLong(KEY_MANUAL_BREAK_STARTED_AT, 0L)
    }

    fun setManualBreakStartedAt(timestampMillis: Long) {
        preferences.edit().putLong(KEY_MANUAL_BREAK_STARTED_AT, timestampMillis).apply()
    }

    fun getManualBreakAwardedBlocks(): Int {
        return preferences.getInt(KEY_MANUAL_BREAK_AWARDED_BLOCKS, 0).coerceAtLeast(0)
    }

    fun setManualBreakAwardedBlocks(blocks: Int) {
        preferences.edit().putInt(KEY_MANUAL_BREAK_AWARDED_BLOCKS, blocks.coerceAtLeast(0)).apply()
    }

    fun setTwentyTwentyEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_TWENTY_TWENTY_ENABLED, enabled).apply()
    }

    fun getTwentyTwentyIntervalMinutes(): Long {
        return preferences.getLong(KEY_TWENTY_TWENTY_INTERVAL_MINUTES, DEFAULT_TWENTY_TWENTY_MINUTES)
            .coerceIn(MIN_TWENTY_TWENTY_MINUTES, MAX_TWENTY_TWENTY_MINUTES)
    }

    fun setTwentyTwentyIntervalMinutes(minutes: Long) {
        preferences.edit()
            .putLong(
                KEY_TWENTY_TWENTY_INTERVAL_MINUTES,
                minutes.coerceIn(MIN_TWENTY_TWENTY_MINUTES, MAX_TWENTY_TWENTY_MINUTES)
            )
            .apply()
    }

    fun setNextTwentyTwentyAt(timestampMillis: Long) {
        preferences.edit().putLong(KEY_NEXT_TWENTY_TWENTY_AT, timestampMillis).apply()
    }

    fun recordTwentyTwentyCompleted(timestampMillis: Long = System.currentTimeMillis()) {
        recordTwentyTwentyEvent(timestampMillis, completed = true)
        preferences.edit()
            .putInt(KEY_TWENTY_TWENTY_COMPLETED_COUNT, getTwentyTwentyCompletedCount() + 1)
            .putLong(KEY_LAST_TWENTY_TWENTY_COMPLETED_AT, timestampMillis)
            .apply()
    }

    fun recordTwentyTwentySnoozed() {
        recordTwentyTwentyEvent(System.currentTimeMillis(), completed = false)
        preferences.edit()
            .putInt(KEY_TWENTY_TWENTY_SNOOZE_COUNT, getTwentyTwentySnoozeCount() + 1)
            .apply()
    }

    private fun recordTwentyTwentyEvent(timestampMillis: Long, completed: Boolean) {
        val records = (readTwentyTwentyRecords() + TwentyTwentyRecord(
            dateKey = dayKey(timestampMillis),
            timestampMillis = timestampMillis,
            completed = completed
        )).sortedBy { it.timestampMillis }.takeLast(MAX_HISTORY_TWENTY_TWENTY)
        preferences.edit()
            .putString(KEY_TWENTY_TWENTY_RECORDS, encodeTwentyTwentyRecords(records))
            .apply()
    }

    fun readTwentyTwentyRecords(): List<TwentyTwentyRecord> {
        val raw = preferences.getString(KEY_TWENTY_TWENTY_RECORDS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        TwentyTwentyRecord(
                            dateKey = item.optString("dateKey"),
                            timestampMillis = item.optLong("timestampMillis"),
                            completed = item.optBoolean("completed", false)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun readTwentyTwentyCompletedRecords(): List<TwentyTwentyRecord> {
        return readTwentyTwentyRecords().filter { it.completed }
    }

    fun getTwentyTwentyCompletedCount(): Int {
        return preferences.getInt(KEY_TWENTY_TWENTY_COMPLETED_COUNT, 0)
    }

    fun getTwentyTwentySnoozeCount(): Int {
        return preferences.getInt(KEY_TWENTY_TWENTY_SNOOZE_COUNT, 0)
    }

    fun isBreakEngineEnabled(): Boolean {
        return preferences.getBoolean(KEY_BREAK_ENGINE_ENABLED, true)
    }

    fun setBreakEngineEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_BREAK_ENGINE_ENABLED, enabled).apply()
    }

    fun getBreakIntervalMinutes(): Long {
        return preferences.getLong(KEY_BREAK_INTERVAL_MINUTES, DEFAULT_BREAK_INTERVAL_MINUTES)
            .coerceIn(MIN_BREAK_INTERVAL_MINUTES, MAX_BREAK_INTERVAL_MINUTES)
    }

    fun setBreakIntervalMinutes(minutes: Long) {
        preferences.edit()
            .putLong(
                KEY_BREAK_INTERVAL_MINUTES,
                minutes.coerceIn(MIN_BREAK_INTERVAL_MINUTES, MAX_BREAK_INTERVAL_MINUTES)
            )
            .apply()
    }

    fun recordBreakCompleted(
        packageName: String,
        timestampMillis: Long = System.currentTimeMillis()
    ) {
        recordBreak(
            BreakRecord(dayKey(timestampMillis), timestampMillis, packageName, completed = true)
        )
    }

    fun recordBreakSkipped(
        packageName: String,
        timestampMillis: Long = System.currentTimeMillis()
    ) {
        recordBreak(
            BreakRecord(dayKey(timestampMillis), timestampMillis, packageName, completed = false)
        )
    }

    private fun recordBreak(record: BreakRecord) {
        val records = (readBreakRecords() + record)
            .sortedBy { it.timestampMillis }
            .takeLast(MAX_HISTORY_BREAKS)
        preferences.edit()
            .putString(KEY_BREAK_RECORDS, encodeBreakRecords(records))
            .apply()
    }

    fun readBreakRecords(): List<BreakRecord> {
        val raw = preferences.getString(KEY_BREAK_RECORDS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        BreakRecord(
                            dateKey = item.optString("dateKey"),
                            timestampMillis = item.optLong("timestampMillis"),
                            packageName = item.optString("packageName"),
                            completed = item.optBoolean("completed", false)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun recordDistanceWarning(timestampMillis: Long = System.currentTimeMillis()) {
        val dateKey = dayKey(timestampMillis)
        val existing = readWarningRecords()
        if (existing.any { kotlin.math.abs(it.timestampMillis - timestampMillis) < WARNING_DEDUPE_WINDOW_MILLIS }) {
            return
        }
        val records = existing
            .plus(DistanceWarningRecord(dateKey, timestampMillis))
            .sortedBy { it.timestampMillis }
            .takeLast(MAX_HISTORY_WARNINGS)
        preferences.edit()
            .putString(KEY_WARNINGS, encodeWarningRecords(records))
            .apply()
    }

    fun recordGameResult(score: Int, difficulty: GameDifficulty) {
        val results = (readGameResults() + GameScoreRecord(
            score = score,
            difficulty = difficulty.label,
            timestampMillis = System.currentTimeMillis()
        )).takeLast(MAX_GAME_RESULTS)
        preferences.edit()
            .putString(KEY_GAME_SCORES, encodeGameResults(results))
            .apply()
    }

    fun readGameResults(): List<GameScoreRecord> {
        val raw = preferences.getString(KEY_GAME_SCORES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.opt(index)
                    if (item is JSONObject) {
                        add(
                            GameScoreRecord(
                                score = item.optInt("score", 0),
                                difficulty = item.optString("difficulty", GameDifficulty.MEDIUM.label),
                                timestampMillis = item.optLong("timestampMillis", 0L)
                            )
                        )
                    } else {
                        // Keep scores written by the first game version usable.
                        add(
                            GameScoreRecord(
                                score = (item as? Number)?.toInt() ?: 0,
                                difficulty = GameDifficulty.MEDIUM.label,
                                timestampMillis = 0L
                            )
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeGameResults(results: List<GameScoreRecord>): String {
        val array = JSONArray()
        results.forEach { result ->
            array.put(
                JSONObject()
                    .put("score", result.score)
                    .put("difficulty", result.difficulty)
                    .put("timestampMillis", result.timestampMillis)
            )
        }
        return array.toString()
    }

    fun readUsageRecords(): List<DailyUsageRecord> {
        val raw = preferences.getString(KEY_USAGE, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val appObject = item.optJSONObject("appMinutes") ?: JSONObject()
                    val appMinutes = buildMap {
                        appObject.keys().forEach { packageName ->
                            put(packageName, appObject.optLong(packageName, 0L))
                        }
                    }
                    val hourlyArray = item.optJSONArray("hourlyMinutes") ?: JSONArray()
                    val hourlyMinutes = buildList {
                        for (hour in 0 until hourlyArray.length()) add(hourlyArray.optLong(hour, 0L))
                    }
                    val blocksArray = item.optJSONArray("blocks") ?: JSONArray()
                    val blocks = buildList {
                        for (blockIndex in 0 until blocksArray.length()) {
                            val block = blocksArray.getJSONObject(blockIndex)
                            add(
                                UsageBlockRecord(
                                    packageName = block.optString("packageName"),
                                    startMillis = block.optLong("startMillis"),
                                    endMillis = block.optLong("endMillis")
                                )
                            )
                        }
                    }
                    add(
                        DailyUsageRecord(
                            dateKey = item.optString("dateKey"),
                            totalMinutes = item.optLong("totalMinutes"),
                            appMinutes = appMinutes,
                            hourlyMinutes = hourlyMinutes,
                            activityBlockCount = item.optInt("activityBlockCount"),
                            longestBlockMinutes = item.optLong("longestBlockMinutes"),
                            blocks = blocks,
                            capturedAtMillis = item.optLong("capturedAtMillis")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun readWarningRecords(): List<DistanceWarningRecord> {
        val raw = preferences.getString(KEY_WARNINGS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        DistanceWarningRecord(
                            dateKey = item.optString("dateKey"),
                            timestampMillis = item.optLong("timestampMillis")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeUsageRecords(records: List<DailyUsageRecord>): String {
        val array = JSONArray()
        records.forEach { record ->
            val item = JSONObject()
                .put("dateKey", record.dateKey)
                .put("totalMinutes", record.totalMinutes)
                .put("activityBlockCount", record.activityBlockCount)
                .put("longestBlockMinutes", record.longestBlockMinutes)
                .put("capturedAtMillis", record.capturedAtMillis)
            val appObject = JSONObject()
            record.appMinutes.forEach { (packageName, minutes) -> appObject.put(packageName, minutes) }
            item.put("appMinutes", appObject)
            item.put("hourlyMinutes", JSONArray(record.hourlyMinutes))
            val blocks = JSONArray()
            record.blocks.forEach { block ->
                blocks.put(
                    JSONObject()
                        .put("packageName", block.packageName)
                        .put("startMillis", block.startMillis)
                        .put("endMillis", block.endMillis)
                )
            }
            item.put("blocks", blocks)
            array.put(item)
        }
        return array.toString()
    }

    private fun encodeWarningRecords(records: List<DistanceWarningRecord>): String {
        val array = JSONArray()
        records.forEach { warning ->
            array.put(
                JSONObject()
                    .put("dateKey", warning.dateKey)
                    .put("timestampMillis", warning.timestampMillis)
            )
        }
        return array.toString()
    }

    private fun encodeBreakRecords(records: List<BreakRecord>): String {
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject()
                    .put("dateKey", record.dateKey)
                    .put("timestampMillis", record.timestampMillis)
                    .put("packageName", record.packageName)
                    .put("completed", record.completed)
            )
        }
        return array.toString()
    }

    private fun encodeTwentyTwentyRecords(records: List<TwentyTwentyRecord>): String {
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject()
                    .put("dateKey", record.dateKey)
                    .put("timestampMillis", record.timestampMillis)
                    .put("completed", record.completed)
            )
        }
        return array.toString()
    }

    private companion object {
        const val PREFERENCES_NAME = "eye_guardian_history"
        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        const val KEY_DARK_THEME = "dark_theme"
        const val KEY_PROFILE_NAME = "profile_name"
        const val KEY_PROFILE_AGE = "profile_age"
        const val KEY_INSTALL_DATE = "install_date"
        const val KEY_NOTIFICATION_SOUND_URI = "notification_sound_uri"
        const val KEY_SELECTED_FOCUS_BALL = "selected_focus_ball"
        const val KEY_USAGE = "daily_usage_records"
        const val KEY_WARNINGS = "distance_warning_records"
        const val KEY_GAME_SCORES = "focus_trail_scores"
        const val KEY_MONITORING_ACTIVE = "background_monitoring_active"
        const val KEY_TWENTY_TWENTY_ENABLED = "twenty_twenty_enabled"
        const val KEY_TWENTY_TWENTY_INTERVAL_MINUTES = "twenty_twenty_interval_minutes"
        const val KEY_NEXT_TWENTY_TWENTY_AT = "next_twenty_twenty_at"
        const val KEY_TWENTY_TWENTY_COMPLETED_COUNT = "twenty_twenty_completed_count"
        const val KEY_TWENTY_TWENTY_SNOOZE_COUNT = "twenty_twenty_snooze_count"
        const val KEY_LAST_TWENTY_TWENTY_COMPLETED_AT = "last_twenty_twenty_completed_at"
        const val KEY_TWENTY_TWENTY_RECORDS = "twenty_twenty_records"
        const val KEY_BREAK_ENGINE_ENABLED = "break_engine_enabled"
        const val KEY_BREAK_INTERVAL_MINUTES = "break_interval_minutes"
        const val KEY_BREAK_RECORDS = "break_records"
        const val KEY_MANUAL_BREAK_ACTIVE = "manual_break_active"
        const val KEY_MANUAL_BREAK_STARTED_AT = "manual_break_started_at"
        const val KEY_MANUAL_BREAK_AWARDED_BLOCKS = "manual_break_awarded_blocks"
        const val MAX_HISTORY_DAYS = 90
        const val MAX_HISTORY_WARNINGS = 500
        const val MAX_HISTORY_BREAKS = 500
        const val MAX_HISTORY_TWENTY_TWENTY = 500
        const val MAX_GAME_RESULTS = 100
        const val WARNING_DEDUPE_WINDOW_MILLIS = 30_000L
        const val DEFAULT_TWENTY_TWENTY_MINUTES = 20L
        const val MIN_TWENTY_TWENTY_MINUTES = 1L
        const val MAX_TWENTY_TWENTY_MINUTES = 120L
        const val DEFAULT_BREAK_INTERVAL_MINUTES = 45L
        const val MIN_BREAK_INTERVAL_MINUTES = 1L
        const val MAX_BREAK_INTERVAL_MINUTES = 180L
    }
}

private data class ReportBundle(
    val lines: List<String>,
    val title: String
)

private fun buildReportBundle(
    context: Context,
    store: EyeGuardianStore,
    profileName: String,
    profileAge: String,
    startMillis: Long,
    endMillis: Long
): ReportBundle {
    val startKey = dayKey(startMillis)
    val endKey = dayKey(endMillis)
    val usage = store.readUsageRecords().filter { it.dateKey in startKey..endKey }
    val warnings = store.readWarningRecords().filter { it.dateKey in startKey..endKey }
    val breaks = store.readBreakRecords().filter { it.dateKey in startKey..endKey }
    val resets = store.readTwentyTwentyRecords().filter { it.dateKey in startKey..endKey }
    val games = store.readGameResults().filter {
        it.timestampMillis in startMillis..(endMillis + 24L * 60L * 60_000L - 1L)
    }
    val appTotals = usage.flatMap { it.appMinutes.entries }
        .groupBy({ it.key }, { it.value })
        .mapValues { (_, values) -> values.sum() }
        .entries
        .sortedByDescending { it.value }
        .take(8)

    val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val rangeText = "${dateFormatter.format(Date(startMillis))} – ${dateFormatter.format(Date(endMillis))}"
    val displayName = profileName.ifBlank { "Not provided" }
    val displayAge = profileAge.ifBlank { "Not provided" }
    val totalMinutes = usage.sumOf { it.totalMinutes }
    val completedBreaks = breaks.count { it.completed }
    val completedResets = resets.count { it.completed }

    val lines = buildList {
        add("Eye Guardian wellness report")
        add("Profile: $displayName  |  Age: $displayAge")
        add("Report period: $rangeText")
        add("")
        add("SUMMARY")
        add("Tracked user-app time: ${formatUsageDuration(totalMinutes)}")
        add("Distance warnings: ${warnings.size}")
        add("Completed screen breaks: $completedBreaks")
        add("Completed 20/20 resets: $completedResets")
        add("Focus Trail games: ${games.size}")
        add("")
        add("TOP APP TIME")
        if (appTotals.isEmpty()) {
            add("No saved app-usage records in this period.")
        } else {
            appTotals.forEach { (packageName, minutes) ->
                add("${resolveAppLabel(context, packageName)}: ${formatUsageDuration(minutes)}")
            }
        }
        add("")
        add("DAILY PATTERN")
        val days = usage.map { it.dateKey }.toSet()
            .plus(warnings.map { it.dateKey })
            .plus(breaks.map { it.dateKey })
            .plus(resets.map { it.dateKey })
            .sorted()
        if (days.isEmpty()) {
            add("No saved activity in this period.")
        } else {
            days.forEach { dateKey ->
                val dayUsage = usage.firstOrNull { it.dateKey == dateKey }?.totalMinutes ?: 0L
                val dayWarnings = warnings.count { it.dateKey == dateKey }
                val dayBreaks = breaks.count { it.dateKey == dateKey && it.completed }
                val dayResets = resets.count { it.dateKey == dateKey && it.completed }
                add("$dateKey  •  ${formatUsageDuration(dayUsage)}  •  $dayWarnings warnings  •  $dayBreaks breaks  •  $dayResets resets")
            }
        }
        add("")
        add("Privacy: this report was created from locally stored aggregated activity. Eye Guardian does not read messages, page contents, or app content.")
    }
    return ReportBundle(lines = lines, title = "Eye Guardian report")
}

private fun writeReportAsPdf(context: Context, uri: Uri, report: ReportBundle) {
    val document = PdfDocument()
    val linesPerPage = 42
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(24, 55, 43)
        textSize = 16f
    }
    report.lines.chunked(linesPerPage).forEachIndexed { pageIndex, pageLines ->
        val page = document.startPage(
            PdfDocument.PageInfo.Builder(595, 842, pageIndex + 1).create()
        )
        val canvas = page.canvas
        canvas.drawColor(android.graphics.Color.rgb(245, 249, 247))
        var y = 58f
        pageLines.forEachIndexed { index, line ->
            paint.textSize = when {
                pageIndex == 0 && index == 0 -> 25f
                line == "SUMMARY" || line == "TOP APP TIME" || line == "DAILY PATTERN" -> 15f
                else -> 12f
            }
            paint.color = when {
                line == "SUMMARY" || line == "TOP APP TIME" || line == "DAILY PATTERN" -> android.graphics.Color.rgb(8, 122, 88)
                else -> android.graphics.Color.rgb(35, 56, 48)
            }
            canvas.drawText(line.take(92), 42f, y, paint)
            y += if (line.isBlank()) 16f else 24f
        }
        document.finishPage(page)
    }
    context.contentResolver.openOutputStream(uri)?.use { output -> document.writeTo(output) }
    document.close()
}

private fun writeReportAsJpeg(context: Context, uri: Uri, report: ReportBundle) {
    val width = 1200
    val height = (report.lines.size * 38 + 180).coerceAtLeast(1600)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.rgb(245, 249, 247))
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(35, 56, 48)
        textSize = 25f
    }
    var y = 70f
    report.lines.forEachIndexed { index, line ->
        paint.textSize = when {
            index == 0 -> 36f
            line == "SUMMARY" || line == "TOP APP TIME" || line == "DAILY PATTERN" -> 24f
            else -> 18f
        }
        paint.color = if (line == "SUMMARY" || line == "TOP APP TIME" || line == "DAILY PATTERN") {
            android.graphics.Color.rgb(8, 122, 88)
        } else {
            android.graphics.Color.rgb(35, 56, 48)
        }
        canvas.drawText(line.take(105), 48f, y, paint)
        y += if (line.isBlank()) 25f else 38f
    }
    context.contentResolver.openOutputStream(uri)?.use { output ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 94, output)
    }
    bitmap.recycle()
}

@Composable
private fun UsageAccessScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenGame: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val historyStore = remember(context) { EyeGuardianStore(context) }
    var hasUsageAccess by remember { mutableStateOf(usageAccessGranted(context)) }
    var snapshot by remember { mutableStateOf<UsageSnapshot?>(null) }
    var habitAnalysis by remember { mutableStateOf<HabitAnalysis?>(null) }
    var gamificationState by remember { mutableStateOf<GamificationState?>(null) }
    var refreshRequest by remember { mutableStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    var lastUpdatedAt by remember { mutableStateOf<Long?>(null) }
    val recentCareHistory = remember(historyStore, refreshRequest) {
        buildList {
            historyStore.readBreakRecords().forEach { record ->
                add(
                    CareHistoryItem(
                        timestampMillis = record.timestampMillis,
                        title = if (record.completed) "Break completed" else "Break skipped",
                        detail = resolveAppLabel(context, record.packageName),
                        completed = record.completed
                    )
                )
            }
            historyStore.readTwentyTwentyRecords().forEach { record ->
                add(
                    CareHistoryItem(
                        timestampMillis = record.timestampMillis,
                        title = if (record.completed) "20/20 reset completed" else "20/20 reset snoozed",
                        detail = "Eye reminder",
                        completed = record.completed
                    )
                )
            }
        }.sortedByDescending { it.timestampMillis }.take(8)
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsageAccess = usageAccessGranted(context)
                refreshRequest++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(hasUsageAccess, refreshRequest) {
        if (hasUsageAccess) {
            isRefreshing = true
            val loadedSnapshot = withContext(Dispatchers.IO) { readTodayUsage(context) }
            // Show the part users care about first; the habit and rewards layers
            // can continue loading without blocking the usage dashboard.
            snapshot = loadedSnapshot
            val loadedAnalysis = withContext(Dispatchers.IO) {
                historyStore.saveUsage(loadedSnapshot.toDailyUsageRecord())
                buildHabitAnalysis(context, historyStore)
            }
            val loadedGamification = withContext(Dispatchers.IO) {
                buildGamificationState(historyStore)
            }
            habitAnalysis = loadedAnalysis
            gamificationState = loadedGamification
            lastUpdatedAt = System.currentTimeMillis()
            isRefreshing = false
        } else {
            snapshot = null
            habitAnalysis = null
            gamificationState = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(appColor(0xFFEAF8F3), appColor(0xFFE8F3FF), appColor(0xFFFFF1EA))
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onBack,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = appColor(0xFF2E7D55)
                )
            ) {
                Text("‹")
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Activity dashboard",
                    style = MaterialTheme.typography.headlineSmall,
                    color = appColor(0xFF163B2A)
                )
                Text(
                    text = "Understand your screen patterns",
                    style = MaterialTheme.typography.bodySmall,
                    color = appColor(0xFF718078)
                )
            }
        }

        Text(
            text = "This layer reads Android's aggregated foreground-app durations so Eye Guardian can understand your screen habits. It cannot read messages, page contents, or what you do inside an app.",
            style = MaterialTheme.typography.bodyMedium,
            color = appColor(0xFF5F6D65)
        )

        if (!hasUsageAccess) {
            Card(
            border = glassBorder(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = appColor(0xFFFFFFFF))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Usage Access is off",
                        style = MaterialTheme.typography.titleMedium,
                        color = appColor(0xFF163B2A)
                    )
                    Text(
                        text = "Android protects this information behind a special Settings permission. Tap below, choose Eye Guardian, turn on Allow usage tracking, then return here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = appColor(0xFF5F6D65)
                    )
                    Button(onClick = {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }) {
                        Text("Grant usage access")
                    }
                }
            }
        } else if (snapshot == null) {
            Text(
                text = "Loading today's usage…",
                style = MaterialTheme.typography.bodyLarge,
                color = appColor(0xFF5F6D65)
            )
        } else {
            val currentSnapshot = snapshot ?: return@Column
            Card(
            border = glassBorder(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = appColor(0xFFE4F2E9))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Today's user-app time",
                        style = MaterialTheme.typography.titleMedium,
                        color = appColor(0xFF163B2A)
                    )
                    Text(
                        text = formatUsageDuration(currentSnapshot.totalMinutes),
                        style = MaterialTheme.typography.headlineMedium,
                        color = appColor(0xFF2E7D55)
                    )
                    Text(
                        text = "Excludes Eye Guardian, the launcher, and Android system components.",
                        style = MaterialTheme.typography.bodySmall,
                        color = appColor(0xFF5F6D65)
                    )
                }
            }

            Card(
            border = glassBorder(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = appColor(0xFFFFFFFF))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Most-used apps today",
                        style = MaterialTheme.typography.titleMedium,
                        color = appColor(0xFF163B2A)
                    )
                    if (currentSnapshot.apps.isEmpty()) {
                        Text(
                            text = "No app usage has been reported yet. Use a few apps, then refresh.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = appColor(0xFF5F6D65)
                        )
                    } else {
                        currentSnapshot.apps.forEach { app ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = app.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = appColor(0xFF163B2A)
                                )
                                Text(
                                    text = formatUsageDuration(app.minutes),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = appColor(0xFF5F6D65)
                                )
                            }
                        }
                    }
                }
            }

            Card(
            border = glassBorder(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = appColor(0xFFFFFFFF))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Today's activity timeline",
                        style = MaterialTheme.typography.titleMedium,
                        color = appColor(0xFF163B2A)
                    )
                    InteractiveUsageChart(
                        values = currentSnapshot.hourlyMinutes,
                        axisLabels = hourlyAxisLabels(currentSnapshot.hourlyMinutes.size),
                        pointLabel = { formatHour(it) }
                    )
                    Text(
                        text = "Future hours are hidden. This day's data is still in progress.",
                        style = MaterialTheme.typography.bodySmall,
                        color = appColor(0xFF718078)
                    )
                    Text(
                        text = "${currentSnapshot.sessionCount} activity blocks · longest block ${formatUsageDuration(currentSnapshot.longestSessionMinutes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = appColor(0xFF5F6D65)
                    )
                }
            }

            Card(
            border = glassBorder(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = appColor(0xFFFFFFFF))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Seven-day pattern",
                        style = MaterialTheme.typography.titleMedium,
                        color = appColor(0xFF163B2A)
                    )
                    InteractiveUsageChart(
                        values = currentSnapshot.dailyMinutes,
                        axisLabels = currentSnapshot.dailyLabels,
                        pointLabel = { currentSnapshot.dailyLabels[it] }
                    )
                    Text(
                        text = buildUsageInsight(currentSnapshot),
                        style = MaterialTheme.typography.bodyMedium,
                        color = appColor(0xFF5F6D65)
                    )
                    Text(
                        text = "Today's point is partial and will increase as the day continues.",
                        style = MaterialTheme.typography.bodySmall,
                        color = appColor(0xFF718078)
                    )
                }
            }

            if (habitAnalysis == null && isRefreshing) {
                Card(
                    border = glassBorder(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = appColor(0xFFEAF4EE))
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = appColor(0xFF2E7D55)
                        )
                        Text(
                            text = "Preparing patterns and recommendations…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = appColor(0xFF5F6D65)
                        )
                    }
                }
            } else {
                habitAnalysis?.let { analysis -> HabitEngineCard(analysis) }
            }

            CareHistoryCard(recentCareHistory)

            gamificationState?.let { state ->
                RewardsCard(state, onOpenGame)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = { refreshRequest++ },
                    enabled = !isRefreshing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = appColor(0xFF2E7D55),
                        contentColor = Color.White
                    )
                ) {
                    Text(if (isRefreshing) "Updating…" else "Update activity data")
                }
                Text(
                    text = lastUpdatedAt?.let { "Updated ${formatUpdatedTime(it)}" } ?: "Not updated yet",
                    style = MaterialTheme.typography.labelSmall,
                    color = appColor(0xFF718078)
                )
            }
        }

        Text(
            text = "Privacy note: daily usage blocks and distance-warning timestamps stay on this device. Eye Guardian does not read messages, page contents, or what you do inside an app.",
            style = MaterialTheme.typography.bodySmall,
            color = appColor(0xFF718078)
        )
    }
}

@Composable
private fun FocusTrailGameScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val historyStore = remember(context) { EyeGuardianStore(context) }
    val targetPositions = remember {
        listOf(
            28.dp to 28.dp,
            218.dp to 34.dp,
            112.dp to 96.dp,
            36.dp to 166.dp,
            236.dp to 188.dp,
            128.dp to 252.dp,
            20.dp to 282.dp,
            220.dp to 292.dp
        )
    }
    var selectedDifficulty by remember { mutableStateOf(GameDifficulty.MEDIUM) }
    var isRunning by remember { mutableStateOf(false) }
    var gameComplete by remember { mutableStateOf(false) }
    var secondsRemaining by remember { mutableStateOf(selectedDifficulty.durationSeconds) }
    var score by remember { mutableStateOf(0) }
    var combo by remember { mutableStateOf(0) }
    var targetIndex by remember { mutableStateOf(0) }
    var targetPatternOffset by remember { mutableStateOf(0) }
    var allResults by remember { mutableStateOf(historyStore.readGameResults()) }
    var selectedBallId by remember { mutableStateOf(historyStore.getSelectedFocusBallId()) }
    var leaderboard by remember {
        mutableStateOf(
            historyStore.readGameResults()
                .sortedByDescending { it.score }
                .take(5)
        )
    }
    var confettiBurstId by remember { mutableStateOf(0) }
    var showConfetti by remember { mutableStateOf(false) }
    val currentScore by rememberUpdatedState(score)
    val unlockedBalls = unlockedFocusBalls(allResults)
    val selectedBall = FOCUS_BALLS.firstOrNull { it.id == selectedBallId }
        ?.takeIf { it in unlockedBalls }
        ?: unlockedBalls.first()
    val nextBall = FOCUS_BALLS.firstOrNull { it !in unlockedBalls }
    val patternName = when (targetPatternOffset % 3) {
        0 -> "Orbit path"
        1 -> "Zig-zag path"
        else -> "Cross-screen path"
    }

    LaunchedEffect(isRunning, selectedDifficulty) {
        if (isRunning) {
            while (secondsRemaining > 0) {
                kotlinx.coroutines.delay(1_000L)
                secondsRemaining -= 1
            }
            if (isRunning && !gameComplete) {
                val previousBest = historyStore.readGameResults().maxOfOrNull { it.score } ?: 0
                historyStore.recordGameResult(currentScore, selectedDifficulty)
                allResults = historyStore.readGameResults()
                leaderboard = allResults
                    .sortedByDescending { it.score }
                    .take(5)
                if (currentScore > previousBest) {
                    confettiBurstId += 1
                    showConfetti = true
                }
                gameComplete = true
                isRunning = false
            }
        }
    }

    LaunchedEffect(confettiBurstId) {
        if (confettiBurstId > 0) {
            kotlinx.coroutines.delay(1_600L)
            showConfetti = false
        }
    }

    LaunchedEffect(isRunning, selectedDifficulty) {
        if (isRunning) {
            while (secondsRemaining > 0) {
                kotlinx.coroutines.delay(selectedDifficulty.movementMillis)
                if (isRunning) {
                    targetIndex = (targetIndex + 1) % targetPositions.size
                }
            }
        }
    }

    fun startGame() {
        score = 0
        combo = 0
        targetIndex = 0
        targetPatternOffset = kotlin.random.Random.nextInt(targetPositions.size)
        secondsRemaining = selectedDifficulty.durationSeconds
        gameComplete = false
        isRunning = true
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(appColor(0xFFF4F0FF), appColor(0xFFE9F5FF), appColor(0xFFFFF1F5))
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onBack) { Text("‹") }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Focus Trail",
                    style = MaterialTheme.typography.headlineSmall,
                    color = appColor(0xFF163B2A)
                )
                Text(
                    text = "A short visual reset challenge",
                    style = MaterialTheme.typography.bodySmall,
                    color = appColor(0xFF718078)
                )
            }
        }

        Card(
            border = glassBorder(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = appColor(0xFFEAF4EE).copy(alpha = .90f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Choose your focus ball", style = MaterialTheme.typography.titleMedium, color = appColor(0xFF163B2A))
                        Text(
                            "${allResults.size} plays · best ${allResults.maxOfOrNull { it.score } ?: 0} points",
                            style = MaterialTheme.typography.bodySmall,
                            color = appColor(0xFF5F6D65)
                        )
                    }
                    Text(selectedBall.name, style = MaterialTheme.typography.labelLarge, color = appColor(0xFF2E7D55))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FOCUS_BALLS.forEach { ball ->
                        val isUnlocked = ball in unlockedBalls
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(70.dp)
                                .clickable(enabled = isUnlocked && !isRunning) {
                                    selectedBallId = ball.id
                                    historyStore.setSelectedFocusBallId(ball.id)
                                },
                            shape = RoundedCornerShape(16.dp),
                            border = if (ball.id == selectedBall.id) glassBorder() else null,
                            colors = CardDefaults.cardColors(
                                containerColor = if (ball.id == selectedBall.id) appColor(0xFFE4F2E9)
                                else appColor(0xFFFFFFFF).copy(alpha = .58f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Brush.radialGradient(listOf(Color(ball.startColor), Color(ball.endColor))))
                                )
                                Text(
                                    if (isUnlocked) ball.name else "Locked",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = appColor(0xFF163B2A)
                                )
                            }
                        }
                    }
                }
                nextBall?.let { ball ->
                    Text(
                        "Next unlock: ${ball.name} after ${ball.requiredPlays} plays or a ${ball.requiredBestScore}-point best score.",
                        style = MaterialTheme.typography.bodySmall,
                        color = appColor(0xFF5F6D65)
                    )
                } ?: Text(
                    "All focus balls unlocked · nice work!",
                    style = MaterialTheme.typography.bodySmall,
                    color = appColor(0xFF2E7D55)
                )
            }
        }

        Card(
            border = glassBorder(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = appColor(0xFFE4EFFF).copy(alpha = .86f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Tap the glowing target before it moves. Build a combo for bonus points.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = appColor(0xFF163B2A)
                )
                Text(
                    text = "${selectedDifficulty.durationSeconds} seconds · $patternName · target every ${selectedDifficulty.movementMillis / 1_000L}s · +15 XP",
                    style = MaterialTheme.typography.bodySmall,
                    color = appColor(0xFF5F6D65)
                )
            }
        }

        Text(
            text = "Choose level",
            style = MaterialTheme.typography.titleMedium,
            color = appColor(0xFF163B2A)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GameDifficulty.entries.forEach { difficulty ->
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        selectedDifficulty = difficulty
                        secondsRemaining = difficulty.durationSeconds
                    },
                    enabled = !isRunning,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selectedDifficulty == difficulty) appColor(0xFFE4F2E9) else Color.Transparent,
                        contentColor = appColor(0xFF2E7D55)
                    )
                ) {
                    Text(difficulty.label)
                }
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        listOf(appColor(0xFF122B4A), appColor(0xFF263B72), appColor(0xFF452D68))
                    )
                ),
            contentAlignment = if (isRunning) Alignment.TopStart else Alignment.Center
        ) {
            if (isRunning) {
                val targetPosition = targetPositions[(targetIndex + targetPatternOffset) % targetPositions.size]
                val targetSize = when (combo % 3) {
                    0 -> selectedDifficulty.baseTargetSize.dp
                    1 -> (selectedDifficulty.baseTargetSize - 8).coerceAtLeast(40).dp
                    else -> (selectedDifficulty.baseTargetSize + 8).dp
                }
                val maxTargetX = (maxWidth - targetSize - 8.dp).coerceAtLeast(8.dp)
                val maxTargetY = (maxHeight - targetSize - 8.dp).coerceAtLeast(8.dp)
                val targetX = targetPosition.first.coerceIn(8.dp, maxTargetX)
                val targetY = targetPosition.second.coerceIn(8.dp, maxTargetY)
                Box(
                    modifier = Modifier
                        .offset(x = targetX, y = targetY)
                        .size(targetSize.coerceAtLeast(48.dp))
                        .clip(RoundedCornerShape(40.dp))
                        .background(
                            Brush.radialGradient(
                                listOf(Color(selectedBall.startColor), Color(selectedBall.endColor))
                            )
                        )
                        .clickable {
                            combo += 1
                            score += 1 + combo / 5
                            targetIndex = (targetIndex + 1) % targetPositions.size
                        }
                        .semantics {
                            contentDescription = "Focus Trail target"
                            role = Role.Button
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "✦", style = MaterialTheme.typography.headlineMedium, color = appColor(0xFF5D4300))
                }
                Text(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
                    text = "$secondsRemaining s · $score points · $combo combo",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (gameComplete) "Trail complete: $score taps" else "Ready when you are",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Button(
                        onClick = { startGame() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = appColor(0xFFFFC857),
                            contentColor = appColor(0xFF5D4300)
                        )
                    ) {
                        Text(if (gameComplete) "Play again" else "Start Focus Trail")
                    }
                }
            }
            if (showConfetti) {
                ConfettiOverlay(
                    modifier = Modifier.matchParentSize(),
                    burstId = confettiBurstId
                )
            }
        }

        Card(
            border = glassBorder(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = appColor(0xFFFFFFFF).copy(alpha = .76f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Focus Trail leaderboard",
                    style = MaterialTheme.typography.titleMedium,
                    color = appColor(0xFF163B2A)
                )
                if (leaderboard.isEmpty()) {
                    Text(
                        text = "Your completed scores will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = appColor(0xFF5F6D65)
                    )
                } else {
                    leaderboard.forEachIndexed { index, result ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${index + 1}. ${result.difficulty}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = appColor(0xFF163B2A)
                            )
                            Text(
                                text = "${result.score} pts",
                                style = MaterialTheme.typography.bodyMedium,
                                color = appColor(0xFF8A5200)
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = "This is a playful awareness exercise, not a vision test or medical treatment.",
            style = MaterialTheme.typography.bodySmall,
            color = appColor(0xFF718078)
        )
    }
}

private data class ConfettiPiece(
    val startX: Float,
    val delay: Float,
    val drift: Float,
    val color: Color
)

@Composable
private fun ConfettiOverlay(
    modifier: Modifier = Modifier,
    burstId: Int
) {
    val confettiColors = listOf(
        Color(0xFFFFC857),
        Color(0xFF62A57D),
        Color(0xFFE57373),
        Color(0xFF64B5F6),
        Color(0xFFBA68C8)
    )
    val pieces = remember {
        List(42) { index ->
            ConfettiPiece(
                startX = ((index * 37) % 100) / 100f,
                delay = ((index * 11) % 35) / 100f,
                drift = ((index % 7) - 3) * 10f,
                color = confettiColors[index % confettiColors.size]
            )
        }
    }
    var progress by remember(burstId) { mutableStateOf(0f) }

    LaunchedEffect(burstId) {
        for (step in 0..36) {
            progress = step / 36f
            kotlinx.coroutines.delay(35L)
        }
    }

    Canvas(modifier = modifier) {
        pieces.forEach { piece ->
            val pieceProgress = ((progress - piece.delay) / (1f - piece.delay)).coerceIn(0f, 1f)
            val x = piece.startX * size.width +
                kotlin.math.sin(pieceProgress * 8f + piece.startX * 10f) * piece.drift
            val y = pieceProgress * (size.height + 40f) - 20f
            drawRect(
                color = piece.color,
                topLeft = Offset(x, y),
                size = Size(7.dp.toPx(), 13.dp.toPx())
            )
        }
    }
}

@Composable
private fun HabitEngineCard(analysis: HabitAnalysis) {
    Card(
            border = glassBorder(),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = appColor(0xFFEAF4EE))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Habit engine",
                style = MaterialTheme.typography.titleMedium,
                color = appColor(0xFF163B2A)
            )
            Text(
                text = "Today: ${analysis.longBlockCountToday} long app block${if (analysis.longBlockCountToday == 1) "" else "s"} · ${analysis.warningCountToday} distance warning${if (analysis.warningCountToday == 1) "" else "s"}",
                style = MaterialTheme.typography.bodyMedium,
                color = appColor(0xFF2E7D55)
            )
            Text(
                text = "Last 7 days: ${analysis.warningsLastSevenDays} warnings · ${analysis.completedBreaksToday} breaks · ${analysis.completedResetsToday} 20/20 resets today",
                style = MaterialTheme.typography.bodySmall,
                color = appColor(0xFF5F6D65)
            )
            if (analysis.skippedBreaksToday > 0) {
                Text(
                    text = "Skipped today: ${analysis.skippedBreaksToday}",
                    style = MaterialTheme.typography.bodySmall,
                    color = appColor(0xFF8A5200)
                )
            }
            if (analysis.warningsAssociatedWithLongBlocks > 0) {
                Text(
                    text = "${analysis.warningsAssociatedWithLongBlocks} warning${if (analysis.warningsAssociatedWithLongBlocks == 1) " was" else "s were"} associated with a long app block.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = appColor(0xFF8A5200)
                )
            }
            analysis.strongestAppLabel?.let { appLabel ->
                Text(
                    text = "Most noticeable pattern: $appLabel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = appColor(0xFF5F6D65)
                )
            }
            analysis.recommendations.forEach { recommendation ->
                Text(
                    text = "• $recommendation",
                    style = MaterialTheme.typography.bodyMedium,
                    color = appColor(0xFF163B2A)
                )
            }
            Text(
                text = "Recommendations are based on your saved device activity, not on app content.",
                style = MaterialTheme.typography.bodySmall,
                color = appColor(0xFF718078)
            )
        }
    }
}

@Composable
private fun CareHistoryCard(items: List<CareHistoryItem>) {
    Card(
            border = glassBorder(),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = appColor(0xFFFFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Recent care history",
                style = MaterialTheme.typography.titleMedium,
                color = appColor(0xFF163B2A)
            )
            if (items.isEmpty()) {
                Text(
                    text = "Completed and skipped reminders will appear here as you use Eye Guardian.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = appColor(0xFF5F6D65)
                )
            } else {
                items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (item.completed) "✓" else "–",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (item.completed) appColor(0xFF2E7D55) else appColor(0xFF8A5200)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = appColor(0xFF163B2A)
                            )
                            Text(
                                text = item.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = appColor(0xFF718078)
                            )
                        }
                        Text(
                            text = formatHistoryTimestamp(item.timestampMillis),
                            style = MaterialTheme.typography.labelSmall,
                            color = appColor(0xFF718078)
                        )
                    }
                }
            }
            Text(
                text = "This history stays on your device and is used to personalize recommendations.",
                style = MaterialTheme.typography.bodySmall,
                color = appColor(0xFF718078)
            )
        }
    }
}

@Composable
private fun RewardsCard(
    state: GamificationState,
    onOpenGame: () -> Unit
) {
    val xpIntoLevel = state.xp % 100
    Card(
            border = glassBorder(),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = appColor(0xFFEDE8FF).copy(alpha = .90f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Rewards & streaks",
                    style = MaterialTheme.typography.titleMedium,
                    color = appColor(0xFF5D4300)
                )
                Text(
                    text = "Level ${state.level}",
                    style = MaterialTheme.typography.labelLarge,
                    color = appColor(0xFF8A5200)
                )
            }
            Text(
                text = "${state.xp} XP · $xpIntoLevel/100 to the next level",
                style = MaterialTheme.typography.bodyLarge,
                color = appColor(0xFF5D4300)
            )
            Text(
                text = "Current care streak: ${state.currentStreakDays} day${if (state.currentStreakDays == 1) "" else "s"}",
                style = MaterialTheme.typography.bodyMedium,
                color = appColor(0xFF5D4300)
            )
            Text(
                text = "Completed screen breaks: ${state.completedBreaks} · +20 XP each",
                style = MaterialTheme.typography.bodyMedium,
                color = appColor(0xFF5D4300)
            )
            Text(
                text = "20/20 resets: ${state.completedTwentyTwentyResets} · +20 XP each",
                style = MaterialTheme.typography.bodyMedium,
                color = appColor(0xFF5D4300)
            )
            Text(
                text = "Focus Trail: ${state.gamesPlayed} plays · best score ${state.bestGameScore}",
                style = MaterialTheme.typography.bodyMedium,
                color = appColor(0xFF5D4300)
            )
            Text(
                text = if (state.todayChallengeComplete) {
                    "Today's challenge complete: activity check-in +10 XP"
                } else {
                    "Today's challenge: refresh activity data to complete your check-in"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.todayChallengeComplete) appColor(0xFF2E7D55) else appColor(0xFF8A5200)
            )
            if (state.achievements.isNotEmpty()) {
                Text(
                    text = "Achievements: ${state.achievements.joinToString(" · ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = appColor(0xFF5D4300)
                )
            }
            OutlinedButton(
                onClick = onOpenGame,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = appColor(0xFF8A5200))
            ) {
                Text("Play Focus Trail")
            }
            Text(
                text = "XP rewards awareness actions and check-ins; it is not a medical score.",
                style = MaterialTheme.typography.bodySmall,
                color = appColor(0xFF806D3A)
            )
        }
    }
}

@Composable
private fun InteractiveUsageChart(
    values: List<Long>,
    axisLabels: List<String>,
    pointLabel: (Int) -> String
) {
    val isDarkTheme = LocalEyeGuardDark.current
    val maxValue = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val chartHorizontalPadding = with(LocalDensity.current) { 18.dp.toPx() }
    var selectedIndex by remember(values, axisLabels) { mutableStateOf<Int?>(null) }
    var isDragging by remember(values, axisLabels) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(196.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.linearGradient(
                    if (isDarkTheme) {
                        listOf(Color(0xFF14263B), Color(0xFF234E86))
                    } else {
                        listOf(Color(0xFFEAF4EE), Color(0xFFDDEEFF))
                    }
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .pointerInput(values) {
                detectDragGestures(
                    onDragStart = { position ->
                        isDragging = true
                        selectedIndex = indexForChartX(
                            position.x,
                            size.width.toFloat(),
                            values.size,
                            chartHorizontalPadding
                        )
                    },
                    onDrag = { change, _ ->
                        selectedIndex = indexForChartX(
                            change.position.x,
                            size.width.toFloat(),
                            values.size,
                            chartHorizontalPadding
                        )
                    },
                    onDragEnd = {
                        isDragging = false
                        selectedIndex = null
                    },
                    onDragCancel = {
                        isDragging = false
                        selectedIndex = null
                    }
                )
            }
    ) {
        val chartFillColor = appColor(0xFF43B7FF).copy(alpha = .30f)
        val chartLineColors = listOf(
            appColor(0xFF4DD7FF),
            appColor(0xFFB37CFF),
            appColor(0xFFFF8EBC)
        )
        val chartGlowColor = appColor(0xFF52C9FF).copy(alpha = .20f)
        val chartPointColor = appColor(0xFF63D8FF)
        val chartSelectionColor = appColor(0xFFFFD166)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val horizontalPadding = 18.dp.toPx()
            val verticalPadding = 22.dp.toPx()
            val chartWidth = size.width - horizontalPadding * 2f
            val chartHeight = size.height - verticalPadding * 2f
            val pointStep = chartWidth / (values.size - 1).coerceAtLeast(1)
            val points = values.mapIndexed { index, value ->
                Offset(
                    x = horizontalPadding + pointStep * index,
                    y = verticalPadding + chartHeight * (1f - value.toFloat() / maxValue.toFloat())
                )
            }

            for (lineIndex in 0..4) {
                val y = verticalPadding + chartHeight * (lineIndex / 4f)
                drawLine(
                    color = if (isDarkTheme) {
                        Color.White.copy(alpha = if (lineIndex == 4) .24f else .10f)
                    } else {
                        Color(0xFF31556B).copy(alpha = if (lineIndex == 4) .22f else .10f)
                    },
                    start = Offset(horizontalPadding, y),
                    end = Offset(size.width - horizontalPadding, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            if (points.size > 1) {
                val fillPath = Path().apply {
                    moveTo(points.first().x, size.height - verticalPadding)
                    lineTo(points.first().x, points.first().y)
                    points.drop(1).forEach { point -> lineTo(point.x, point.y) }
                    lineTo(points.last().x, size.height - verticalPadding)
                    close()
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(chartFillColor, Color.Transparent),
                        startY = verticalPadding,
                        endY = size.height - verticalPadding
                    )
                )

                val linePath = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { point -> lineTo(point.x, point.y) }
                }
                drawPath(
                    path = linePath,
                    brush = Brush.horizontalGradient(
                        chartLineColors
                    ),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            points.forEach { point ->
                drawCircle(
                    color = chartGlowColor,
                    radius = 12.dp.toPx(),
                    center = point
                )
                drawCircle(
                    color = chartPointColor,
                    radius = 6.dp.toPx(),
                    center = point
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.5.dp.toPx(),
                    center = point
                )
            }

            if (isDragging) selectedIndex?.let { index ->
                val point = points[index]
                drawLine(
                    color = chartSelectionColor,
                    start = Offset(point.x, verticalPadding),
                    end = Offset(point.x, size.height - verticalPadding),
                    strokeWidth = 2.dp.toPx()
                )
                drawCircle(
                    color = chartSelectionColor.copy(alpha = .30f),
                    radius = 15.dp.toPx(),
                    center = point
                )
                drawCircle(
                    color = chartSelectionColor,
                    radius = 8.dp.toPx(),
                    center = point
                )
                drawCircle(color = Color.White, radius = 3.dp.toPx(), center = point)
            }
        }
    }

    if (isDragging) selectedIndex?.let { index ->
        Text(
            text = "${pointLabel(index)} · ${formatUsageDuration(values[index])}",
            style = MaterialTheme.typography.bodyMedium,
            color = appColor(0xFF9B5B00)
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        axisLabels.forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = appColor(0xFF62777F)
            )
        }
    }
}

private fun indexForChartX(
    x: Float,
    width: Float,
    count: Int,
    horizontalPadding: Float
): Int {
    if (count <= 1 || width <= 0f) return 0
    val chartWidth = (width - horizontalPadding * 2f).coerceAtLeast(1f)
    return (((x - horizontalPadding) / chartWidth) * (count - 1))
        .toInt()
        .coerceIn(0, count - 1)
}

private fun buildUsageInsight(snapshot: UsageSnapshot): String {
    val peakHour = snapshot.hourlyMinutes.indices.maxByOrNull { snapshot.hourlyMinutes[it] }
    val peakText = if (peakHour != null && snapshot.hourlyMinutes[peakHour] > 0L) {
        "Your busiest period was around ${formatHour(peakHour)}."
    } else {
        "There is not enough activity data for a daily pattern yet."
    }
    val longestText = if (snapshot.longestSessionMinutes >= 20L) {
        " Your longest session was ${formatUsageDuration(snapshot.longestSessionMinutes)}; a short break after long sessions may help."
    } else {
        " Your longest session was under 20 minutes."
    }
    return peakText + longestText
}

private fun formatHour(hour: Int): String {
    val suffix = if (hour < 12) "AM" else "PM"
    val displayHour = when (val twelveHour = hour % 12) {
        0 -> 12
        else -> twelveHour
    }
    return "$displayHour $suffix"
}

private fun hourlyAxisLabels(hourCount: Int): List<String> {
    return when {
        hourCount <= 6 -> listOf("12a", "now")
        hourCount <= 12 -> listOf("12a", "6a", "now")
        else -> listOf("12a", "6a", "12p", "now")
    }
}

private fun usageAccessGranted(context: Context): Boolean {
    val appOps = context.getSystemService(AppOpsManager::class.java)
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun readTodayUsage(context: Context): UsageSnapshot {
    val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)
    val ignoredPackages = ignoredUsagePackages(context)
    val startOfDay = startOfDayMillis(0)
    val now = System.currentTimeMillis()

    val foregroundIntervals = clipForegroundIntervals(
        readForegroundIntervals(
            usageStatsManager = usageStatsManager,
            startTime = (startOfDay - DAY_LOOKBACK_MILLIS).coerceAtLeast(0L),
            endTime = now,
            ignoredPackages = ignoredPackages
        ),
        startOfDay,
        now
    ).let(::mergeShortForegroundGaps)

    val durationByPackage = foregroundIntervals
        .groupBy { it.packageName }
        .mapValues { (_, intervals) -> intervals.sumOf { it.end - it.start } }
        .entries
        .sortedByDescending { it.value }

    val totalMinutes = millisToDisplayMinutes(durationByPackage.sumOf { it.value })
    val topApps = durationByPackage.take(5).map { entry ->
        UsageAppRow(
            packageName = entry.key,
            label = resolveAppLabel(context, entry.key),
            minutes = millisToDisplayMinutes(entry.value)
        )
    }

    val currentHour = Calendar.getInstance().apply { timeInMillis = now }
        .get(Calendar.HOUR_OF_DAY)
    val hourlyMillis = MutableList(currentHour + 1) { 0L }
    foregroundIntervals.forEach { interval ->
        addIntervalToHourlyBuckets(hourlyMillis, interval.start, interval.end, startOfDay)
    }
    // Keep each bucket in whole minutes without rounding every bucket up.
    // Rounding each bucket independently can make a chart claim more time
    // than the aggregate total actually contains.
    val hourlyMinutes = hourlyMillis.map { it / MINUTE_MILLIS }

    val dailyMinutes = mutableListOf<Long>()
    val dailyLabels = mutableListOf<String>()
    val dayFormatter = SimpleDateFormat("EEE d", Locale.getDefault())
    for (daysAgo in 6 downTo 0) {
        val dayStart = startOfDayMillis(daysAgo)
        val dayEnd = if (daysAgo == 0) now else startOfDayMillis(daysAgo - 1)
        val dayIntervals = clipForegroundIntervals(
            readForegroundIntervals(
                usageStatsManager = usageStatsManager,
                startTime = (dayStart - DAY_LOOKBACK_MILLIS).coerceAtLeast(0L),
                endTime = dayEnd,
                ignoredPackages = ignoredPackages
            ),
            dayStart,
            dayEnd
        ).let(::mergeShortForegroundGaps)
        dailyMinutes += millisToDisplayMinutes(dayIntervals.sumOf { it.end - it.start })
        dailyLabels += dayFormatter.format(java.util.Date(dayStart))
    }

    val longestSessionMinutes = foregroundIntervals
        .maxOfOrNull { millisToDisplayMinutes(it.end - it.start) } ?: 0L

    return UsageSnapshot(
        totalMinutes = totalMinutes,
        apps = topApps,
        appMinutes = durationByPackage.associate { it.key to millisToDisplayMinutes(it.value) },
        hourlyMinutes = hourlyMinutes,
        dailyMinutes = dailyMinutes,
        dailyLabels = dailyLabels,
        sessionCount = foregroundIntervals.size,
        longestSessionMinutes = longestSessionMinutes,
        foregroundBlocks = foregroundIntervals.map {
            UsageBlockRecord(
                packageName = it.packageName,
                startMillis = it.start,
                endMillis = it.end
            )
        }
    )
}

private fun UsageSnapshot.toDailyUsageRecord(
    capturedAtMillis: Long = System.currentTimeMillis()
): DailyUsageRecord {
    return DailyUsageRecord(
        dateKey = dayKey(capturedAtMillis),
        totalMinutes = totalMinutes,
        appMinutes = appMinutes,
        hourlyMinutes = hourlyMinutes,
        activityBlockCount = sessionCount,
        longestBlockMinutes = longestSessionMinutes,
        blocks = foregroundBlocks,
        capturedAtMillis = capturedAtMillis
    )
}

private fun buildHabitAnalysis(
    context: Context,
    store: EyeGuardianStore,
    nowMillis: Long = System.currentTimeMillis()
): HabitAnalysis {
    val todayKey = dayKey(nowMillis)
    val usageRecords = store.readUsageRecords()
    val warningRecords = store.readWarningRecords()
    val todayUsage = usageRecords.firstOrNull { it.dateKey == todayKey }
    val todayWarnings = warningRecords.filter { it.dateKey == todayKey }
    val longBlockThresholdMillis = 20L * 60L * 1_000L
    val longBlocks = todayUsage?.blocks.orEmpty().filter {
        it.durationMillis >= longBlockThresholdMillis
    }
    val allLongBlocks = usageRecords.flatMap { it.blocks }.filter {
        it.durationMillis >= longBlockThresholdMillis
    }
    val sevenDaysMillis = 7L * 24L * 60L * 60L * 1_000L
    val warningsLastSevenDays = warningRecords.count {
        it.timestampMillis in (nowMillis - sevenDaysMillis)..nowMillis
    }
    val warningsPreviousSevenDays = warningRecords.count {
        it.timestampMillis in (nowMillis - 2L * sevenDaysMillis)..(nowMillis - sevenDaysMillis)
    }
    val warningMatchesByPackage = mutableMapOf<String, Int>()
    var associatedWarnings = 0

    todayWarnings.forEach { warning ->
        val associatedBlock = longBlocks
            .filter { block ->
                val duringBlock = warning.timestampMillis in block.startMillis..block.endMillis
                val shortlyAfterBlock = warning.timestampMillis >= block.endMillis &&
                    warning.timestampMillis - block.endMillis <= 15L * 60L * 1_000L
                duringBlock || shortlyAfterBlock
            }
            .maxByOrNull { it.endMillis }
        if (associatedBlock != null) {
            associatedWarnings++
            warningMatchesByPackage[associatedBlock.packageName] =
                (warningMatchesByPackage[associatedBlock.packageName] ?: 0) + 1
        }
    }

    warningRecords
        .filter { it.timestampMillis in (nowMillis - sevenDaysMillis)..nowMillis }
        .forEach { warning ->
            val associatedBlock = allLongBlocks
                .filter { block ->
                    val duringBlock = warning.timestampMillis in block.startMillis..block.endMillis
                    val shortlyAfterBlock = warning.timestampMillis >= block.endMillis &&
                        warning.timestampMillis - block.endMillis <= 15L * 60L * 1_000L
                    duringBlock || shortlyAfterBlock
                }
                .maxByOrNull { it.endMillis }
            if (associatedBlock != null) {
                warningMatchesByPackage[associatedBlock.packageName] =
                    (warningMatchesByPackage[associatedBlock.packageName] ?: 0) + 1
            }
        }

    val strongestPackage = warningMatchesByPackage.maxByOrNull { it.value }?.key
    val strongestAppLabel = strongestPackage?.let { resolveAppLabel(context, it) }
    val todayBreakRecords = store.readBreakRecords().filter { it.dateKey == todayKey }
    val completedBreaksToday = todayBreakRecords.count { it.completed }
    val skippedBreaksToday = todayBreakRecords.count { !it.completed }
    val completedResetsToday = store.readTwentyTwentyCompletedRecords()
        .count { it.dateKey == todayKey }
    val recommendations = buildList {
        if (longBlocks.isNotEmpty()) {
            add("After a ${formatUsageDuration(longBlocks.maxOf { it.durationMillis / 60_000L })} block, look at something far away for 20 seconds.")
        }
        if (associatedWarnings > 0 && strongestAppLabel != null) {
            add("$strongestAppLabel is the clearest place to try a visual break before continuing.")
        } else if (todayWarnings.isNotEmpty()) {
            add("When a distance warning appears, pause before resuming the next app session.")
        }
        if (strongestAppLabel != null && warningsLastSevenDays > 0) {
            add("Over the last seven days, $strongestAppLabel had the strongest overlap with distance warnings.")
        }
        if (warningsLastSevenDays > warningsPreviousSevenDays && warningsLastSevenDays > 0) {
            add("Distance warnings are higher than the previous seven-day period; try taking the next break before continuing.")
        } else if (warningsLastSevenDays < warningsPreviousSevenDays) {
            add("Distance warnings are trending down compared with the previous seven days. Keep the rhythm going.")
        }
        if (skippedBreaksToday > completedBreaksToday && skippedBreaksToday > 0) {
            add("You have skipped more break reminders than you completed today. Try starting the next one for a quick reset.")
        } else if (completedBreaksToday + completedResetsToday > 0) {
            add("You have completed ${completedBreaksToday + completedResetsToday} care reset${if (completedBreaksToday + completedResetsToday == 1) "" else "s"} today. That is a strong start.")
        }
        if (todayUsage != null && todayUsage.totalMinutes >= 120L && todayWarnings.isEmpty()) {
            add("You have had more than two hours of user-app time today; add a short visual break now.")
        }
        if (isEmpty()) {
            add("Keep using Distance Lab and refresh activity data so Eye Guardian can learn your pattern.")
        }
    }

    return HabitAnalysis(
        warningCountToday = todayWarnings.size,
        longBlockCountToday = longBlocks.size,
        warningsAssociatedWithLongBlocks = associatedWarnings,
        strongestAppLabel = strongestAppLabel,
        completedBreaksToday = completedBreaksToday,
        skippedBreaksToday = skippedBreaksToday,
        completedResetsToday = completedResetsToday,
        warningsLastSevenDays = warningsLastSevenDays,
        warningsPreviousSevenDays = warningsPreviousSevenDays,
        recommendations = recommendations
    )
}

private fun buildGamificationState(
    store: EyeGuardianStore,
    nowMillis: Long = System.currentTimeMillis()
): GamificationState {
    val usageRecords = store.readUsageRecords()
    val warningCount = store.readWarningRecords().size
    val completedBreaks = store.readBreakRecords().count { it.completed }
    val completedTwentyTwentyResets = store.getTwentyTwentyCompletedCount()
    val gameResults = store.readGameResults()
    val completedBreakDates = store.readBreakRecords()
        .filter { it.completed }
        .map { it.dateKey }
    val completedResetDates = store.readTwentyTwentyCompletedRecords()
        .map { it.dateKey }
    val habitDates = (completedBreakDates + completedResetDates).toSet()
    val recordDates = usageRecords.map { it.dateKey }.toSet()
    val todayKey = dayKey(nowMillis)
    var dayOffset = if (todayKey in habitDates) 0 else 1
    var currentStreak = 0
    while (dayKey(startOfDayMillis(dayOffset)) in habitDates) {
        currentStreak++
        dayOffset++
    }

    val xp = (
        usageRecords.size * 10 +
            warningCount * 5 +
            gameResults.size * 15 +
            completedBreaks * 20 +
            completedTwentyTwentyResets * 20
        )
    val achievements = buildList {
        if (usageRecords.isNotEmpty()) add("First check-in")
        if (currentStreak >= 3) add("3-day rhythm")
        if (warningCount > 0) add("Distance aware")
        if (usageRecords.size >= 7) add("Week observer")
        if (gameResults.isNotEmpty()) add("Focus explorer")
        if ((gameResults.maxOfOrNull { it.score } ?: 0) >= 10) add("Trailblazer")
        if (completedBreaks > 0) add("Break builder")
        if (completedTwentyTwentyResets > 0) add("20/20 resetter")
    }

    return GamificationState(
        xp = xp,
        level = xp / 100 + 1,
        currentStreakDays = currentStreak,
        checkInDays = usageRecords.size,
        completedBreaks = completedBreaks,
        completedTwentyTwentyResets = completedTwentyTwentyResets,
        warningCount = warningCount,
        gamesPlayed = gameResults.size,
        bestGameScore = gameResults.maxOfOrNull { it.score } ?: 0,
        achievements = achievements,
        todayChallengeComplete = todayKey in recordDates
    )
}

private fun dayKey(timestampMillis: Long): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date(timestampMillis))
}

private fun dayStartMillis(year: Int, month: Int, day: Int): Long {
    return Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, day)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun formatReportDate(timestampMillis: Long): String {
    return SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(timestampMillis))
}

private fun startOfDayMillisFromTimestamp(timestampMillis: Long): Long {
    return Calendar.getInstance().apply {
        timeInMillis = timestampMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private data class ForegroundInterval(
    val packageName: String,
    val start: Long,
    val end: Long
)

private fun startOfDayMillis(daysAgo: Int): Long {
    return Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -daysAgo)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun readForegroundIntervals(
    usageStatsManager: UsageStatsManager,
    startTime: Long,
    endTime: Long,
    ignoredPackages: Set<String>
): List<ForegroundInterval> {
    val usageEvents = runCatching {
        usageStatsManager.queryEvents(startTime, endTime)
    }.getOrNull() ?: return emptyList()

    val event = UsageEvents.Event()
    val intervals = mutableListOf<ForegroundInterval>()
    var activePackage: String? = null
    var activeStart = 0L

    fun closeActiveInterval(end: Long) {
        val packageName = activePackage
        if (packageName != null && packageName !in ignoredPackages && end > activeStart) {
            intervals += ForegroundInterval(packageName, activeStart, end)
        }
        activePackage = null
        activeStart = 0L
    }

    while (usageEvents.hasNextEvent()) {
        usageEvents.getNextEvent(event)
        val packageName = event.packageName ?: continue
        when (event.eventType) {
            UsageEvents.Event.MOVE_TO_FOREGROUND,
            UsageEvents.Event.ACTIVITY_RESUMED -> {
                if (activePackage != packageName) {
                    closeActiveInterval(event.timeStamp)
                    activePackage = packageName
                    activeStart = event.timeStamp
                }
            }
            UsageEvents.Event.MOVE_TO_BACKGROUND,
            UsageEvents.Event.ACTIVITY_PAUSED -> {
                if (activePackage == packageName) {
                    closeActiveInterval(event.timeStamp)
                }
            }
        }
    }
    if (activePackage != null) closeActiveInterval(endTime)

    return intervals
}

private fun clipForegroundIntervals(
    intervals: List<ForegroundInterval>,
    windowStart: Long,
    windowEnd: Long
): List<ForegroundInterval> {
    return intervals.mapNotNull { interval ->
        val clippedStart = maxOf(interval.start, windowStart)
        val clippedEnd = minOf(interval.end, windowEnd)
        if (clippedEnd > clippedStart) {
            interval.copy(start = clippedStart, end = clippedEnd)
        } else {
            null
        }
    }
}

private fun mergeShortForegroundGaps(
    intervals: List<ForegroundInterval>
): List<ForegroundInterval> {
    val merged = mutableListOf<ForegroundInterval>()
    intervals.sortedBy { it.start }.forEach { interval ->
        val previous = merged.lastOrNull()
        if (
            previous != null &&
            previous.packageName == interval.packageName &&
            interval.start - previous.end <= 60_000L
        ) {
            merged[merged.lastIndex] = previous.copy(end = maxOf(previous.end, interval.end))
        } else {
            merged += interval
        }
    }
    return merged
}

private fun ignoredUsagePackages(context: Context): Set<String> {
    val packageManager = context.packageManager
    val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val homePackages = packageManager
        .queryIntentActivities(homeIntent, PackageManager.MATCH_ALL)
        .mapNotNull { it.activityInfo?.packageName }
    val launcherPackages = runCatching {
        packageManager.getInstalledApplications(PackageManager.MATCH_ALL)
            .filter { applicationInfo ->
                val packageLooksLikeLauncher = applicationInfo.packageName
                    .contains("launcher", ignoreCase = true)
                val labelLooksLikeLauncher = applicationInfo.loadLabel(packageManager)
                    .toString()
                    .contains("launcher", ignoreCase = true)
                packageLooksLikeLauncher || labelLooksLikeLauncher
            }
            .map { it.packageName }
    }.getOrDefault(emptyList())

    return buildSet {
        add(context.packageName)
        add("android")
        add("com.android.systemui")
        addAll(homePackages)
        addAll(launcherPackages)
    }
}

private fun addIntervalToHourlyBuckets(
    buckets: MutableList<Long>,
    startTime: Long,
    endTime: Long,
    dayStart: Long
) {
    val hourMillis = 60L * 60L * 1_000L
    val dayEnd = dayStart + 24L * hourMillis
    var cursor = maxOf(startTime, dayStart)
    val cappedEnd = minOf(endTime, dayEnd)
    while (cursor < cappedEnd) {
        val hourIndex = ((cursor - dayStart) / hourMillis).toInt().coerceIn(0, 23)
        val hourEnd = minOf(dayStart + (hourIndex + 1L) * hourMillis, cappedEnd)
        buckets[hourIndex] += hourEnd - cursor
        cursor = hourEnd
    }
}

private const val MINUTE_MILLIS = 60_000L
private const val MANUAL_BREAK_BLOCK_MILLIS = 20L * MINUTE_MILLIS
private const val DAY_LOOKBACK_MILLIS = 24L * 60L * MINUTE_MILLIS

private fun resolveAppLabel(context: Context, packageName: String): String {
    return runCatching {
        val applicationInfo = context.packageManager.getApplicationInfo(
            packageName,
            PackageManager.MATCH_ALL
        )
        applicationInfo.loadLabel(context.packageManager).toString()
    }.getOrElse {
        val fallback = packageName.split('.')
            .asReversed()
            .firstOrNull { it !in setOf("android", "app", "mobile", "main") }
            ?: packageName.substringAfterLast('.')
        fallback.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
    }
}

private fun formatUsageDuration(minutes: Long): String {
    val hours = minutes / 60L
    val remainingMinutes = minutes % 60L
    return if (hours > 0L) {
        "${hours}h ${remainingMinutes}m"
    } else {
        "${remainingMinutes}m"
    }
}

private fun millisToDisplayMinutes(millis: Long): Long {
    if (millis <= 0L) return 0L
    return ((millis + 30_000L) / 60_000L).coerceAtLeast(1L)
}

private fun formatUpdatedTime(timestamp: Long): String {
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(java.util.Date(timestamp))
}

private fun formatHistoryTimestamp(timestamp: Long): String {
    return SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()).format(java.util.Date(timestamp))
}

@Composable
private fun ProgressCard() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val historyStore = remember(context) { EyeGuardianStore(context) }
    var refreshRequest by remember { mutableStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshRequest++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val todayProgress = remember(historyStore, refreshRequest) {
        val today = dayKey(System.currentTimeMillis())
        val completedBreaks = historyStore.readBreakRecords()
            .count { it.dateKey == today && it.completed }
        val completedResets = historyStore.readTwentyTwentyCompletedRecords()
            .count { it.dateKey == today }
        val hasCheckIn = historyStore.readUsageRecords().any { it.dateKey == today }
        val totalBreaks = completedBreaks + completedResets
        val todayXp = totalBreaks * 20 + if (hasCheckIn) 10 else 0
        val streak = buildGamificationState(historyStore).currentStreakDays
        Triple(totalBreaks, todayXp, streak)
    }

    Card(
            border = glassBorder(),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = appColor(0xFFFFFFFF))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Today",
                style = MaterialTheme.typography.titleMedium,
                color = appColor(0xFF163B2A)
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                ProgressItem(value = todayProgress.first.toString(), label = "Breaks")
                Spacer(modifier = Modifier.weight(1f))
                ProgressItem(value = todayProgress.second.toString(), label = "XP")
                Spacer(modifier = Modifier.weight(1f))
                ProgressItem(value = "${todayProgress.third} days", label = "Streak")
            }
        }
    }
}

@Composable
private fun ProgressItem(value: String, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = appColor(0xFF163B2A)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = appColor(0xFF718078)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EyeGuardHomePreview() {
    EyeGuardTheme {
        EyeGuardHome()
    }
}
