@file:OptIn(ExperimentalTextApi::class)

package com.convx.music

import com.convx.music.ui.component.sf
import com.convx.music.ui.component.SymbolIcon
import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Forum
import com.composables.sfsymbols.SfSymbols
import com.composables.sfsymbols.monochrome.SFEllipsisBubble
import com.convx.music.ui.ios.compat.Button
import androidx.compose.material3.ButtonDefaults
import com.convx.music.ui.ios.compat.Card
import androidx.compose.material3.CardDefaults
import com.convx.music.ui.ios.compat.Icon
import androidx.compose.material3.LargeFloatingActionButton
import com.convx.music.ui.ios.compat.ListItem
import com.convx.music.ui.ios.compat.AppTheme
import com.convx.music.ui.ios.compat.Surface
import com.convx.music.ui.ios.compat.Switch
import com.convx.music.ui.ios.compat.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.convx.music.constants.IsFirstRunKey
import com.convx.music.ui.theme.ConvxTheme
import com.convx.music.ui.utils.safeOpenUri
import com.convx.music.utils.dataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import androidx.datastore.preferences.core.edit

data class OnboardingPageInfo(
    val content: @Composable (onUpdateScrollState: (Boolean) -> Unit) -> Unit
)

class WelcomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        val forceShow = intent.getBooleanExtra("FORCE_SHOW", false)

        // Read onboarding state asynchronously; the synchronous DataStore helper can
        // block the main thread on a cold launch while its first snapshot is loading.
        lifecycleScope.launch {
            val isFirstRun = dataStore.data
                .map { it[IsFirstRunKey] ?: true }
                .first()

            if (!isFirstRun && !forceShow) {
                finishOnboarding()
                return@launch
            }

            enableEdgeToEdge()
            setContent {
                ConvxTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = AppTheme.colorScheme.background
                    ) {
                        WelcomePagerScreen(onFinished = {
                            lifecycleScope.launch {
                                dataStore.edit { it[IsFirstRunKey] = false }
                                if (forceShow) {
                                    finish()
                                } else {
                                    finishOnboarding()
                                }
                            }
                        })
                    }
                }
            }
        }
    }

    private fun finishOnboarding() {
        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_PLAY_INTRO, true))
        finish()
        // The intro picks up from the black splash; a window slide would break the handoff.
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}

@OptIn(ExperimentalTextApi::class)
val GoogleSansFlex = FontFamily(
    Font(
        resId = com.convx.music.R.font.google_sans_flex,
        weight = FontWeight.Normal,
        style = FontStyle.Normal,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(400),
            FontVariation.width(100f),
            FontVariation.Setting("ROND", 100f)
        )
    )
)

@Composable
fun WelcomePagerScreen(onFinished: () -> Unit) {
    val context = LocalContext.current

    // Keep the original permission state and activity-result handlers, but render
    // the new five-screen Nexa onboarding instead of the legacy four-page pager.
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    var canInstallPackages by remember {
        mutableStateOf(
            if (BuildConfig.FLAVOR.contains("gms", ignoreCase = true) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
            } else true
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    hasNotificationPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    canInstallPackages = runCatching {
                        context.packageManager.canRequestPackageInstalls()
                    }.getOrDefault(false)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasNotificationPermission = granted }
    )
    val installParamsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            canInstallPackages = runCatching {
                context.packageManager.canRequestPackageInstalls()
            }.getOrDefault(false)
        }
    }

    fun requestOrOpenNotifications() {
        if (hasNotificationPermission) {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
            context.startActivity(intent)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun openInstallSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            installParamsLauncher.launch(intent)
        }
    }

    com.convx.music.ui.onboarding.NexaOnboardingFlow(
        onFinished = onFinished,
        notificationsGranted = { hasNotificationPermission },
        requestNotifications = { onPermissionResult ->
            requestOrOpenNotifications()
            onPermissionResult()
        },
        canInstallPackages = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
            } else true
        },
        requestInstallPermission = ::openInstallSettings,
    )
}

/** Brand palette for the intro only. Mint matches the existing logo vector fill (#C9E4C8). */
private object NexaIntroColors {
    val nexaMint = Color(0xFFC9E4C8)
    val accent = Color(0xFFE5173F)
    val accentText = Color(0xFFFF4B6B)
    val background = Color(0xFF08080A)
    val text = Color(0xFFF8F8FA)
    val muted = Color(0xFFA1A1A6)
    val glass = Color.White.copy(alpha = 0.07f)
    val glassBorder = Color.White.copy(alpha = 0.10f)
    val violetGlow = Color(0x443F2D83)
}

/** Keep all web destinations in one place, so each link is easy to update. */
private object NexaIntroLinks {
    const val GITHUB_REPO_URL = "https://github.com/tanmayy91/NexaMusic"
    const val DISCORD_INVITE_URL = "https://discord.gg/D8fYQKUEzw"
    const val LICENSE_URL = "https://github.com/tanmayy91/NexaMusic/blob/main/LICENSE"
}

/** Uses the existing logo resource, unchanged. Connect a different existing asset only here if needed. */
@Composable
private fun NexaLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(com.convx.music.R.drawable.convx_logo),
        contentDescription = stringResource(com.convx.music.R.string.app_name),
        modifier = modifier,
    )
}

@Composable
private fun IntroAmbientBackground() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(NexaIntroColors.accent.copy(alpha = 0.22f), Color.Transparent),
                center = Offset(size.width * 0.52f, 0f),
                radius = size.width * 0.95f,
            ),
            size = size,
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(NexaIntroColors.violetGlow, Color.Transparent),
                center = Offset(0f, size.height),
                radius = size.width * 0.95f,
            ),
            size = size,
        )
    }
}

@Composable
private fun IntroGlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && onClick != null) 0.975f else 1f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "introCardPress",
    )
    val shape = RoundedCornerShape(21.dp)
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(NexaIntroColors.glass)
            .border(BorderStroke(1.dp, NexaIntroColors.glassBorder), shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick,
                    )
                } else Modifier
            ),
    ) {
        content()
    }
}

@Composable
private fun IntroActionButton(
    text: String,
    isPrimary: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "introButtonPress",
    )
    val shape = CircleShape
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(
                when {
                    !enabled && isPrimary -> NexaIntroColors.accent.copy(alpha = 0.48f)
                    isPrimary -> NexaIntroColors.accent
                    else -> Color.White.copy(alpha = 0.08f)
                }
            )
            .border(
                BorderStroke(
                    1.dp,
                    if (isPrimary) Color.White.copy(alpha = 0.08f) else NexaIntroColors.glassBorder,
                ),
                shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontFamily = GoogleSansFlex,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = if (isPrimary) Color.White else NexaIntroColors.text,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun WelcomeIntroPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(194.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(190.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(NexaIntroColors.accent.copy(alpha = 0.34f), Color.Transparent)
                        ),
                        CircleShape,
                    )
            )
            Box(
                Modifier
                    .size(142.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(NexaIntroColors.glass)
                    .border(BorderStroke(1.dp, NexaIntroColors.glassBorder), RoundedCornerShape(32.dp)),
                contentAlignment = Alignment.Center,
            ) {
                NexaLogo(Modifier.size(82.dp))
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(com.convx.music.R.string.welcome_to),
            fontFamily = GoogleSansFlex,
            fontSize = 20.sp,
            color = NexaIntroColors.muted,
            textAlign = TextAlign.Center,
        )
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = NexaIntroColors.nexaMint)) { append("Nerox") }
                withStyle(SpanStyle(color = NexaIntroColors.text)) { append("Music") }
            },
            fontFamily = GoogleSansFlex,
            fontWeight = FontWeight.Bold,
            fontSize = 46.sp,
            lineHeight = 54.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IntroPill(
                text = if (BuildConfig.FLAVOR.contains("gms", ignoreCase = true)) "Gms Edition" else "Foss Edition",
                leadingDot = NexaIntroColors.nexaMint,
            )
            IntroPill(text = "v${BuildConfig.VERSION_NAME}")
        }

        Spacer(Modifier.height(22.dp))
        Text(
            text = stringResource(com.convx.music.R.string.welcome_preparing_subtitle),
            fontFamily = GoogleSansFlex,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            color = NexaIntroColors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.86f),
        )
    }
}

@Composable
private fun IntroPill(text: String, leadingDot: Color? = null) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.07f))
            .border(BorderStroke(1.dp, NexaIntroColors.glassBorder), CircleShape)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (leadingDot != null) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(leadingDot))
        }
        Text(
            text = text,
            fontFamily = GoogleSansFlex,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = NexaIntroColors.text,
        )
    }
}

@Composable
private fun IntroHeading(
    firstLine: String,
    accentLine: String,
    subtitle: String? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = firstLine,
            fontFamily = GoogleSansFlex,
            fontWeight = FontWeight.Medium,
            fontSize = 39.sp,
            lineHeight = 43.sp,
            color = NexaIntroColors.text,
        )
        Text(
            text = accentLine,
            fontFamily = GoogleSansFlex,
            fontWeight = FontWeight.Bold,
            fontSize = 39.sp,
            lineHeight = 43.sp,
            color = NexaIntroColors.accentText,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = subtitle,
                fontFamily = GoogleSansFlex,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = NexaIntroColors.muted,
            )
        }
    }
}

@Composable
private fun RequiredPermissionsIntroPage(
    hasNotificationPermission: Boolean,
    canInstallPackages: Boolean,
    showInstallationPermission: Boolean,
    onNotificationsClick: () -> Unit,
    onNotificationToggle: (Boolean) -> Unit,
    onInstallClick: () -> Unit,
) {
    // The whole page scrolls when Dynamic Type is large; the action buttons remain fixed outside it.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 10.dp),
    ) {
        IntroHeading(
            firstLine = stringResource(com.convx.music.R.string.perm_required),
            accentLine = stringResource(com.convx.music.R.string.perm_permissions),
            subtitle = stringResource(com.convx.music.R.string.perm_intro_text),
        )

        Spacer(Modifier.height(22.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IntroGlassCard(onClick = onNotificationsClick) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IntroIconTile(
                        painter = rememberVectorPainter(Icons.Rounded.Notifications),
                        background = Color(0xFFFFAEE4),
                        tint = Color(0xFF8D0053),
                        size = 46.dp,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(com.convx.music.R.string.perm_notif_title),
                            fontFamily = GoogleSansFlex,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = NexaIntroColors.text,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(com.convx.music.R.string.perm_notif_desc),
                            fontFamily = GoogleSansFlex,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = NexaIntroColors.muted,
                        )
                    }
                    Switch(
                        checked = hasNotificationPermission,
                        onCheckedChange = onNotificationToggle,
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = NexaIntroColors.nexaMint,
                            checkedTrackColor = NexaIntroColors.nexaMint.copy(alpha = 0.40f),
                            checkedBorderColor = NexaIntroColors.nexaMint,
                        ),
                    )
                }
            }

            if (showInstallationPermission) {
                IntroGlassCard(onClick = onInstallClick) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        IntroIconTile(
                            painter = rememberVectorPainter(Icons.Rounded.SystemUpdate),
                            background = Color(0xFFFFB683),
                            tint = Color(0xFF753403),
                            size = 46.dp,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(com.convx.music.R.string.perm_install_title),
                                fontFamily = GoogleSansFlex,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = NexaIntroColors.text,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(com.convx.music.R.string.perm_install_desc),
                                fontFamily = GoogleSansFlex,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = NexaIntroColors.muted,
                            )
                        }
                        IntroActionButton(
                            text = if (canInstallPackages) "Allowed" else "Allow",
                            isPrimary = true,
                            modifier = Modifier.widthIn(min = 72.dp).heightIn(min = 38.dp),
                            onClick = onInstallClick,
                        )
                    }
                }
            }

            Text(
                text = "You can change these anytime in Settings.",
                fontFamily = GoogleSansFlex,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = NexaIntroColors.muted,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 14.dp),
            )
        }
    }
}

@Composable
private fun CommunityIntroPage(
    onGitHub: () -> Unit,
    onDiscord: () -> Unit,
) {
    // On short displays or large text, let the page itself scroll instead of clipping cards.
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 10.dp)
    ) {
        IntroHeading(
            firstLine = "Join our",
            accentLine = "Community",
            subtitle = "Nerox Music is an open-source music app built with community support. Your help makes a difference!",
        )
        Spacer(Modifier.height(22.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            CommunityLinkCard(
                modifier = Modifier.weight(1f).heightIn(min = 270.dp),
                icon = rememberVectorPainter(Icons.Rounded.Star),
                iconBackground = Color(0xFFFFF1A8),
                iconTint = Color(0xFF8D6E00),
                title = "Star on GitHub",
                description = "Help us reach more people by starring our repository.",
                onClick = onGitHub,
            )
            CommunityLinkCard(
                modifier = Modifier.weight(1f).heightIn(min = 270.dp),
                icon = rememberVectorPainter(SfSymbols.Monochrome.SFEllipsisBubble),
                iconBackground = Color(0xFF67D4FF),
                iconTint = Color(0xFF004E5D),
                title = "Join Discord",
                description = "Get the latest updates and chat with the community.",
                onClick = onDiscord,
            )
        }
    }
}

@Composable
private fun CommunityLinkCard(
    modifier: Modifier,
    icon: Painter,
    iconBackground: Color,
    iconTint: Color,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    IntroGlassCard(modifier = modifier, onClick = onClick) {
        Box(Modifier.fillMaxSize().padding(15.dp)) {
            Box(
                Modifier.align(Alignment.TopEnd).size(30.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.09f))
                    .border(BorderStroke(1.dp, NexaIntroColors.glassBorder), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                SymbolIcon(symbol = sf("arrow.up.right"), contentDescription = null, tint = NexaIntroColors.text)
            }
            Column(
                modifier = Modifier.fillMaxSize().padding(top = 42.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    IntroIconTile(icon, iconBackground, iconTint, size = 54.dp)
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = title,
                        fontFamily = GoogleSansFlex,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        lineHeight = 22.sp,
                        color = NexaIntroColors.text,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = description,
                        fontFamily = GoogleSansFlex,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = NexaIntroColors.muted,
                    )
                }
                Text(
                    text = "Open",
                    fontFamily = GoogleSansFlex,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NexaIntroColors.accentText,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun FeaturesIntroPage(
    onGitHub: () -> Unit,
    onLicense: () -> Unit,
    onScrollPositionChanged: (Boolean) -> Unit,
) {
    val scrollState = rememberScrollState()
    val isAtBottom by remember {
        derivedStateOf {
            scrollState.maxValue == 0 || scrollState.value >= scrollState.maxValue - 20
        }
    }
    LaunchedEffect(isAtBottom) { onScrollPositionChanged(isAtBottom) }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 10.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(scrollState),
        ) {
            IntroHeading(
                firstLine = stringResource(com.convx.music.R.string.feat_discover),
                accentLine = stringResource(com.convx.music.R.string.feat_features),
                subtitle = stringResource(com.convx.music.R.string.feat_intro),
            )
            Spacer(Modifier.height(18.dp))

            // Four explicit rows keep the grid aligned at 2 columns. It can scroll on
            // compact displays or with large text rather than clipping a tile.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IntroFeatureTile(
                        modifier = Modifier.weight(1f),
                        icon = rememberVectorPainter(Icons.Rounded.Lyrics),
                        iconBackground = Color(0xFFFFAEE4),
                        iconTint = Color(0xFF8D0053),
                        title = stringResource(com.convx.music.R.string.feat_lyrics_title),
                        description = stringResource(com.convx.music.R.string.feat_lyrics_desc),
                    )
                    IntroFeatureTile(
                        modifier = Modifier.weight(1f),
                        icon = rememberVectorPainter(Icons.Rounded.CloudDownload),
                        iconBackground = Color(0xFF80DA88),
                        iconTint = Color(0xFF00522C),
                        title = stringResource(com.convx.music.R.string.feat_download_title),
                        description = stringResource(com.convx.music.R.string.feat_download_desc),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IntroFeatureTile(
                        modifier = Modifier.weight(1f),
                        icon = rememberVectorPainter(Icons.Rounded.HighQuality),
                        iconBackground = Color(0xFFFFB683),
                        iconTint = Color(0xFF753403),
                        title = stringResource(com.convx.music.R.string.feat_quality_title),
                        description = stringResource(com.convx.music.R.string.feat_quality_desc),
                    )
                    IntroFeatureTile(
                        modifier = Modifier.weight(1f),
                        icon = rememberVectorPainter(Icons.Rounded.BlurOn),
                        iconBackground = Color(0xFFCABEFF),
                        iconTint = Color(0xFF1C0062),
                        title = stringResource(com.convx.music.R.string.feat_glass_title),
                        description = stringResource(com.convx.music.R.string.feat_glass_desc),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IntroFeatureTile(
                        modifier = Modifier.weight(1f),
                        icon = rememberVectorPainter(Icons.Rounded.Forum),
                        iconBackground = Color(0xFF67D4FF),
                        iconTint = Color(0xFF004E5D),
                        title = stringResource(com.convx.music.R.string.feat_discord_title),
                        description = stringResource(com.convx.music.R.string.feat_discord_desc),
                    )
                    // Keep all eight advertised features visible in both editions.
                    // The existing updater/permission logic remains GMS-specific elsewhere.
                    IntroFeatureTile(
                        modifier = Modifier.weight(1f),
                        icon = rememberVectorPainter(Icons.Rounded.SystemUpdate),
                        iconBackground = Color(0xFF67D4FF),
                        iconTint = Color(0xFF004E5D),
                        title = stringResource(com.convx.music.R.string.feat_update_title),
                        description = stringResource(com.convx.music.R.string.feat_update_desc),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IntroFeatureTile(
                        modifier = Modifier.weight(1f),
                        icon = rememberVectorPainter(Icons.Rounded.Gavel),
                        iconBackground = Color(0xFFB6C6ED),
                        iconTint = Color(0xFF001B3F),
                        title = stringResource(com.convx.music.R.string.feat_license_title),
                        description = stringResource(com.convx.music.R.string.feat_license_desc),
                        onClick = onLicense,
                    )
                    IntroFeatureTile(
                        modifier = Modifier.weight(1f),
                        icon = rememberVectorPainter(Icons.Rounded.Terminal),
                        iconBackground = Color(0xFFCABEFF),
                        iconTint = Color(0xFF1C0062),
                        title = stringResource(com.convx.music.R.string.feat_github_title),
                        description = stringResource(com.convx.music.R.string.feat_github_desc),
                        onClick = onGitHub,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun IntroFeatureTile(
    modifier: Modifier = Modifier,
    icon: Painter,
    iconBackground: Color,
    iconTint: Color,
    title: String,
    description: String,
    onClick: (() -> Unit)? = null,
) {
    IntroGlassCard(
        modifier = modifier.heightIn(min = 110.dp),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IntroIconTile(icon, iconBackground, iconTint, size = 30.dp)
                if (onClick != null) {
                    SymbolIcon(
                        symbol = sf("arrow.up.right"),
                        contentDescription = null,
                        tint = NexaIntroColors.muted,
                        size = 13.dp,
                    )
                }
            }
            Text(
                text = title,
                fontFamily = GoogleSansFlex,
                fontSize = 14.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Bold,
                color = NexaIntroColors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = description,
                fontFamily = GoogleSansFlex,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = NexaIntroColors.muted,
            )
        }
    }
}

@Composable
private fun IntroIconTile(
    painter: Painter,
    background: Color,
    tint: Color,
    size: Dp,
) {
    Box(
        modifier = Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.52f),
        )
    }
}
