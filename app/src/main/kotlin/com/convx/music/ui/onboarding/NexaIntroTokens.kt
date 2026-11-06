package com.convx.music.ui.onboarding

import androidx.compose.ui.graphics.Color

/** Keep every intro colour, type choice, link and timing token in one place. */
object NexaIntroTokens {
    val WelcomeBg = Color(0xFF0F0D0B)
    val LyricsBg = Color(0xFFC23A1D)
    val OfflineBg = Color(0xFF12382A)
    val PermissionsBg = Color(0xFFF3EAD9)
    val FinishBg = Color(0xFFF2A33A)

    val White = Color(0xFFFFFFFF)
    val WelcomeMuted = Color(0xFFB8B3AC)
    val Mint = Color(0xFFC8E3C9)
    val Cream = Color(0xFFFFF3E2)
    val Ink = Color(0xFF1A0F0A)
    val OfflineText = Color(0xFFF4EEDD)
    val Saffron = Color(0xFFF2A33A)
    val PermissionsInk = Color(0xFF1A120C)
    val TerracottaAccent = Color(0xFFB3331A)
    val FinishAccent = Color(0xFF7A1F0C)
    val RedButton = Color(0xFFE5173F)
    val CardWhite = Color(0xFFFFFFFF)

    // Optional bundled fonts: add Bricolage Grotesque and DM Sans to res/font
    // and replace these null families with FontFamily(Font(R.font....)).
    const val HEADLINE_FONT = "Bricolage Grotesque"
    const val BODY_FONT = "DM Sans"
    const val HEADLINE_SIZE_SP = 36
    const val WORDMARK_SIZE_SP = 52
    const val BODY_SIZE_SP = 17

    // Fill these with the project's official URLs before release.
    const val GITHUB_REPO_URL = "https://github.com/tanmayy91/NexaMusic"
    const val DISCORD_INVITE_URL = "https://discord.gg/D8fYQKUEzw"
    const val LICENSE_URL = "https://github.com/tanmayy91/NexaMusic/blob/main/LICENSE"

    const val SCREEN_SLIDE_DP = 48
    const val STAGGER_MS = 100L
    const val LYRICS_LINE_MS = 2800L
    const val LOOP_MS = 1800
    const val ANIMATION_MS = 420
}
