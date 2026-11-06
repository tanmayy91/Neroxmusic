package com.convx.music.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import com.convx.music.R
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * New five-step intro only. Call this from the existing onboarding route and keep
 * the current app navigation unchanged. Permission handlers are injected so this
 * flow does not silently replace app-level permission logic.
 *
 * NexaLogo: swap the painterResource below for your exact existing N-logo asset.
 */
@Composable
fun NexaOnboardingFlow(
    onFinished: () -> Unit,
    notificationsGranted: () -> Boolean,
    requestNotifications: (() -> Unit) -> Unit,
    canInstallPackages: () -> Boolean,
    requestInstallPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var page by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val reduceMotion = remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    var notificationEnabled by remember { mutableStateOf(notificationsGranted()) }
    var installAllowed by remember { mutableStateOf(canInstallPackages()) }
    val backgrounds = listOf(
        NexaIntroTokens.WelcomeBg, NexaIntroTokens.LyricsBg, NexaIntroTokens.OfflineBg,
        NexaIntroTokens.PermissionsBg, NexaIntroTokens.FinishBg,
    )
    val background by animateColorAsStateCompat(backgrounds[page], reduceMotion)

    fun openUrl(url: String) {
        if (url.isBlank()) return
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    fun next() { if (page < 4) page++ else onFinished() }
    fun back() { if (page > 0) page-- }

    Box(
        modifier.fillMaxSize().background(background)
            .pointerInput(page) {
                var total = 0f
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, amount -> total += amount },
                    onDragEnd = { if (abs(total) > 72f) { if (total < 0) next() else back() }; total = 0f },
                    onDragCancel = { total = 0f },
                )
            },
    ) {
        if (page < 4) {
            Text(
                "Skip",
                color = if (page == 3) NexaIntroTokens.PermissionsInk else Color.White,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 12.dp, end = 24.dp)
                    .clip(RoundedCornerShape(24.dp)).clickableNoRipple { onFinished() }.padding(10.dp),
                fontSize = 15.sp,
            )
        }
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                (slideInHorizontally(tween(if (reduceMotion) 0 else 360)) { it / 8 } + fadeIn(tween(280))) togetherWith
                    (slideOutHorizontally(tween(if (reduceMotion) 0 else 360)) { -it / 8 } + fadeOut(tween(180)))
            },
            label = "intro-screen",
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        ) { target ->
            when (target) {
                0 -> WelcomeScreen(reduceMotion, play = target == page)
                1 -> LyricsScreen(reduceMotion, play = target == page)
                2 -> OfflineScreen(reduceMotion, play = target == page)
                3 -> PermissionsScreen(
                    reduceMotion = reduceMotion,
                    play = target == page,
                    notificationsEnabled = notificationEnabled,
                    installAllowed = installAllowed,
                    onNotificationToggle = { enabled ->
                        if (enabled) requestNotifications { notificationEnabled = notificationsGranted() }
                        else notificationEnabled = false
                    },
                    onInstallClick = {
                        requestInstallPermission()
                        installAllowed = canInstallPackages()
                    },
                )
                else -> CommunityScreen(
                    reduceMotion = reduceMotion,
                    play = target == page,
                    onGitHub = { openUrl(NexaIntroTokens.GITHUB_REPO_URL) },
                    onDiscord = { openUrl(NexaIntroTokens.DISCORD_INVITE_URL) },
                    onLicense = { openUrl(NexaIntroTokens.LICENSE_URL) },
                )
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                repeat(5) { index ->
                    val selected = page == index
                    val width by animateFloatAsStateCompat(if (selected) 26f else 7f, reduceMotion)
                    Box(Modifier.width(width.dp).height(7.dp).clip(CircleShape)
                        .background(if (page == 3) NexaIntroTokens.TerracottaAccent.copy(alpha = if (selected) 1f else .35f)
                        else if (page == 0) NexaIntroTokens.Mint.copy(alpha = if (selected) 1f else .35f)
                        else NexaIntroTokens.Ink.copy(alpha = if (selected) 1f else .32f)))
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (page > 0) {
                    Button(
                        onClick = ::back,
                        modifier = Modifier.height(58.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = if(page == 1) NexaIntroTokens.Cream else if(page == 3) NexaIntroTokens.PermissionsInk else if(page == 2) NexaIntroTokens.OfflineText else NexaIntroTokens.Ink),
                    ) { Text("Back", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                }
                val buttonBg = when(page) {
                    0 -> NexaIntroTokens.RedButton
                    1 -> NexaIntroTokens.Ink
                    2 -> NexaIntroTokens.Saffron
                    3 -> NexaIntroTokens.PermissionsInk
                    else -> NexaIntroTokens.Ink
                }
                val buttonText = when(page) {
                    0 -> Color.White
                    1 -> NexaIntroTokens.Cream
                    2 -> NexaIntroTokens.Ink
                    3 -> NexaIntroTokens.PermissionsBg
                    else -> NexaIntroTokens.Saffron
                }
                Button(
                    onClick = ::next,
                    modifier = Modifier.weight(1f).height(58.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = buttonBg, contentColor = buttonText),
                ) { Text(if(page == 4) "Get Started" else "Next", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun WelcomeScreen(reduceMotion: Boolean, play: Boolean) {
    val t = rememberInfiniteTransition(label = "welcome")
    val floatY by t.animateFloat(if (reduceMotion) 0f else -8f, if (reduceMotion) 0f else 8f,
        infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "logo-float")
    val pulse by t.animateFloat(0f, if (reduceMotion) 0f else 1f,
        infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "rings")
    Column(Modifier.fillMaxSize().padding(top = 72.dp, bottom = 105.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
            repeat(3) { i ->
                val size = 155.dp + (i * 34).dp
                Box(Modifier.size(size).scale(if(reduceMotion) 1f else .72f + pulse * .38f)
                    .border(1.dp, NexaIntroTokens.Mint.copy(alpha = if(reduceMotion) .55f else (1f-pulse)*.6f), CircleShape))
            }
            NexaLogo(Modifier.offset(y = floatY.dp).fillMaxWidth(.58f).height(174.dp))
        }
        Spacer(Modifier.height(22.dp))
        EqualizerBars(NexaIntroTokens.Mint, reduceMotion, 9)
        Spacer(Modifier.height(24.dp))
        Text("Welcome to", color = NexaIntroTokens.WelcomeMuted, fontSize = 18.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Nerox", color = NexaIntroTokens.Mint, fontSize = 52.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-2).sp)
            Text("Music", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-2).sp)
        }
        Text("Music that moves with you.", color = Color.White, fontSize = 17.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedPill("Gms Edition", NexaIntroTokens.Mint)
            OutlinedPill("v0.1.0", NexaIntroTokens.Mint)
        }
    }
}

@Composable
private fun LyricsScreen(reduceMotion: Boolean, play: Boolean) {
    val lines = listOf(
        "Lights go down, the bass comes in",
        "Hold the moment, let it spin",
        "Every word is yours to sing",
        "Turn it up and let it ring",
    )
    var line by remember { mutableIntStateOf(0) }
    var word by remember { mutableIntStateOf(0) }
    LaunchedEffect(reduceMotion, play) {
        if (!reduceMotion && play) while (true) {
            delay(NexaIntroTokens.LYRICS_LINE_MS / 5)
            val words = lines[line].split(" ")
            if (word < words.lastIndex) word++ else {
                word = 0
                line = (line + 1) % lines.size
            }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(top = 68.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Put the lyric preview first, as in the supplied visual reference.
        Column(
            Modifier.fillMaxWidth()
                .weight(1f, fill = false)
                .heightIn(min = 280.dp, max = 350.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(NexaIntroTokens.Ink.copy(alpha = .16f))
                .border(1.dp, NexaIntroTokens.Cream.copy(alpha = .24f), RoundedCornerShape(30.dp))
                .padding(horizontal = 24.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                Modifier.clip(CircleShape)
                    .background(NexaIntroTokens.Ink.copy(alpha = .18f))
                    .border(1.dp, NexaIntroTokens.Cream.copy(alpha = .28f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EqualizerBars(NexaIntroTokens.Cream, reduceMotion, 3, Modifier.height(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Live synced", color = NexaIntroTokens.Cream, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }

            Column(
                Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                lines.forEachIndexed { index, lyric ->
                    val active = line == index
                    Text(
                        text = lyric,
                        color = when {
                            active -> NexaIntroTokens.Ink
                            index == 3 -> NexaIntroTokens.Cream.copy(alpha = .82f)
                            else -> NexaIntroTokens.Ink.copy(alpha = .62f)
                        },
                        fontSize = 22.sp,
                        lineHeight = 29.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                    )
                }
            }
        }

        Box(
            Modifier.size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(NexaIntroTokens.Cream.copy(alpha = .16f))
                .border(1.dp, NexaIntroTokens.Cream.copy(alpha = .35f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            DrawnIcon("lyrics", NexaIntroTokens.Cream, reduceMotion, Modifier, play, 28.dp)
        }

        RichHeadline(
            "Sing along.\nWord by word.",
            "Word by word.",
            NexaIntroTokens.Cream,
            NexaIntroTokens.Ink,
        )

        Text(
            "Lyrics glide with every beat, so you never lose your place.",
            color = NexaIntroTokens.Cream,
            fontSize = 17.sp,
            lineHeight = 24.sp,
        )
    }
}

@Composable
private fun OfflineScreen(reduceMotion: Boolean, play: Boolean) {
    val t = rememberInfiniteTransition(label = "offline")
    val progress by t.animateFloat(if(reduceMotion) 1f else 0f, 1f,
        infiniteRepeatable(tween(4500, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "download-ring")
    val bounce by t.animateFloat(0f, if(reduceMotion) 0f else 6f,
        infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "download-bob")
    val rows = listOf("Late Night Mix", "Road Trip", "Focus Flow")
    Column(Modifier.fillMaxSize().padding(top=78.dp, bottom=112.dp), verticalArrangement=Arrangement.Center) {
        Spacer(Modifier.height(8.dp))
        RichHeadline("Your music.\nEven offline.", "Even offline.", NexaIntroTokens.OfflineText, NexaIntroTokens.Saffron)
        Spacer(Modifier.height(14.dp))
        Text("Download once, play anywhere, in premium quality.", color=NexaIntroTokens.OfflineText, fontSize=17.sp, lineHeight=24.sp)
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth().height(158.dp), contentAlignment=Alignment.Center) {
            Canvas(Modifier.size(132.dp)) {
                drawArc(NexaIntroTokens.Mint.copy(alpha=.2f), -90f, 360f, false, style=Stroke(7.dp.toPx(), cap=StrokeCap.Round))
                drawArc(NexaIntroTokens.Mint, -90f, 360f*progress, false, style=Stroke(7.dp.toPx(), cap=StrokeCap.Round))
            }
            DrawnIcon("cloud-download", NexaIntroTokens.Mint, reduceMotion, Modifier.offset(y=bounce.dp), play, 54.dp)
            Box(Modifier.align(Alignment.BottomCenter).offset(x=52.dp, y=(-10).dp).clip(RoundedCornerShape(10.dp)).background(NexaIntroTokens.Saffron).padding(horizontal=12.dp, vertical=6.dp)) {
                Text("HQ", color=NexaIntroTokens.Ink, fontWeight=FontWeight.ExtraBold, fontSize=13.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        rows.forEachIndexed { i, title ->
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(reduceMotion) { if (reduceMotion) visible=true else { delay(500L+i*180L); visible=true } }
            Row(Modifier.fillMaxWidth().padding(vertical=5.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha=.08f)).padding(14.dp), verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(NexaIntroTokens.Mint), contentAlignment=Alignment.Center) {
                    if (visible) {
                        Text("✓", color=NexaIntroTokens.Ink, fontWeight=FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text(title, color=NexaIntroTokens.OfflineText, fontWeight=FontWeight.SemiBold, fontSize=15.sp) }
                Text("Downloaded", color=NexaIntroTokens.Mint, fontSize=12.sp)
            }
        }
    }
}

@Composable
private fun PermissionsScreen(
    reduceMotion: Boolean,
    play: Boolean,
    notificationsEnabled: Boolean,
    installAllowed: Boolean,
    onNotificationToggle: (Boolean) -> Unit,
    onInstallClick: () -> Unit,
) {
    val bellRotation by rememberInfiniteTransition(label="bell").animateFloat(
        0f, if(reduceMotion) 0f else 12f,
        infiniteRepeatable(tween(180, delayMillis=3000), RepeatMode.Reverse), label="bell-wiggle"
    )
    Column(Modifier.fillMaxSize().padding(top=78.dp,bottom=112.dp), verticalArrangement=Arrangement.Center) {
        RichHeadline("Two quick things,\nthen we're off.", "then we're off.", NexaIntroTokens.PermissionsInk, NexaIntroTokens.TerracottaAccent)
        Spacer(Modifier.height(12.dp))
        Text("You can change these anytime in Settings.", color=NexaIntroTokens.PermissionsInk.copy(alpha=.78f), fontSize=17.sp)
        Spacer(Modifier.height(26.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(NexaIntroTokens.CardWhite).padding(18.dp), verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(NexaIntroTokens.PermissionsInk.copy(alpha = .08f)), contentAlignment = Alignment.Center) { DrawnIcon("bell", Color(0xFFB3331A), reduceMotion, Modifier.rotate(bellRotation), play, 26.dp) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Notifications", color=NexaIntroTokens.PermissionsInk, fontWeight=FontWeight.Bold, fontSize=18.sp)
                Spacer(Modifier.height(5.dp))
                Text("Control your music right from the notification bar.", color=NexaIntroTokens.PermissionsInk.copy(alpha=.8f), fontSize=14.sp, lineHeight=19.sp)
            }
            Switch(checked=notificationsEnabled, onCheckedChange=onNotificationToggle, colors=SwitchDefaults.colors(checkedThumbColor=NexaIntroTokens.CardWhite, checkedTrackColor=NexaIntroTokens.TerracottaAccent))
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(NexaIntroTokens.CardWhite).padding(18.dp), verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(NexaIntroTokens.PermissionsInk.copy(alpha = .08f)), contentAlignment = Alignment.Center) { DrawnIcon("phone-download", Color(0xFFB3331A), reduceMotion, Modifier, play, 26.dp) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("App Installation", color=NexaIntroTokens.PermissionsInk, fontWeight=FontWeight.Bold, fontSize=18.sp)
                Spacer(Modifier.height(5.dp))
                Text("So updates install smoothly.", color=NexaIntroTokens.PermissionsInk.copy(alpha=.8f), fontSize=14.sp, lineHeight=19.sp)
            }
            Button(onClick=onInstallClick, shape=RoundedCornerShape(14.dp), colors=ButtonDefaults.buttonColors(containerColor=NexaIntroTokens.PermissionsInk,contentColor=NexaIntroTokens.PermissionsBg)) {
                Text(if(installAllowed) "Allowed ✓" else "Allow", fontWeight=FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CommunityScreen(reduceMotion: Boolean, play: Boolean, onGitHub: () -> Unit, onDiscord: () -> Unit, onLicense: () -> Unit) {
    val t = rememberInfiniteTransition(label="community")
    val float by t.animateFloat(0f, if(reduceMotion) 0f else 9f, infiniteRepeatable(tween(1900), RepeatMode.Reverse), label="community-float")
    Column(Modifier.fillMaxSize().padding(top=72.dp,bottom=112.dp), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.Center) {
        RichHeadline("You're in.\nLet's play.", "Let's play.", NexaIntroTokens.Ink, NexaIntroTokens.FinishAccent, TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text("Built in the open by people who love music.", color=NexaIntroTokens.Ink, fontSize=17.sp, textAlign=TextAlign.Center, lineHeight=24.sp)
        Spacer(Modifier.height(24.dp))
        Box(Modifier.size(104.dp).offset(y=float.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF1A120C)), contentAlignment=Alignment.Center) {
            DrawnIcon("music-note", NexaIntroTokens.Mint, reduceMotion, Modifier, play, 52.dp)
        }
        Spacer(Modifier.height(26.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp), verticalAlignment=Alignment.CenterVertically) {
            InfoChip("Liquid Glass UI", NexaIntroTokens.Ink, NexaIntroTokens.FinishBg)
            InfoChip("Discord Presence", NexaIntroTokens.Ink, NexaIntroTokens.FinishBg)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp), verticalAlignment=Alignment.CenterVertically) {
            InfoChip("Dynamic Updates", NexaIntroTokens.Ink, NexaIntroTokens.FinishBg)
            ClickableTextCompat("Open source · GPL v3.0", NexaIntroTokens.Ink, NexaIntroTokens.FinishBg, onLicense)
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Button(onClick=onGitHub, shape=RoundedCornerShape(16.dp), colors=ButtonDefaults.buttonColors(containerColor=NexaIntroTokens.Ink,contentColor=NexaIntroTokens.Saffron)) {
                DrawnIcon("star", NexaIntroTokens.Saffron, reduceMotion, Modifier, play, 20.dp)
                Spacer(Modifier.width(6.dp)); Text("Star on GitHub", fontWeight=FontWeight.Bold)
            }
            Button(onClick=onDiscord, shape=RoundedCornerShape(16.dp), colors=ButtonDefaults.buttonColors(containerColor=NexaIntroTokens.Ink,contentColor=NexaIntroTokens.Saffron)) {
                DrawnIcon("chat", NexaIntroTokens.Saffron, reduceMotion, Modifier, play, 20.dp)
                Spacer(Modifier.width(6.dp)); Text("Join Discord", fontWeight=FontWeight.Bold)
            }
        }
    }
}

/** Replace this painter with the exact N-logo asset; it is intentionally large, never tiled. */
@Composable
private fun NexaLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.convx_logo),
        contentDescription = "Nerox Music logo",
        modifier = modifier,
    )
}

@Composable
private fun RichHeadline(full: String, accent: String, base: Color, highlight: Color, align: TextAlign = TextAlign.Start) {
    val parts = full.split(accent)
    androidx.compose.foundation.text.BasicText(
        text = androidx.compose.ui.text.buildAnnotatedString {
            val start = full.indexOf(accent)
            if(start < 0) append(full) else {
                append(full.substring(0,start))
                pushStyle(androidx.compose.ui.text.SpanStyle(color=highlight)); append(accent); pop()
                append(full.substring(start+accent.length))
            }
        },
        modifier=Modifier.fillMaxWidth(),
        style=androidx.compose.ui.text.TextStyle(color=base,fontSize=36.sp,lineHeight=39.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=(-1.4).sp,textAlign=align),
    )
}

@Composable
private fun EqualizerBars(color: Color, reduceMotion: Boolean, count: Int, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label="equalizer")
    Row(modifier.height(26.dp), horizontalArrangement=Arrangement.spacedBy(3.dp), verticalAlignment=Alignment.CenterVertically) {
        repeat(count) { i ->
            val h by transition.animateFloat(5f, if(reduceMotion) 13f else (8 + (i*7)%17).toFloat(),
                infiniteRepeatable(tween(250+i*43), RepeatMode.Reverse), label="bar-$i")
            Box(Modifier.width(3.dp).height(if(reduceMotion) 10.dp else h.dp).clip(CircleShape).background(color))
        }
    }
}

@Composable
private fun DrawnIcon(
    name: String,
    color: Color,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    play: Boolean = true,
    size: androidx.compose.ui.unit.Dp = 24.dp,
) {
    AnimatedLineIcon(
        name = name,
        iconSize = size,
        colour = color,
        play = play,
        reduceMotion = reduceMotion,
        modifier = modifier,
    )
}

@Composable
private fun OutlinedPill(text: String, color: Color) {
    Box(Modifier.clip(CircleShape).border(1.dp,color.copy(alpha=.7f),CircleShape).padding(horizontal=13.dp,vertical=7.dp)) {
        Text(text,color=color,fontSize=12.sp,fontWeight=FontWeight.Medium)
    }
}

@Composable
private fun InfoChip(text: String, color: Color, bg: Color) {
    Box(Modifier.clip(CircleShape).background(bg).border(1.dp,color.copy(alpha=.25f),CircleShape).padding(horizontal=11.dp,vertical=9.dp)) {
        Text(text,color=color,fontSize=11.sp,fontWeight=FontWeight.SemiBold)
    }
}

@Composable
private fun ClickableTextCompat(text: String, color: Color, bg: Color, onClick: () -> Unit) {
    Box(Modifier.clip(CircleShape).background(bg).border(1.dp,color.copy(alpha=.25f),CircleShape).clickableNoRipple(onClick).padding(horizontal=11.dp,vertical=9.dp)) {
        Text(text,color=color,fontSize=11.sp,fontWeight=FontWeight.SemiBold)
    }
}

@Composable
private fun animateColorAsStateCompat(target: Color, reduceMotion: Boolean): State<Color> =
    androidx.compose.animation.animateColorAsState(target, tween(if(reduceMotion) 0 else 520), label="background-color")

@Composable
private fun animateFloatAsStateCompat(target: Float, reduceMotion: Boolean): State<Float> =
    animateFloatAsState(target, tween(if(reduceMotion) 0 else 280), label="control")

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)
